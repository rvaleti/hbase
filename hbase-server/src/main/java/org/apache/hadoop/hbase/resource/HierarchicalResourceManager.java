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
package org.apache.hadoop.hbase.resource;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.resource.scheduler.HierarchicalScheduler;
import org.apache.hadoop.hbase.resource.scheduler.SchedulerPolicy;
import org.apache.hadoop.hbase.resource.throttle.ResourceThrottleManager;
import org.apache.hadoop.hbase.resource.metrics.ResourceMetricsCollector;
import org.apache.hadoop.hbase.resource.config.ResourceConfigManager;
import org.apache.yetus.audience.InterfaceAudience;
import org.apache.yetus.audience.InterfaceStability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hierarchical Resource Manager for Apache HBase
 * 
 * Provides comprehensive resource management with:
 * - Multi-tier hierarchical scheduling
 * - Configurable fair scheduling algorithms
 * - Dynamic throttling with multiple thresholds
 * - Real-time monitoring and metrics
 * - Web-based administration interface
 */
@InterfaceAudience.Private
@InterfaceStability.Unstable
public class HierarchicalResourceManager {
  private static final Logger LOG = LoggerFactory.getLogger(HierarchicalResourceManager.class);
  
  // Configuration keys
  public static final String HIERARCHICAL_RESOURCE_MANAGER_ENABLED = 
      "hbase.resource.hierarchical.enabled";
  public static final String HIERARCHICAL_SCHEDULER_POLICY = 
      "hbase.resource.hierarchical.scheduler.policy";
  public static final String HIERARCHICAL_THROTTLE_ENABLED = 
      "hbase.resource.hierarchical.throttle.enabled";
  public static final String HIERARCHICAL_METRICS_UPDATE_INTERVAL = 
      "hbase.resource.hierarchical.metrics.update.interval.ms";
  
  // Default values
  public static final boolean DEFAULT_HIERARCHICAL_RESOURCE_MANAGER_ENABLED = true;
  public static final String DEFAULT_HIERARCHICAL_SCHEDULER_POLICY = "FAIR_SHARE";
  public static final boolean DEFAULT_HIERARCHICAL_THROTTLE_ENABLED = true;
  public static final long DEFAULT_HIERARCHICAL_METRICS_UPDATE_INTERVAL = 5000; // 5 seconds
  
  private final Configuration conf;
  private final boolean enabled;
  
  // Core components
  private final HierarchicalScheduler scheduler;
  private final ResourceThrottleManager throttleManager;
  private final ResourceMetricsCollector metricsCollector;
  private final ResourceConfigManager configManager;
  
  // Resource tracking
  private final Map<String, ResourcePool> resourcePools = new ConcurrentHashMap<>();
  private final AtomicLong totalAllocatedMemory = new AtomicLong(0);
  private final AtomicLong totalAllocatedCpu = new AtomicLong(0);
  private final AtomicLong totalAllocatedIO = new AtomicLong(0);
  
  // Executor for background tasks
  private final ScheduledExecutorService executor;
  
  // State
  private volatile boolean running = false;
  
  public HierarchicalResourceManager(Configuration conf) {
    this.conf = conf;
    this.enabled = conf.getBoolean(HIERARCHICAL_RESOURCE_MANAGER_ENABLED, 
        DEFAULT_HIERARCHICAL_RESOURCE_MANAGER_ENABLED);
    
    if (!enabled) {
      LOG.info("Hierarchical Resource Manager is disabled");
      this.scheduler = null;
      this.throttleManager = null;
      this.metricsCollector = null;
      this.configManager = null;
      this.executor = null;
      return;
    }
    
    LOG.info("Initializing Hierarchical Resource Manager");
    
    // Initialize core components
    this.configManager = new ResourceConfigManager(conf);
    this.scheduler = new HierarchicalScheduler(conf, configManager);
    this.throttleManager = new ResourceThrottleManager(conf, configManager);
    this.metricsCollector = new ResourceMetricsCollector(conf);
    
    // Initialize executor
    this.executor = new ScheduledThreadPoolExecutor(4, 
        r -> {
          Thread t = new Thread(r, "HierarchicalResourceManager-Worker");
          t.setDaemon(true);
          return t;
        });
    
    // Initialize default resource pools
    initializeDefaultResourcePools();
  }
  
  /**
   * Start the hierarchical resource manager
   */
  public synchronized void start() throws IOException {
    if (!enabled || running) {
      return;
    }
    
    LOG.info("Starting Hierarchical Resource Manager");
    
    // Start core components
    configManager.start();
    scheduler.start();
    throttleManager.start();
    metricsCollector.start();
    
    // Schedule periodic tasks
    long metricsInterval = conf.getLong(HIERARCHICAL_METRICS_UPDATE_INTERVAL, 
        DEFAULT_HIERARCHICAL_METRICS_UPDATE_INTERVAL);
    
    executor.scheduleAtFixedRate(this::updateMetrics, 0, metricsInterval, TimeUnit.MILLISECONDS);
    executor.scheduleAtFixedRate(this::rebalanceResources, 30000, 30000, TimeUnit.MILLISECONDS);
    executor.scheduleAtFixedRate(this::enforceThrottling, 1000, 1000, TimeUnit.MILLISECONDS);
    
    running = true;
    LOG.info("Hierarchical Resource Manager started successfully");
  }
  
