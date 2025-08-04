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
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.security.User;
import org.apache.yetus.audience.InterfaceAudience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RPC interceptor that filters and monitors requests based on user identity
 * and unique IDs. Supports configurable user allowlists/blocklists and
 * tracks request metrics per user.
 */
@InterfaceAudience.Private
public class UserBasedRpcInterceptor implements RpcRequestInterceptor {
  private static final Logger LOG = LoggerFactory.getLogger(UserBasedRpcInterceptor.class);

  /** Configuration key for allowed users */
  public static final String ALLOWED_USERS_KEY = "hbase.rpc.interceptor.allowed.users";
  
  /** Configuration key for blocked users */
  public static final String BLOCKED_USERS_KEY = "hbase.rpc.interceptor.blocked.users";
  
  /** Configuration key for unique ID header name */
  public static final String UNIQUE_ID_HEADER_KEY = "hbase.rpc.interceptor.unique.id.header";
  public static final String UNIQUE_ID_HEADER_DEFAULT = "X-Request-ID";
  
  /** Configuration key for allowed unique IDs */
  public static final String ALLOWED_UNIQUE_IDS_KEY = "hbase.rpc.interceptor.allowed.unique.ids";
  
  /** Configuration key for blocked unique IDs */
  public static final String BLOCKED_UNIQUE_IDS_KEY = "hbase.rpc.interceptor.blocked.unique.ids";

  private final Set<String> allowedUsers;
  private final Set<String> blockedUsers;
  private final Set<String> allowedUniqueIds;
  private final Set<String> blockedUniqueIds;
  private final String uniqueIdHeader;
  private final Map<String, UserMetrics> userMetrics;

  public UserBasedRpcInterceptor() {
    this.allowedUsers = new HashSet<>();
    this.blockedUsers = new HashSet<>();
    this.allowedUniqueIds = new HashSet<>();
    this.blockedUniqueIds = new HashSet<>();
    this.uniqueIdHeader = UNIQUE_ID_HEADER_DEFAULT;
    this.userMetrics = new ConcurrentHashMap<>();
  }

  public UserBasedRpcInterceptor(Configuration conf) {
    this.uniqueIdHeader = conf.get(UNIQUE_ID_HEADER_KEY, UNIQUE_ID_HEADER_DEFAULT);
    this.userMetrics = new ConcurrentHashMap<>();
    
    // Load user configurations
    String[] allowed = conf.getStrings(ALLOWED_USERS_KEY);
    this.allowedUsers = new HashSet<>();
    if (allowed != null) {
      for (String user : allowed) {
        this.allowedUsers.add(user.trim());
      }
    }
    
    String[] blocked = conf.getStrings(BLOCKED_USERS_KEY);
    this.blockedUsers = new HashSet<>();
    if (blocked != null) {
      for (String user : blocked) {
        this.blockedUsers.add(user.trim());
      }
    }
    
    // Load unique ID configurations
    String[] allowedIds = conf.getStrings(ALLOWED_UNIQUE_IDS_KEY);
    this.allowedUniqueIds = new HashSet<>();
    if (allowedIds != null) {
      for (String id : allowedIds) {
        this.allowedUniqueIds.add(id.trim());
      }
    }
    
    String[] blockedIds = conf.getStrings(BLOCKED_UNIQUE_IDS_KEY);
    this.blockedUniqueIds = new HashSet<>();
    if (blockedIds != null) {
      for (String id : blockedIds) {
        this.blockedUniqueIds.add(id.trim());
      }
    }
    
    LOG.info("UserBasedRpcInterceptor initialized - allowed users: {}, blocked users: {}, " +
             "allowed unique IDs: {}, blocked unique IDs: {}", 
             allowedUsers.size(), blockedUsers.size(), 
             allowedUniqueIds.size(), blockedUniqueIds.size());
  }

