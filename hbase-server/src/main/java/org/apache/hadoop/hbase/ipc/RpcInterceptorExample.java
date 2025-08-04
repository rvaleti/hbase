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

import java.io.IOException;
import java.util.Optional;
import org.apache.hadoop.hbase.security.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example RPC Interceptor Framework Usage and Configuration
 * 
 * This class demonstrates how to implement custom RPC interceptors for HBase
 * to filter requests based on user identity or unique request IDs.
 * 
 * Configuration Example (hbase-site.xml):
 * 
 * <!-- Enable RPC interception -->
 * <property>
 *   <name>hbase.rpc.interception.enabled</name>
 *   <value>true</value>
 * </property>
 * 
 * <!-- Configure interceptor classes -->
 * <property>
 *   <name>hbase.rpc.interceptor.classes</name>
 *   <value>org.apache.hadoop.hbase.ipc.UserBasedRpcInterceptor,com.example.CustomInterceptor</value>
 * </property>
 * 
 * <!-- User-based filtering -->
 * <property>
 *   <name>hbase.rpc.interceptor.allowed.users</name>
 *   <value>admin,service_account,trusted_user</value>
 * </property>
 * 
 * <property>
 *   <name>hbase.rpc.interceptor.blocked.users</name>
 *   <value>suspicious_user,banned_account</value>
 * </property>
 * 
 * <!-- Unique ID based filtering -->
 * <property>
 *   <name>hbase.rpc.interceptor.unique.id.header</name>
 *   <value>X-Request-ID</value>
 * </property>
 * 
 * <property>
 *   <name>hbase.rpc.interceptor.allowed.unique.ids</name>
 *   <value>trusted-client-123,internal-service-456</value>
 * </property>
 * 
 * <property>
 *   <name>hbase.rpc.interceptor.blocked.unique.ids</name>
 *   <value>malicious-client-999,suspicious-id-888</value>
 * </property>
 * 
 * Usage Examples:
 * 
 * 1. Block specific users:
 *    - Configure blocked.users with comma-separated usernames
 *    - All requests from these users will be rejected
 * 
 * 2. Allow only specific users:
 *    - Configure allowed.users with comma-separated usernames  
 *    - Only requests from these users will be allowed
 *    - All other users will be blocked
 * 
 * 3. Monitor user activity:
 *    - Interceptors track metrics per user (requests, successes, failures, blocks)
 *    - Use these metrics for auditing and monitoring
 * 
 * 4. Custom filtering logic:
 *    - Implement RpcRequestInterceptor interface
 *    - Add custom business logic for request filtering
 *    - Register via configuration or programmatically
 */
public class RpcInterceptorExample {

  /**
   * Example custom interceptor that demonstrates advanced filtering logic.
   */
  public static class CustomSecurityInterceptor implements RpcRequestInterceptor {
    private static final Logger LOG = LoggerFactory.getLogger(CustomSecurityInterceptor.class);

    @Override
    public InterceptResult beforeRequest(RpcCall call, Optional<User> user) throws IOException {
      String userName = user.map(User::getShortName).orElse("ANONYMOUS");
      String methodName = call.getMethodName();
      
      // Example: Block anonymous users from administrative operations
      if (!user.isPresent() && isAdminOperation(methodName)) {
        LOG.warn("Blocking anonymous user from admin operation: {}", methodName);
        return InterceptResult.BLOCK;
      }
      
      // Example: Rate limiting - block if user has made too many requests recently
      if (isRateLimited(userName)) {
        LOG.warn("Rate limiting user {} for method {}", userName, methodName);
        return InterceptResult.BLOCK;
      }
      
      // Example: Time-based restrictions
      if (isOutsideAllowedHours()) {
        LOG.warn("Blocking request outside allowed hours from user {}", userName);
        return InterceptResult.BLOCK;
      }
      
      LOG.debug("Allowing request from user {} for method {}", userName, methodName);
      return InterceptResult.CONTINUE;
    }

