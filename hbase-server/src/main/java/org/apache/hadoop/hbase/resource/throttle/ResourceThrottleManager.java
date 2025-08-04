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
package org.apache.hadoop.hbase.resource.throttle;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.resource.ResourceRequest;
import org.apache.hadoop.hbase.resource.ThrottlingConfig;
import org.apache.hadoop.hbase.resource.config.ResourceConfigManager;
import org.apache.yetus.audience.InterfaceAudience;
import org.apache.yetus.audience.InterfaceStability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Multi-level throttling manager for resource requests
 */
@InterfaceAudience.Private
@InterfaceStability.Unstable
public class ResourceThrottleManager {
  private static final Logger LOG = LoggerFactory.getLogger(ResourceThrottleManager.class);
  
  // Configuration keys
  public static final String THROTTLE_ENABLED = "hbase.resource.throttle.enabled";
  public static final String THROTTLE_GLOBAL_RPS_LIMIT = "hbase.resource.throttle.global.rps.limit";
  public static final String THROTTLE_USER_RPS_LIMIT = "hbase.resource.throttle.user.rps.limit";
  public static final String THROTTLE_NAMESPACE_RPS_LIMIT = "hbase.resource.throttle.namespace.rps.limit";
  public static final String THROTTLE_TABLE_RPS_LIMIT = "hbase.resource.throttle.table.rps.limit";
  public static final String THROTTLE_MEMORY_THRESHOLD = "hbase.resource.throttle.memory.threshold";
  public static final String THROTTLE_CPU_THRESHOLD = "hbase.resource.throttle.cpu.threshold";
  public static final String THROTTLE_IO_THRESHOLD = "hbase.resource.throttle.io.threshold";
  
  // Default values
  public static final boolean DEFAULT_THROTTLE_ENABLED = true;
  public static final long DEFAULT_GLOBAL_RPS_LIMIT = 10000;
  public static final long DEFAULT_USER_RPS_LIMIT = 1000;
  public static final long DEFAULT_NAMESPACE_RPS_LIMIT = 5000;
  public static final long DEFAULT_TABLE_RPS_LIMIT = 2000;
  public static final double DEFAULT_MEMORY_THRESHOLD = 0.8;
  public static final double DEFAULT_CPU_THRESHOLD = 0.8;
  public static final double DEFAULT_IO_THRESHOLD = 0.8;
  
  private final Configuration conf;
  private final ResourceConfigManager configManager;
  
  // Throttling configuration
  private volatile boolean enabled;
  private volatile long globalRpsLimit;
  private volatile long userRpsLimit;
  private volatile long namespaceRpsLimit;
  private volatile long tableRpsLimit;
  private volatile double memoryThreshold;
  private volatile double cpuThreshold;
  private volatile double ioThreshold;
  
  // Rate limiters
  private final Map<String, RateLimiter> userRateLimiters = new ConcurrentHashMap<>();
  private final Map<String, RateLimiter> namespaceRateLimiters = new ConcurrentHashMap<>();
  private final Map<String, RateLimiter> tableRateLimiters = new ConcurrentHashMap<>();
  private final RateLimiter globalRateLimiter;
  
  // Metrics
  private final AtomicLong totalThrottledRequests = new AtomicLong(0);
  private final AtomicLong globalThrottledRequests = new AtomicLong(0);
  private final AtomicLong userThrottledRequests = new AtomicLong(0);
  private final AtomicLong namespaceThrottledRequests = new AtomicLong(0);
  private final AtomicLong tableThrottledRequests = new AtomicLong(0);
  private final AtomicLong resourceThrottledRequests = new AtomicLong(0);
  
  // State
  private volatile boolean running = false;
  
  public ResourceThrottleManager(Configuration conf, ResourceConfigManager configManager) {
    this.conf = conf;
    this.configManager = configManager;
    
    // Load configuration
    loadConfiguration();
    
    // Initialize global rate limiter
    this.globalRateLimiter = new TokenBucketRateLimiter(globalRpsLimit);
  }
  
  public void start() throws IOException {
    LOG.info("Starting Resource Throttle Manager");
    running = true;
  }
  
  public void stop() {
    LOG.info("Stopping Resource Throttle Manager");
    running = false;
    
    // Clear rate limiters
    userRateLimiters.clear();
    namespaceRateLimiters.clear();
    tableRateLimiters.clear();
  }
  