  @Override
  public InterceptResult beforeRequest(RpcCall call, Optional<User> user) throws IOException {
    String username = user.map(User::getShortName).orElse("ANONYMOUS");
    String uniqueId = extractUniqueId(call);
    
    // Track request metrics
    getUserMetrics(username).incrementRequestCount();
    
    // Check user-based blocking
    if (!blockedUsers.isEmpty() && user.isPresent() && blockedUsers.contains(username)) {
      LOG.warn("Blocking request from blocked user: {} for method: {}", 
               username, call.getMethodName());
      getUserMetrics(username).incrementBlockedCount();
      return InterceptResult.BLOCK;
    }
    
    // Check user-based allowlist (if configured)
    if (!allowedUsers.isEmpty() && user.isPresent() && !allowedUsers.contains(username)) {
      LOG.warn("Blocking request from non-allowed user: {} for method: {}", 
               username, call.getMethodName());
      getUserMetrics(username).incrementBlockedCount();
      return InterceptResult.BLOCK;
    }
    
    // Check unique ID based blocking
    if (uniqueId != null) {
      if (!blockedUniqueIds.isEmpty() && blockedUniqueIds.contains(uniqueId)) {
        LOG.warn("Blocking request with blocked unique ID: {} from user: {} for method: {}", 
                 uniqueId, username, call.getMethodName());
        getUserMetrics(username).incrementBlockedCount();
        return InterceptResult.BLOCK;
      }
      
      // Check unique ID allowlist (if configured)
      if (!allowedUniqueIds.isEmpty() && !allowedUniqueIds.contains(uniqueId)) {
        LOG.warn("Blocking request with non-allowed unique ID: {} from user: {} for method: {}", 
                 uniqueId, username, call.getMethodName());
        getUserMetrics(username).incrementBlockedCount();
        return InterceptResult.BLOCK;
      }
    }
    
    if (LOG.isDebugEnabled()) {
      LOG.debug("Allowing request from user: {} with unique ID: {} for method: {}", 
                username, uniqueId, call.getMethodName());
    }
    
    return InterceptResult.CONTINUE;
  }

  @Override
  public void afterRequest(RpcCall call, Optional<User> user, Object result) {
    String username = user.map(User::getShortName).orElse("ANONYMOUS");
    getUserMetrics(username).incrementSuccessCount();
    
    if (LOG.isDebugEnabled()) {
      LOG.debug("Request completed successfully for user: {} on method: {}", 
                username, call.getMethodName());
    }
  }

  @Override
  public void onRequestFailure(RpcCall call, Optional<User> user, Throwable exception) {
    String username = user.map(User::getShortName).orElse("ANONYMOUS");
    getUserMetrics(username).incrementFailureCount();
    
    LOG.debug("Request failed for user: {} on method: {} with exception: {}", 
              username, call.getMethodName(), exception.getMessage());
  }

  @Override
  public boolean shouldInterceptUser(Optional<User> user) {
    if (user.isPresent()) {
      String username = user.get().getShortName();
      
      // Intercept if user is in blocked list
      if (!blockedUsers.isEmpty() && blockedUsers.contains(username)) {
        return true;
      }
      
      // Intercept if allowlist is configured and user is not in it
      if (!allowedUsers.isEmpty() && !allowedUsers.contains(username)) {
        return true;
      }
      
      // Intercept if any user filtering is configured
      return !allowedUsers.isEmpty() || !blockedUsers.isEmpty();
    }
    
    // Intercept anonymous users if any user filtering is configured
    return !allowedUsers.isEmpty() || !blockedUsers.isEmpty();
  }

  @Override
  public boolean shouldInterceptUniqueId(RpcCall call) {
    String uniqueId = extractUniqueId(call);
    if (uniqueId == null) {
      // Intercept if unique ID filtering is configured but no ID present
      return !allowedUniqueIds.isEmpty() || !blockedUniqueIds.isEmpty();
    }
    
    // Intercept if unique ID is in blocked list
    if (!blockedUniqueIds.isEmpty() && blockedUniqueIds.contains(uniqueId)) {
      return true;
    }
    
    // Intercept if allowlist is configured and unique ID is not in it
    if (!allowedUniqueIds.isEmpty() && !allowedUniqueIds.contains(uniqueId)) {
      return true;
    }
    
    return false;
  }

