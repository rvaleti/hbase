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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.security.User;
import org.apache.hadoop.hbase.ipc.RpcRequestInterceptor.InterceptResult;
import org.apache.yetus.audience.InterfaceAudience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manager for RPC request interceptors. Handles registration, prioritization,
 * and execution of interceptors based on user identity or unique request IDs.
 */
@InterfaceAudience.Private
public class RpcInterceptorManager {
  private static final Logger LOG = LoggerFactory.getLogger(RpcInterceptorManager.class);

  /** Configuration key for enabling RPC interception */
  public static final String RPC_INTERCEPTION_ENABLED_KEY = "hbase.rpc.interception.enabled";
  public static final boolean RPC_INTERCEPTION_ENABLED_DEFAULT = false;

  /** Configuration key for interceptor class names */
  public static final String RPC_INTERCEPTOR_CLASSES_KEY = "hbase.rpc.interceptor.classes";

  private final List<RpcRequestInterceptor> interceptors;
  private final boolean enabled;

  public RpcInterceptorManager(Configuration conf) {
    this.enabled = conf.getBoolean(RPC_INTERCEPTION_ENABLED_KEY, RPC_INTERCEPTION_ENABLED_DEFAULT);
    this.interceptors = new CopyOnWriteArrayList<>();
    
    if (enabled) {
      loadInterceptorsFromConfiguration(conf);
    }
    
    LOG.info("RPC Interceptor Manager initialized with {} interceptors, enabled: {}", 
             interceptors.size(), enabled);
  }

  /**
   * Add an interceptor to the manager.
   * 
   * @param interceptor the interceptor to add
   */
  public void addInterceptor(RpcRequestInterceptor interceptor) {
    if (!enabled) {
      LOG.warn("Attempted to add interceptor {} but interception is disabled", 
               interceptor.getClass().getSimpleName());
      return;
    }
    
    interceptors.add(interceptor);
    // Re-sort by priority
    interceptors.sort(Comparator.comparingInt(RpcRequestInterceptor::getPriority));
    
    LOG.info("Added RPC interceptor: {} with priority {}", 
             interceptor.getClass().getSimpleName(), interceptor.getPriority());
  }

  /**
   * Remove an interceptor from the manager.
   * 
   * @param interceptor the interceptor to remove
   */
  public void removeInterceptor(RpcRequestInterceptor interceptor) {
    if (interceptors.remove(interceptor)) {
      LOG.info("Removed RPC interceptor: {}", interceptor.getClass().getSimpleName());
    }
  }

  /**
   * Intercept an incoming RPC request before processing.
   * 
   * @param call the RPC call
   * @param user the requesting user
   * @return InterceptResult indicating how to proceed
   * @throws IOException if interception fails
   */
  public InterceptResult interceptRequest(RpcCall call, Optional<User> user) throws IOException {
    if (!enabled || interceptors.isEmpty()) {
      return InterceptResult.CONTINUE;
    }

    for (RpcRequestInterceptor interceptor : interceptors) {
      try {
        // Check if this interceptor should handle this request
        if (shouldApplyInterceptor(interceptor, call, user)) {
          InterceptResult result = interceptor.beforeRequest(call, user);
          
          if (LOG.isDebugEnabled()) {
            LOG.debug("Interceptor {} returned {} for user {} on method {}", 
                     interceptor.getClass().getSimpleName(), result,
                     user.map(User::getShortName).orElse("UNKNOWN"),
                     call.getMethodName());
          }
          
          switch (result) {
            case BLOCK:
              LOG.warn("Request blocked by interceptor {} for user {} on method {}",
                      interceptor.getClass().getSimpleName(),
                      user.map(User::getShortName).orElse("UNKNOWN"),
                      call.getMethodName());
              return InterceptResult.BLOCK;
            case SKIP_REMAINING:
              return InterceptResult.SKIP_REMAINING;
            case CONTINUE:
              // Continue to next interceptor
              break;
          }
        }
      } catch (Exception e) {
        LOG.error("Error in interceptor {} for user {} on method {}", 
                 interceptor.getClass().getSimpleName(),
                 user.map(User::getShortName).orElse("UNKNOWN"),
                 call.getMethodName(), e);
        // Continue with other interceptors on error
      }
    }
    
    return InterceptResult.CONTINUE;
  }

  /**
   * Notify interceptors after successful request processing.
   * 
   * @param call the RPC call
   * @param user the requesting user
   * @param result the call result
   */
  public void afterRequest(RpcCall call, Optional<User> user, Object result) {
    if (!enabled || interceptors.isEmpty()) {
      return;
    }

    for (RpcRequestInterceptor interceptor : interceptors) {
      try {
        if (shouldApplyInterceptor(interceptor, call, user)) {
          interceptor.afterRequest(call, user, result);
        }
      } catch (Exception e) {
        LOG.error("Error in interceptor {} after request for user {} on method {}", 
                 interceptor.getClass().getSimpleName(),
                 user.map(User::getShortName).orElse("UNKNOWN"),
                 call.getMethodName(), e);
      }
    }
  }

  /**
   * Notify interceptors of request failure.
   * 
   * @param call the RPC call
   * @param user the requesting user
   * @param exception the exception that occurred
   */
  public void onRequestFailure(RpcCall call, Optional<User> user, Throwable exception) {
    if (!enabled || interceptors.isEmpty()) {
      return;
    }

    for (RpcRequestInterceptor interceptor : interceptors) {
      try {
        if (shouldApplyInterceptor(interceptor, call, user)) {
          interceptor.onRequestFailure(call, user, exception);
        }
      } catch (Exception e) {
        LOG.error("Error in interceptor {} on request failure for user {} on method {}", 
                 interceptor.getClass().getSimpleName(),
                 user.map(User::getShortName).orElse("UNKNOWN"),
                 call.getMethodName(), e);
      }
    }
  }

  /**
   * Check if an interceptor should be applied to this request.
   */
  private boolean shouldApplyInterceptor(RpcRequestInterceptor interceptor, RpcCall call, Optional<User> user) {
    return interceptor.shouldInterceptUser(user) || interceptor.shouldInterceptUniqueId(call);
  }

  /**
   * Load interceptors from configuration.
   */
  private void loadInterceptorsFromConfiguration(Configuration conf) {
    String[] classNames = conf.getStrings(RPC_INTERCEPTOR_CLASSES_KEY);
    if (classNames == null || classNames.length == 0) {
      LOG.info("No RPC interceptor classes configured");
      return;
    }

    for (String className : classNames) {
      try {
        Class<?> clazz = Class.forName(className.trim());
        if (RpcRequestInterceptor.class.isAssignableFrom(clazz)) {
          RpcRequestInterceptor interceptor = (RpcRequestInterceptor) clazz.newInstance();
          addInterceptor(interceptor);
        } else {
          LOG.error("Class {} does not implement RpcRequestInterceptor", className);
        }
      } catch (Exception e) {
        LOG.error("Failed to load RPC interceptor class: " + className, e);
      }
    }
  }

  /**
   * Get the number of registered interceptors.
   * 
   * @return number of interceptors
   */
  public int getInterceptorCount() {
    return interceptors.size();
  }

  /**
   * Check if interception is enabled.
   * 
   * @return true if enabled
   */
  public boolean isEnabled() {
    return enabled;
  }
}