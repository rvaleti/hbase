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
import org.apache.yetus.audience.InterfaceAudience;

/**
 * Interface for intercepting RPC requests based on user identity or unique identifiers.
 * Implementations can filter, monitor, or modify requests before they are processed
 * by the HBase RPC server.
 */
@InterfaceAudience.Private
public interface RpcRequestInterceptor {

  /**
   * Called before an RPC request is processed. Can be used to filter requests,
   * apply security policies, or collect metrics.
   * 
   * @param call the incoming RPC call
   * @param user the user making the request (if available)
   * @return InterceptResult indicating how to proceed with the request
   * @throws IOException if interception fails
   */
  InterceptResult beforeRequest(RpcCall call, Optional<User> user) throws IOException;

  /**
   * Called after an RPC request has been processed successfully.
   * 
   * @param call the RPC call that was processed
   * @param user the user who made the request (if available)
   * @param result the result of the call
   */
  void afterRequest(RpcCall call, Optional<User> user, Object result);

  /**
   * Called when an RPC request fails with an exception.
   * 
   * @param call the RPC call that failed
   * @param user the user who made the request (if available)
   * @param exception the exception that occurred
   */
  void onRequestFailure(RpcCall call, Optional<User> user, Throwable exception);

  /**
   * Check if this interceptor should handle requests from the given user.
   * 
   * @param user the user to check
   * @return true if this interceptor should handle requests from this user
   */
  boolean shouldInterceptUser(Optional<User> user);

  /**
   * Check if this interceptor should handle requests with the given unique ID.
   * The unique ID can be extracted from request headers or other call metadata.
   * 
   * @param call the RPC call containing potential unique ID
   * @return true if this interceptor should handle this request
   */
  boolean shouldInterceptUniqueId(RpcCall call);

  /**
   * Get the priority of this interceptor. Lower numbers indicate higher priority.
   * Interceptors are executed in priority order.
   * 
   * @return priority value
   */
  default int getPriority() {
    return 100;
  }

  /**
   * Result of request interception indicating how to proceed.
   */
  enum InterceptResult {
    /** Continue processing the request normally */
    CONTINUE,
    /** Block/reject the request */
    BLOCK,
    /** Skip remaining interceptors but continue processing */
    SKIP_REMAINING
  }
}