  @Override
  public int getPriority() {
    return 50; // Higher priority than default
  }

  /**
   * Extract unique ID from RPC call headers or attributes.
   */
  private String extractUniqueId(RpcCall call) {
    // Try to extract from connection header attributes
    if (call instanceof ServerCall) {
      ServerCall<?> serverCall = (ServerCall<?>) call;
      // This would need to be implemented based on how HBase handles custom headers
      // For now, return null as a placeholder
      return null;
    }
    return null;
  }

  /**
   * Get or create metrics for a user.
   */
  private UserMetrics getUserMetrics(String username) {
    return userMetrics.computeIfAbsent(username, k -> new UserMetrics());
  }

  /**
   * Get metrics for all users.
   */
  public Map<String, UserMetrics> getAllUserMetrics() {
    return new ConcurrentHashMap<>(userMetrics);
  }

  /**
   * Reset metrics for all users.
   */
  public void resetMetrics() {
    userMetrics.clear();
  }

  /**
   * Add a user to the allowed list.
   */
  public void addAllowedUser(String username) {
    allowedUsers.add(username);
    LOG.info("Added user {} to allowed list", username);
  }

  /**
   * Remove a user from the allowed list.
   */
  public void removeAllowedUser(String username) {
    allowedUsers.remove(username);
    LOG.info("Removed user {} from allowed list", username);
  }

  /**
   * Add a user to the blocked list.
   */
  public void addBlockedUser(String username) {
    blockedUsers.add(username);
    LOG.info("Added user {} to blocked list", username);
  }

  /**
   * Remove a user from the blocked list.
   */
  public void removeBlockedUser(String username) {
    blockedUsers.remove(username);
    LOG.info("Removed user {} from blocked list", username);
  }

  /**
   * Add a unique ID to the allowed list.
   */
  public void addAllowedUniqueId(String uniqueId) {
    allowedUniqueIds.add(uniqueId);
    LOG.info("Added unique ID {} to allowed list", uniqueId);
  }

  /**
   * Remove a unique ID from the allowed list.
   */
  public void removeAllowedUniqueId(String uniqueId) {
    allowedUniqueIds.remove(uniqueId);
    LOG.info("Removed unique ID {} from allowed list", uniqueId);
  }

  /**
   * Add a unique ID to the blocked list.
   */
  public void addBlockedUniqueId(String uniqueId) {
    blockedUniqueIds.add(uniqueId);
    LOG.info("Added unique ID {} to blocked list", uniqueId);
  }

  /**
   * Remove a unique ID from the blocked list.
   */
  public void removeBlockedUniqueId(String uniqueId) {
    blockedUniqueIds.remove(uniqueId);
    LOG.info("Removed unique ID {} from blocked list", uniqueId);
  }

  /**
   * Metrics tracking for user requests.
   */
  public static class UserMetrics {
    private final AtomicLong requestCount = new AtomicLong(0);
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);
    private final AtomicLong blockedCount = new AtomicLong(0);

    public void incrementRequestCount() {
      requestCount.incrementAndGet();
    }

    public void incrementSuccessCount() {
      successCount.incrementAndGet();
    }

    public void incrementFailureCount() {
      failureCount.incrementAndGet();
    }

    public void incrementBlockedCount() {
      blockedCount.incrementAndGet();
    }

    public long getRequestCount() {
      return requestCount.get();
    }

    public long getSuccessCount() {
      return successCount.get();
    }

    public long getFailureCount() {
      return failureCount.get();
    }

    public long getBlockedCount() {
      return blockedCount.get();
    }

    @Override
    public String toString() {
      return String.format("UserMetrics{requests=%d, success=%d, failures=%d, blocked=%d}",
                          requestCount.get(), successCount.get(), failureCount.get(), blockedCount.get());
    }
  }
}