  /**
   * Stop the hierarchical resource manager
   */
  public synchronized void stop() {
    if (!enabled || !running) {
      return;
    }
    
    LOG.info("Stopping Hierarchical Resource Manager");
    
    running = false;
    
    if (executor != null) {
      executor.shutdown();
      try {
        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
          executor.shutdownNow();
        }
      } catch (InterruptedException e) {
        executor.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
    
    // Stop core components
    if (metricsCollector != null) metricsCollector.stop();
    if (throttleManager != null) throttleManager.stop();
    if (scheduler != null) scheduler.stop();
    if (configManager != null) configManager.stop();
    
    LOG.info("Hierarchical Resource Manager stopped");
  }
  
  /**
   * Allocate resources for a request
   */
  public ResourceAllocation allocateResources(ResourceRequest request) {
    if (!enabled || !running) {
      return new ResourceAllocation(request, true, "Resource management disabled");
    }
    
    try {
      // Check throttling first
      if (!throttleManager.allowRequest(request)) {
        return new ResourceAllocation(request, false, "Request throttled");
      }
      
      // Schedule the request
      ResourceAllocation allocation = scheduler.scheduleRequest(request);
      
      if (allocation.isGranted()) {
        // Update resource tracking
        updateResourceUsage(allocation);
        
        // Update metrics
        metricsCollector.recordAllocation(allocation);
      }
      
      return allocation;
      
    } catch (Exception e) {
      LOG.error("Error allocating resources for request: " + request, e);
      return new ResourceAllocation(request, false, "Internal error: " + e.getMessage());
    }
  }
  
  /**
   * Release resources for a completed request
   */
  public void releaseResources(ResourceAllocation allocation) {
    if (!enabled || !running || allocation == null || !allocation.isGranted()) {
      return;
    }
    
    try {
      // Release from scheduler
      scheduler.releaseResources(allocation);
      
      // Update resource tracking
      releaseResourceUsage(allocation);
      
      // Update metrics
      metricsCollector.recordRelease(allocation);
      
    } catch (Exception e) {
      LOG.error("Error releasing resources for allocation: " + allocation, e);
    }
  }
  
  /**
   * Get current resource pool information
   */
  public Map<String, ResourcePool> getResourcePools() {
    return new ConcurrentHashMap<>(resourcePools);
  }
  
  /**
   * Get resource usage statistics
   */
  public ResourceUsageStats getResourceUsageStats() {
    return new ResourceUsageStats(
        totalAllocatedMemory.get(),
        totalAllocatedCpu.get(),
        totalAllocatedIO.get(),
        resourcePools.size(),
        running
    );
  }
  
  /**
   * Update scheduler configuration
   */
  public void updateSchedulerConfig(SchedulerConfig config) throws IOException {
    if (!enabled || !running) {
      throw new IOException("Resource manager not running");
    }
    
    configManager.updateSchedulerConfig(config);
    scheduler.updateConfiguration(config);
  }
  
  /**
   * Update throttling configuration
   */
  public void updateThrottlingConfig(ThrottlingConfig config) throws IOException {
    if (!enabled || !running) {
      throw new IOException("Resource manager not running");
    }
    
    configManager.updateThrottlingConfig(config);
    throttleManager.updateConfiguration(config);
  }
  
  // Private helper methods
  
  private void initializeDefaultResourcePools() {
    // Create default hierarchical pools
    resourcePools.put("root", new ResourcePool("root", null, 1.0f));
    resourcePools.put("root.system", new ResourcePool("root.system", "root", 0.3f));
    resourcePools.put("root.user", new ResourcePool("root.user", "root", 0.7f));
    resourcePools.put("root.user.high", new ResourcePool("root.user.high", "root.user", 0.4f));
    resourcePools.put("root.user.normal", new ResourcePool("root.user.normal", "root.user", 0.4f));
    resourcePools.put("root.user.low", new ResourcePool("root.user.low", "root.user", 0.2f));
  }
  
  private void updateMetrics() {
    try {
      metricsCollector.updateResourcePoolMetrics(resourcePools);
      metricsCollector.updateGlobalMetrics(getResourceUsageStats());
    } catch (Exception e) {
      LOG.warn("Error updating metrics", e);
    }
  }
  
  private void rebalanceResources() {
    try {
      scheduler.rebalanceResources();
    } catch (Exception e) {
      LOG.warn("Error rebalancing resources", e);
    }
  }
  
  private void enforceThrottling() {
    try {
      throttleManager.enforceThrottling();
    } catch (Exception e) {
      LOG.warn("Error enforcing throttling", e);
    }
  }
  
  private void updateResourceUsage(ResourceAllocation allocation) {
    ResourceRequest request = allocation.getRequest();
    totalAllocatedMemory.addAndGet(request.getMemoryMB());
    totalAllocatedCpu.addAndGet(request.getCpuCores());
    totalAllocatedIO.addAndGet(request.getIoWeight());
  }
  
  private void releaseResourceUsage(ResourceAllocation allocation) {
    ResourceRequest request = allocation.getRequest();
    totalAllocatedMemory.addAndGet(-request.getMemoryMB());
    totalAllocatedCpu.addAndGet(-request.getCpuCores());
    totalAllocatedIO.addAndGet(-request.getIoWeight());
  }
  
  public boolean isEnabled() {
    return enabled;
  }
  
  public boolean isRunning() {
    return running;
  }
}