  /**
   * Check if a request should be allowed or throttled
   */
  public boolean allowRequest(ResourceRequest request) {
    if (!enabled || !running) {
      return true;
    }
    
    // Check global rate limit
    if (!globalRateLimiter.tryAcquire()) {
      globalThrottledRequests.incrementAndGet();
      totalThrottledRequests.incrementAndGet();
      LOG.debug("Request throttled by global rate limit: {}", request.getRequestId());
      return false;
    }
    
    // Check user rate limit
    String userId = request.getUserId();
    if (userId != null) {
      RateLimiter userLimiter = userRateLimiters.computeIfAbsent(userId, 
          k -> new TokenBucketRateLimiter(userRpsLimit));
      
      if (!userLimiter.tryAcquire()) {
        userThrottledRequests.incrementAndGet();
        totalThrottledRequests.incrementAndGet();
        LOG.debug("Request throttled by user rate limit: user={}, request={}", 
            userId, request.getRequestId());
        return false;
      }
    }
    
    // Check namespace rate limit
    String namespace = request.getNamespace();
    if (namespace != null) {
      RateLimiter namespaceLimiter = namespaceRateLimiters.computeIfAbsent(namespace,
          k -> new TokenBucketRateLimiter(namespaceRpsLimit));
      
      if (!namespaceLimiter.tryAcquire()) {
        namespaceThrottledRequests.incrementAndGet();
        totalThrottledRequests.incrementAndGet();
        LOG.debug("Request throttled by namespace rate limit: namespace={}, request={}", 
            namespace, request.getRequestId());
        return false;
      }
    }
    
    // Check table rate limit
    String tableName = request.getTableName();
    if (tableName != null) {
      RateLimiter tableLimiter = tableRateLimiters.computeIfAbsent(tableName,
          k -> new TokenBucketRateLimiter(tableRpsLimit));
      
      if (!tableLimiter.tryAcquire()) {
        tableThrottledRequests.incrementAndGet();
        totalThrottledRequests.incrementAndGet();
        LOG.debug("Request throttled by table rate limit: table={}, request={}", 
            tableName, request.getRequestId());
        return false;
      }
    }
    
    // Check resource thresholds
    if (!checkResourceThresholds(request)) {
      resourceThrottledRequests.incrementAndGet();
      totalThrottledRequests.incrementAndGet();
      LOG.debug("Request throttled by resource thresholds: {}", request.getRequestId());
      return false;
    }
    
    return true;
  }
  
  /**
   * Update throttling configuration
   */
  public void updateConfiguration(ThrottlingConfig config) {
    if (config.getEnabled() != null) {
      this.enabled = config.getEnabled();
    }
    
    if (config.getGlobalRpsLimit() != null) {
      this.globalRpsLimit = config.getGlobalRpsLimit();
      globalRateLimiter.setRate(globalRpsLimit);
    }
    
    if (config.getUserRpsLimit() != null) {
      this.userRpsLimit = config.getUserRpsLimit();
      updateRateLimiters(userRateLimiters, userRpsLimit);
    }
    
    if (config.getNamespaceRpsLimit() != null) {
      this.namespaceRpsLimit = config.getNamespaceRpsLimit();
      updateRateLimiters(namespaceRateLimiters, namespaceRpsLimit);
    }
    
    if (config.getTableRpsLimit() != null) {
      this.tableRpsLimit = config.getTableRpsLimit();
      updateRateLimiters(tableRateLimiters, tableRpsLimit);
    }
    
    if (config.getMemoryThreshold() != null) {
      this.memoryThreshold = config.getMemoryThreshold();
    }
    
    if (config.getCpuThreshold() != null) {
      this.cpuThreshold = config.getCpuThreshold();
    }
    
    if (config.getIoThreshold() != null) {
      this.ioThreshold = config.getIoThreshold();
    }
    
    LOG.info("Updated throttling configuration: enabled={}, globalRps={}, userRps={}", 
        enabled, globalRpsLimit, userRpsLimit);
  }
  
  /**
   * Enforce throttling (called periodically)
   */
  public void enforceThrottling() {
    if (!enabled || !running) {
      return;
    }
    
    // Clean up inactive rate limiters
    cleanupInactiveRateLimiters();
    
    // Update rate limiter permits
    updateRateLimiterPermits();
  }
  