    @Override
    public void afterRequest(RpcCall call, Optional<User> user, Object result) {
      // Example: Log successful administrative operations
      String userName = user.map(User::getShortName).orElse("ANONYMOUS");
      String methodName = call.getMethodName();
      
      if (isAdminOperation(methodName)) {
        LOG.info("Admin operation {} completed successfully by user {}", methodName, userName);
      }
    }

    @Override
    public void onRequestFailure(RpcCall call, Optional<User> user, Throwable exception) {
      // Example: Log failed requests for security monitoring
      String userName = user.map(User::getShortName).orElse("ANONYMOUS");
      String methodName = call.getMethodName();
      
      LOG.warn("Request failed for user {} on method {}: {}", 
               userName, methodName, exception.getMessage());
    }

    @Override
    public boolean shouldInterceptUser(Optional<User> user) {
      // Intercept all requests for comprehensive security checking
      return true;
    }

    @Override
    public boolean shouldInterceptUniqueId(RpcCall call) {
      // Intercept based on unique ID if needed
      return false;
    }

    @Override
    public int getPriority() {
      return 10; // High priority for security checks
    }

    private boolean isAdminOperation(String methodName) {
      // Example admin operations that require authentication
      return methodName.contains("CreateTable") || 
             methodName.contains("DeleteTable") || 
             methodName.contains("ModifyTable") ||
             methodName.contains("Shutdown");
    }

    private boolean isRateLimited(String userName) {
      // Example: Implement rate limiting logic
      // This would typically check against a cache/store of recent requests
      return false; // Placeholder implementation
    }

    private boolean isOutsideAllowedHours() {
      // Example: Check if current time is within allowed hours
      // This could be based on configuration or business rules
      return false; // Placeholder implementation
    }
  }

  /**
   * Example of programmatically registering interceptors.
   */
  public static void registerCustomInterceptors(RpcServer rpcServer) {
    RpcInterceptorManager manager = rpcServer.interceptorManager;
    
    // Add custom security interceptor
    manager.addInterceptor(new CustomSecurityInterceptor());
    
    // Add audit logging interceptor
    manager.addInterceptor(new AuditLoggingInterceptor());
  }

  /**
   * Example audit logging interceptor.
   */
  public static class AuditLoggingInterceptor implements RpcRequestInterceptor {
    private static final Logger AUDIT_LOG = 
      LoggerFactory.getLogger("AUDIT." + AuditLoggingInterceptor.class.getName());

    @Override
    public InterceptResult beforeRequest(RpcCall call, Optional<User> user) throws IOException {
      String userName = user.map(User::getShortName).orElse("ANONYMOUS");
      String methodName = call.getMethodName();
      String clientAddress = call.getRemoteAddress().getHostAddress();
      
      AUDIT_LOG.info("RPC_REQUEST user={} method={} client={} timestamp={}", 
                     userName, methodName, clientAddress, System.currentTimeMillis());
      
      return InterceptResult.CONTINUE;
    }

    @Override
    public void afterRequest(RpcCall call, Optional<User> user, Object result) {
      String userName = user.map(User::getShortName).orElse("ANONYMOUS");
      String methodName = call.getMethodName();
      
      AUDIT_LOG.info("RPC_SUCCESS user={} method={} timestamp={}", 
                     userName, methodName, System.currentTimeMillis());
    }

    @Override
    public void onRequestFailure(RpcCall call, Optional<User> user, Throwable exception) {
      String userName = user.map(User::getShortName).orElse("ANONYMOUS");
      String methodName = call.getMethodName();
      
      AUDIT_LOG.warn("RPC_FAILURE user={} method={} error={} timestamp={}", 
                     userName, methodName, exception.getMessage(), System.currentTimeMillis());
    }

    @Override
    public boolean shouldInterceptUser(Optional<User> user) {
      return true; // Log all requests
    }

    @Override
    public boolean shouldInterceptUniqueId(RpcCall call) {
      return false;
    }

    @Override
    public int getPriority() {
      return 200; // Lower priority - runs after security checks
    }
  }
}