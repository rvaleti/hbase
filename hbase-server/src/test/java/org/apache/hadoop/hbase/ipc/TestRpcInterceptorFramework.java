/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.ipc;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.Optional;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.security.User;
import org.apache.hadoop.hbase.testclassification.SmallTests;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(SmallTests.class)
public class TestRpcInterceptorFramework {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestRpcInterceptorFramework.class);

  private Configuration conf;
  private RpcInterceptorManager manager;
  private TestRpcInterceptor interceptor;

  @Before
  public void setUp() {
    conf = HBaseConfiguration.create();
    conf.setBoolean(RpcInterceptorManager.RPC_INTERCEPTION_ENABLED_KEY, true);
    manager = new RpcInterceptorManager(conf);
    interceptor = new TestRpcInterceptor();
  }

  @Test
  public void testInterceptorRegistration() {
    assertEquals(0, manager.getInterceptorCount());
    
    manager.addInterceptor(interceptor);
    assertEquals(1, manager.getInterceptorCount());
    
    manager.removeInterceptor(interceptor);
    assertEquals(0, manager.getInterceptorCount());
  }

  @Test
  public void testInterceptorDisabled() {
    Configuration disabledConf = HBaseConfiguration.create();
    disabledConf.setBoolean(RpcInterceptorManager.RPC_INTERCEPTION_ENABLED_KEY, false);
    
    RpcInterceptorManager disabledManager = new RpcInterceptorManager(disabledConf);
    assertFalse(disabledManager.isEnabled());
    
    disabledManager.addInterceptor(interceptor);
    assertEquals(0, disabledManager.getInterceptorCount());
  }

  @Test
  public void testUserBasedInterception() throws IOException {
    User testUser = mock(User.class);
    when(testUser.getShortName()).thenReturn("testuser");
    
    RpcCall call = mock(RpcCall.class);
    when(call.getMethodName()).thenReturn("testMethod");
    when(call.getRequestUser()).thenReturn(Optional.of(testUser));
    
    // Test interceptor that blocks specific user
    TestRpcInterceptor blockingInterceptor = new TestRpcInterceptor();
    blockingInterceptor.setBlockUser("testuser");
    manager.addInterceptor(blockingInterceptor);
    
    RpcRequestInterceptor.InterceptResult result = 
      manager.interceptRequest(call, Optional.of(testUser));
    
    assertEquals(RpcRequestInterceptor.InterceptResult.BLOCK, result);
    assertTrue(blockingInterceptor.beforeRequestCalled);
  }

  @Test
  public void testUserBasedInterceptionAllowed() throws IOException {
    User testUser = mock(User.class);
    when(testUser.getShortName()).thenReturn("alloweduser");
    
    RpcCall call = mock(RpcCall.class);
    when(call.getMethodName()).thenReturn("testMethod");
    when(call.getRequestUser()).thenReturn(Optional.of(testUser));
    
    // Test interceptor that allows specific user
    TestRpcInterceptor allowingInterceptor = new TestRpcInterceptor();
    allowingInterceptor.setAllowUser("alloweduser");
    manager.addInterceptor(allowingInterceptor);
    
    RpcRequestInterceptor.InterceptResult result = 
      manager.interceptRequest(call, Optional.of(testUser));
    
    assertEquals(RpcRequestInterceptor.InterceptResult.CONTINUE, result);
    assertTrue(allowingInterceptor.beforeRequestCalled);
  }

  @Test
  public void testAfterRequestCallback() {
    User testUser = mock(User.class);
    when(testUser.getShortName()).thenReturn("testuser");
    
    RpcCall call = mock(RpcCall.class);
    when(call.getMethodName()).thenReturn("testMethod");
    
    TestRpcInterceptor callbackInterceptor = new TestRpcInterceptor();
    callbackInterceptor.setInterceptAll(true);
    manager.addInterceptor(callbackInterceptor);
    
    Object result = new Object();
    manager.afterRequest(call, Optional.of(testUser), result);
    
    assertTrue(callbackInterceptor.afterRequestCalled);
    assertEquals(result, callbackInterceptor.lastResult);
  }

  @Test
  public void testOnRequestFailureCallback() {
    User testUser = mock(User.class);
    when(testUser.getShortName()).thenReturn("testuser");
    
    RpcCall call = mock(RpcCall.class);
    when(call.getMethodName()).thenReturn("testMethod");
    
    TestRpcInterceptor callbackInterceptor = new TestRpcInterceptor();
    callbackInterceptor.setInterceptAll(true);
    manager.addInterceptor(callbackInterceptor);
    
    Throwable exception = new RuntimeException("Test exception");
    manager.onRequestFailure(call, Optional.of(testUser), exception);
    
    assertTrue(callbackInterceptor.onRequestFailureCalled);
    assertEquals(exception, callbackInterceptor.lastException);
  }

  @Test
  public void testUserBasedRpcInterceptorConfiguration() {
    Configuration testConf = HBaseConfiguration.create();
    testConf.setStrings(UserBasedRpcInterceptor.ALLOWED_USERS_KEY, "user1", "user2");
    testConf.setStrings(UserBasedRpcInterceptor.BLOCKED_USERS_KEY, "baduser");
    
    UserBasedRpcInterceptor userInterceptor = new UserBasedRpcInterceptor(testConf);
    
    // Test allowed user
    User allowedUser = mock(User.class);
    when(allowedUser.getShortName()).thenReturn("user1");
    assertTrue(userInterceptor.shouldInterceptUser(Optional.of(allowedUser)));
    
    // Test blocked user
    User blockedUser = mock(User.class);
    when(blockedUser.getShortName()).thenReturn("baduser");
    assertTrue(userInterceptor.shouldInterceptUser(Optional.of(blockedUser)));
    
    // Test unknown user (should be intercepted when allowlist is configured)
    User unknownUser = mock(User.class);
    when(unknownUser.getShortName()).thenReturn("unknown");
    assertTrue(userInterceptor.shouldInterceptUser(Optional.of(unknownUser)));
  }

  @Test
  public void testUserBasedRpcInterceptorBlocking() throws IOException {
    Configuration testConf = HBaseConfiguration.create();
    testConf.setStrings(UserBasedRpcInterceptor.BLOCKED_USERS_KEY, "baduser");
    
    UserBasedRpcInterceptor userInterceptor = new UserBasedRpcInterceptor(testConf);
    
    User blockedUser = mock(User.class);
    when(blockedUser.getShortName()).thenReturn("baduser");
    
    RpcCall call = mock(RpcCall.class);
    when(call.getMethodName()).thenReturn("testMethod");
    
    RpcRequestInterceptor.InterceptResult result = 
      userInterceptor.beforeRequest(call, Optional.of(blockedUser));
    
    assertEquals(RpcRequestInterceptor.InterceptResult.BLOCK, result);
    
    // Check metrics
    UserBasedRpcInterceptor.UserMetrics metrics = 
      userInterceptor.getAllUserMetrics().get("baduser");
    assertNotNull(metrics);
    assertEquals(1, metrics.getRequestCount());
    assertEquals(1, metrics.getBlockedCount());
  }

  @Test
  public void testUserBasedRpcInterceptorMetrics() throws IOException {
    UserBasedRpcInterceptor userInterceptor = new UserBasedRpcInterceptor();
    
    User testUser = mock(User.class);
    when(testUser.getShortName()).thenReturn("testuser");
    
    RpcCall call = mock(RpcCall.class);
    when(call.getMethodName()).thenReturn("testMethod");
    
    // Test successful request
    userInterceptor.beforeRequest(call, Optional.of(testUser));
    userInterceptor.afterRequest(call, Optional.of(testUser), new Object());
    
    UserBasedRpcInterceptor.UserMetrics metrics = 
      userInterceptor.getAllUserMetrics().get("testuser");
    assertNotNull(metrics);
    assertEquals(1, metrics.getRequestCount());
    assertEquals(1, metrics.getSuccessCount());
    assertEquals(0, metrics.getFailureCount());
    assertEquals(0, metrics.getBlockedCount());
    
    // Test failed request
    userInterceptor.onRequestFailure(call, Optional.of(testUser), new RuntimeException());
    
    assertEquals(1, metrics.getRequestCount());
    assertEquals(1, metrics.getSuccessCount());
    assertEquals(1, metrics.getFailureCount());
    assertEquals(0, metrics.getBlockedCount());
  }

  /**
   * Test implementation of RpcRequestInterceptor for testing purposes.
   */
  private static class TestRpcInterceptor implements RpcRequestInterceptor {
    private String blockUser;
    private String allowUser;
    private boolean interceptAll = false;
    
    boolean beforeRequestCalled = false;
    boolean afterRequestCalled = false;
    boolean onRequestFailureCalled = false;
    Object lastResult;
    Throwable lastException;

    public void setBlockUser(String user) {
      this.blockUser = user;
    }
    
    public void setAllowUser(String user) {
      this.allowUser = user;
    }
    
    public void setInterceptAll(boolean interceptAll) {
      this.interceptAll = interceptAll;
    }

    @Override
    public InterceptResult beforeRequest(RpcCall call, Optional<User> user) throws IOException {
      beforeRequestCalled = true;
      
      if (blockUser != null && user.isPresent() && blockUser.equals(user.get().getShortName())) {
        return InterceptResult.BLOCK;
      }
      
      return InterceptResult.CONTINUE;
    }

    @Override
    public void afterRequest(RpcCall call, Optional<User> user, Object result) {
      afterRequestCalled = true;
      lastResult = result;
    }

    @Override
    public void onRequestFailure(RpcCall call, Optional<User> user, Throwable exception) {
      onRequestFailureCalled = true;
      lastException = exception;
    }

    @Override
    public boolean shouldInterceptUser(Optional<User> user) {
      if (interceptAll) return true;
      
      if (blockUser != null && user.isPresent() && blockUser.equals(user.get().getShortName())) {
        return true;
      }
      
      if (allowUser != null && user.isPresent() && allowUser.equals(user.get().getShortName())) {
        return true;
      }
      
      return false;
    }

    @Override
    public boolean shouldInterceptUniqueId(RpcCall call) {
      return interceptAll;
    }

    @Override
    public int getPriority() {
      return 100;
    }
  }
}