  /**
   * Get throttling statistics
   */
  public ThrottlingStats getThrottlingStats() {
    return new ThrottlingStats(
        enabled,
        globalRpsLimit,
        userRpsLimit,
        namespaceRpsLimit,
        tableRpsLimit,
        memoryThreshold,
        cpuThreshold,
        ioThreshold,
        totalThrottledRequests.get(),
        globalThrottledRequests.get(),
        userThrottledRequests.get(),
        namespaceThrottledRequests.get(),
        tableThrottledRequests.get(),
        resourceThrottledRequests.get(),
        userRateLimiters.size(),
        namespaceRateLimiters.size(),
        tableRateLimiters.size()
    );
  }
  
  // Private helper methods
  
  private void loadConfiguration() {
    this.enabled = conf.getBoolean(THROTTLE_ENABLED, DEFAULT_THROTTLE_ENABLED);
    this.globalRpsLimit = conf.getLong(THROTTLE_GLOBAL_RPS_LIMIT, DEFAULT_GLOBAL_RPS_LIMIT);
    this.userRpsLimit = conf.getLong(THROTTLE_USER_RPS_LIMIT, DEFAULT_USER_RPS_LIMIT);
    this.namespaceRpsLimit = conf.getLong(THROTTLE_NAMESPACE_RPS_LIMIT, DEFAULT_NAMESPACE_RPS_LIMIT);
    this.tableRpsLimit = conf.getLong(THROTTLE_TABLE_RPS_LIMIT, DEFAULT_TABLE_RPS_LIMIT);
    this.memoryThreshold = conf.getDouble(THROTTLE_MEMORY_THRESHOLD, DEFAULT_MEMORY_THRESHOLD);
    this.cpuThreshold = conf.getDouble(THROTTLE_CPU_THRESHOLD, DEFAULT_CPU_THRESHOLD);
    this.ioThreshold = conf.getDouble(THROTTLE_IO_THRESHOLD, DEFAULT_IO_THRESHOLD);
  }
  
  private boolean checkResourceThresholds(ResourceRequest request) {
    // Check system resource utilization
    Runtime runtime = Runtime.getRuntime();
    
    // Memory check
    long totalMemory = runtime.totalMemory();
    long freeMemory = runtime.freeMemory();
    double memoryUtilization = (double) (totalMemory - freeMemory) / runtime.maxMemory();
    
    if (memoryUtilization > memoryThreshold) {
      LOG.debug("Memory utilization {} exceeds threshold {}", 
          memoryUtilization, memoryThreshold);
      return false;
    }
    
    // CPU check (simplified - would need JMX or other monitoring in production)
    double cpuUtilization = getCpuUtilization();
    if (cpuUtilization > cpuThreshold) {
      LOG.debug("CPU utilization {} exceeds threshold {}", 
          cpuUtilization, cpuThreshold);
      return false;
    }
    
    // IO check (simplified - would need disk/network monitoring in production)
    double ioUtilization = getIoUtilization();
    if (ioUtilization > ioThreshold) {
      LOG.debug("IO utilization {} exceeds threshold {}", 
          ioUtilization, ioThreshold);
      return false;
    }
    
    return true;
  }
  
  private double getCpuUtilization() {
    // Simplified CPU utilization - in production, would use JMX or OS metrics
    return Math.random() * 0.5; // Mock value for demonstration
  }
  
  private double getIoUtilization() {
    // Simplified IO utilization - in production, would use disk/network metrics
    return Math.random() * 0.3; // Mock value for demonstration
  }
  
  private void updateRateLimiters(Map<String, RateLimiter> limiters, long newRate) {
    for (RateLimiter limiter : limiters.values()) {
      limiter.setRate(newRate);
    }
  }
  
  private void cleanupInactiveRateLimiters() {
    // Remove rate limiters that haven't been used recently
    long cutoffTime = System.currentTimeMillis() - 300000; // 5 minutes
    
    cleanupRateLimiterMap(userRateLimiters, cutoffTime);
    cleanupRateLimiterMap(namespaceRateLimiters, cutoffTime);
    cleanupRateLimiterMap(tableRateLimiters, cutoffTime);
  }
  
  private void cleanupRateLimiterMap(Map<String, RateLimiter> limiters, long cutoffTime) {
    limiters.entrySet().removeIf(entry -> entry.getValue().getLastUsedTime() < cutoffTime);
  }
  
  private void updateRateLimiterPermits() {
    // Refresh rate limiter permits
    globalRateLimiter.refill();
    
    for (RateLimiter limiter : userRateLimiters.values()) {
      limiter.refill();
    }
    
    for (RateLimiter limiter : namespaceRateLimiters.values()) {
      limiter.refill();
    }
    
    for (RateLimiter limiter : tableRateLimiters.values()) {
      limiter.refill();
    }
  }
}