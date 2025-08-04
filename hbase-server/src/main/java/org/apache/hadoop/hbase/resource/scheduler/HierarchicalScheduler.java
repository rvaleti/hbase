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
package org.apache.hadoop.hbase.resource.scheduler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.resource.*;
import org.apache.hadoop.hbase.resource.config.ResourceConfigManager;
import org.apache.yetus.audience.InterfaceAudience;
import org.apache.yetus.audience.InterfaceStability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hierarchical scheduler for resource management with configurable fair scheduling algorithms
 */
@InterfaceAudience.Private
@InterfaceStability.Unstable
public class HierarchicalScheduler {
  private static final Logger LOG = LoggerFactory.getLogger(HierarchicalScheduler.class);
  
  // Configuration keys
  public static final String SCHEDULER_POLICY = "hbase.resource.scheduler.policy";
  public static final String SCHEDULER_FAIR_SHARE_PREEMPTION_ENABLED = 
      "hbase.resource.scheduler.fair.preemption.enabled";
  public static final String SCHEDULER_FAIR_SHARE_PREEMPTION_TIMEOUT = 
      "hbase.resource.scheduler.fair.preemption.timeout.ms";
  public static final String SCHEDULER_MAX_RESOURCE_ALLOCATION_WAIT = 
      "hbase.resource.scheduler.allocation.wait.max.ms";
  
  // Default values
  public static final String DEFAULT_SCHEDULER_POLICY = "FAIR_SHARE";
  public static final boolean DEFAULT_FAIR_SHARE_PREEMPTION_ENABLED = true;
  public static final long DEFAULT_FAIR_SHARE_PREEMPTION_TIMEOUT = 30000; // 30 seconds
  public static final long DEFAULT_MAX_RESOURCE_ALLOCATION_WAIT = 60000; // 60 seconds
  
  private final Configuration conf;
  private final ResourceConfigManager configManager;
  private final Map<String, ResourcePool> resourcePools = new ConcurrentHashMap<>();
  private final Map<String, PriorityBlockingQueue<ResourceRequest>> requestQueues = new ConcurrentHashMap<>();
  private final Map<String, ResourceAllocation> activeAllocations = new ConcurrentHashMap<>();
  
  // Scheduling policies
  private volatile SchedulerPolicy schedulerPolicy = SchedulerPolicy.FAIR_SHARE;
  private volatile boolean preemptionEnabled;
  private volatile long preemptionTimeoutMs;
  private volatile long maxAllocationWaitMs;
  
  // Metrics
  private final AtomicLong totalScheduledRequests = new AtomicLong(0);
  private final AtomicLong totalGrantedRequests = new AtomicLong(0);
  private final AtomicLong totalRejectedRequests = new AtomicLong(0);
  private final AtomicLong totalPreemptedRequests = new AtomicLong(0);
  
  // State
  private volatile boolean running = false;
  
  public HierarchicalScheduler(Configuration conf, ResourceConfigManager configManager) {
    this.conf = conf;
    this.configManager = configManager;
    
    // Load configuration
    loadConfiguration();
  }
  
  public void start() throws IOException {
    LOG.info("Starting Hierarchical Scheduler with policy: " + schedulerPolicy);
    running = true;
  }
  
  public void stop() {
    LOG.info("Stopping Hierarchical Scheduler");
    running = false;
    
    // Clear queues and active allocations
    requestQueues.clear();
    activeAllocations.clear();
  }
  
  /**
   * Schedule a resource request
   */
  public ResourceAllocation scheduleRequest(ResourceRequest request) {
    if (!running) {
      return new ResourceAllocation(request, false, "Scheduler not running");
    }
    
    totalScheduledRequests.incrementAndGet();
    
    String poolName = determineResourcePool(request);
    ResourcePool pool = resourcePools.get(poolName);
    
    if (pool == null) {
      totalRejectedRequests.incrementAndGet();
      return new ResourceAllocation(request, false, "Resource pool not found: " + poolName);
    }
    
    pool.incrementTotalRequests();
    
    // Check if we can immediately allocate
    if (canImmediatelyAllocate(request, pool)) {
      ResourceAllocation allocation = allocateImmediately(request, pool);
      if (allocation.isGranted()) {
        pool.incrementGrantedRequests();
        totalGrantedRequests.incrementAndGet();
        activeAllocations.put(request.getRequestId(), allocation);
      } else {
        pool.incrementRejectedRequests();
        totalRejectedRequests.incrementAndGet();
      }
      return allocation;
    }
    
    // Queue the request for later scheduling
    return queueRequest(request, pool);
  }
  
  /**
   * Release resources for a completed allocation
   */
  public void releaseResources(ResourceAllocation allocation) {
    if (!running || allocation == null || !allocation.isGranted()) {
      return;
    }
    
    String requestId = allocation.getRequest().getRequestId();
    ResourceAllocation removed = activeAllocations.remove(requestId);
    
    if (removed != null) {
      String poolName = allocation.getAllocatedPool();
      ResourcePool pool = resourcePools.get(poolName);
      
      if (pool != null) {
        pool.deallocateResources(
            allocation.getActualMemoryMB(),
            allocation.getActualCpuCores(),
            allocation.getActualIoWeight()
        );
        
        // Try to schedule waiting requests
        scheduleWaitingRequests(pool);
      }
    }
  }
  
  /**
   * Update scheduler configuration
   */
  public void updateConfiguration(SchedulerConfig config) {
    if (config.getSchedulerPolicy() != null) {
      this.schedulerPolicy = config.getSchedulerPolicy();
    }
    
    if (config.getPreemptionEnabled() != null) {
      this.preemptionEnabled = config.getPreemptionEnabled();
    }
    
    if (config.getPreemptionTimeoutMs() != null) {
      this.preemptionTimeoutMs = config.getPreemptionTimeoutMs();
    }
    
    if (config.getMaxAllocationWaitMs() != null) {
      this.maxAllocationWaitMs = config.getMaxAllocationWaitMs();
    }
    
    LOG.info("Updated scheduler configuration: policy={}, preemption={}", 
        schedulerPolicy, preemptionEnabled);
  }
  
  /**
   * Rebalance resources across pools
   */
  public void rebalanceResources() {
    if (!running) {
      return;
    }
    
    try {
      // Update fair shares for all pools
      updateFairShares();
      
      // Check for preemption opportunities
      if (preemptionEnabled) {
        checkPreemption();
      }
      
      // Try to schedule waiting requests
      scheduleAllWaitingRequests();
      
    } catch (Exception e) {
      LOG.warn("Error during resource rebalancing", e);
    }
  }
  
  /**
   * Set resource pools
   */
  public void setResourcePools(Map<String, ResourcePool> pools) {
    resourcePools.clear();
    resourcePools.putAll(pools);
    
    // Initialize request queues for each pool
    for (String poolName : pools.keySet()) {
      requestQueues.computeIfAbsent(poolName, 
          k -> new PriorityBlockingQueue<>(100, this::compareRequests));
    }
    
    // Update fair shares
    updateFairShares();
  }
  
  /**
   * Get scheduler statistics
   */
  public SchedulerStats getSchedulerStats() {
    Map<String, PoolStats> poolStats = new HashMap<>();
    
    for (Map.Entry<String, ResourcePool> entry : resourcePools.entrySet()) {
      String poolName = entry.getKey();
      ResourcePool pool = entry.getValue();
      PriorityBlockingQueue<ResourceRequest> queue = requestQueues.get(poolName);
      
      poolStats.put(poolName, new PoolStats(
          pool.getTotalRequests(),
          pool.getGrantedRequests(),
          pool.getRejectedRequests(),
          queue != null ? queue.size() : 0,
          pool.getAllocatedMemoryMB(),
          pool.getAllocatedCpuCores(),
          pool.getAllocatedIoWeight(),
          pool.getFairShareMemory(),
          pool.getFairShareCpu(),
          pool.getFairShareIo()
      ));
    }
    
    return new SchedulerStats(
        schedulerPolicy,
        totalScheduledRequests.get(),
        totalGrantedRequests.get(),
        totalRejectedRequests.get(),
        totalPreemptedRequests.get(),
        activeAllocations.size(),
        poolStats
    );
  }
  
  // Private helper methods
  
  private void loadConfiguration() {
    String policyStr = conf.get(SCHEDULER_POLICY, DEFAULT_SCHEDULER_POLICY);
    try {
      this.schedulerPolicy = SchedulerPolicy.valueOf(policyStr);
    } catch (IllegalArgumentException e) {
      LOG.warn("Invalid scheduler policy: {}, using default: {}", policyStr, DEFAULT_SCHEDULER_POLICY);
      this.schedulerPolicy = SchedulerPolicy.valueOf(DEFAULT_SCHEDULER_POLICY);
    }
    
    this.preemptionEnabled = conf.getBoolean(SCHEDULER_FAIR_SHARE_PREEMPTION_ENABLED, 
        DEFAULT_FAIR_SHARE_PREEMPTION_ENABLED);
    this.preemptionTimeoutMs = conf.getLong(SCHEDULER_FAIR_SHARE_PREEMPTION_TIMEOUT, 
        DEFAULT_FAIR_SHARE_PREEMPTION_TIMEOUT);
    this.maxAllocationWaitMs = conf.getLong(SCHEDULER_MAX_RESOURCE_ALLOCATION_WAIT, 
        DEFAULT_MAX_RESOURCE_ALLOCATION_WAIT);
  }
  
  private String determineResourcePool(ResourceRequest request) {
    String requestedPool = request.getResourcePool();
    
    // Validate that the pool exists
    if (resourcePools.containsKey(requestedPool)) {
      return requestedPool;
    }
    
    // Fall back to user-based pool assignment
    String userId = request.getUserId();
    String namespace = request.getNamespace();
    
    // Try namespace-specific pool
    if (namespace != null) {
      String namespacePool = "root.user." + namespace;
      if (resourcePools.containsKey(namespacePool)) {
        return namespacePool;
      }
    }
    
    // Default to normal priority pool
    return "root.user.normal";
  }
  
  private boolean canImmediatelyAllocate(ResourceRequest request, ResourcePool pool) {
    return pool.canAllocate(request.getMemoryMB(), request.getCpuCores(), request.getIoWeight());
  }
  
  private ResourceAllocation allocateImmediately(ResourceRequest request, ResourcePool pool) {
    boolean success = pool.allocateResources(
        request.getMemoryMB(),
        request.getCpuCores(),
        request.getIoWeight()
    );
    
    if (success) {
      return new ResourceAllocation(request, true, "Immediate allocation", pool.getName(),
          request.getMemoryMB(), request.getCpuCores(), request.getIoWeight());
    } else {
      return new ResourceAllocation(request, false, "Allocation failed");
    }
  }
  
  private ResourceAllocation queueRequest(ResourceRequest request, ResourcePool pool) {
    PriorityBlockingQueue<ResourceRequest> queue = requestQueues.get(pool.getName());
    if (queue != null) {
      queue.offer(request);
      pool.setQueuedRequests(queue.size());
      return new ResourceAllocation(request, false, "Queued for scheduling");
    } else {
      pool.incrementRejectedRequests();
      totalRejectedRequests.incrementAndGet();
      return new ResourceAllocation(request, false, "No queue available for pool: " + pool.getName());
    }
  }
  
  private void scheduleWaitingRequests(ResourcePool pool) {
    PriorityBlockingQueue<ResourceRequest> queue = requestQueues.get(pool.getName());
    if (queue == null || queue.isEmpty()) {
      return;
    }
    
    Iterator<ResourceRequest> iterator = queue.iterator();
    while (iterator.hasNext()) {
      ResourceRequest request = iterator.next();
      
      if (canImmediatelyAllocate(request, pool)) {
        iterator.remove();
        ResourceAllocation allocation = allocateImmediately(request, pool);
        if (allocation.isGranted()) {
          pool.incrementGrantedRequests();
          totalGrantedRequests.incrementAndGet();
          activeAllocations.put(request.getRequestId(), allocation);
        }
      }
    }
    
    pool.setQueuedRequests(queue.size());
  }
  
  private void scheduleAllWaitingRequests() {
    for (ResourcePool pool : resourcePools.values()) {
      scheduleWaitingRequests(pool);
    }
  }
  
  private void updateFairShares() {
    // Calculate fair shares based on the scheduler policy
    switch (schedulerPolicy) {
      case FAIR_SHARE:
        updateFairSharesProportional();
        break;
      case DRF:
        updateFairSharesDRF();
        break;
      case FIFO:
        // FIFO doesn't use fair shares
        break;
    }
  }
  
  private void updateFairSharesProportional() {
    // Calculate proportional fair shares based on weights
    Map<String, List<ResourcePool>> hierarchy = buildHierarchy();
    
    for (Map.Entry<String, List<ResourcePool>> entry : hierarchy.entrySet()) {
      String parentName = entry.getKey();
      List<ResourcePool> children = entry.getValue();
      
      if (children.isEmpty()) continue;
      
      // Get parent resources (or system total for root)
      long parentMemory = getParentMemory(parentName);
      long parentCpu = getParentCpu(parentName);
      long parentIo = getParentIo(parentName);
      
      // Calculate total weights
      float totalWeight = children.stream()
          .map(ResourcePool::getShareWeight)
          .reduce(0.0f, Float::sum);
      
      if (totalWeight > 0) {
        // Distribute fair shares proportionally
        for (ResourcePool child : children) {
          double share = child.getShareWeight() / totalWeight;
          child.setFairShareMemory(parentMemory * share);
          child.setFairShareCpu(parentCpu * share);
          child.setFairShareIo(parentIo * share);
        }
      }
    }
  }
  
  private void updateFairSharesDRF() {
    // Dominant Resource Fairness (DRF) algorithm
    // TODO: Implement DRF fair share calculation
    LOG.debug("DRF fair share calculation not yet implemented, falling back to proportional");
    updateFairSharesProportional();
  }
  
  private Map<String, List<ResourcePool>> buildHierarchy() {
    Map<String, List<ResourcePool>> hierarchy = new HashMap<>();
    
    for (ResourcePool pool : resourcePools.values()) {
      String parentName = pool.getParentName();
      if (parentName == null) {
        parentName = "ROOT";
      }
      
      hierarchy.computeIfAbsent(parentName, k -> new ArrayList<>()).add(pool);
    }
    
    return hierarchy;
  }
  
  private long getParentMemory(String parentName) {
    if ("ROOT".equals(parentName)) {
      return Runtime.getRuntime().maxMemory() / (1024 * 1024); // Convert to MB
    }
    
    ResourcePool parent = resourcePools.get(parentName);
    return parent != null ? parent.getMaxMemoryMB() : 0;
  }
  
  private long getParentCpu(String parentName) {
    if ("ROOT".equals(parentName)) {
      return Runtime.getRuntime().availableProcessors();
    }
    
    ResourcePool parent = resourcePools.get(parentName);
    return parent != null ? parent.getMaxCpuCores() : 0;
  }
  
  private long getParentIo(String parentName) {
    if ("ROOT".equals(parentName)) {
      return 10000; // Arbitrary IO weight limit
    }
    
    ResourcePool parent = resourcePools.get(parentName);
    return parent != null ? parent.getMaxIoWeight() : 0;
  }
  
  private void checkPreemption() {
    // Check for preemption opportunities
    long currentTime = System.currentTimeMillis();
    
    for (ResourceAllocation allocation : activeAllocations.values()) {
      if (shouldPreempt(allocation, currentTime)) {
        preemptAllocation(allocation);
      }
    }
  }
  
  private boolean shouldPreempt(ResourceAllocation allocation, long currentTime) {
    // Check if allocation has exceeded fair share and timeout
    String poolName = allocation.getAllocatedPool();
    ResourcePool pool = resourcePools.get(poolName);
    
    if (pool == null) return false;
    
    // Check if this pool is over its fair share
    boolean overFairShare = 
        pool.getAllocatedMemoryMB() > pool.getFairShareMemory() ||
        pool.getAllocatedCpuCores() > pool.getFairShareCpu() ||
        pool.getAllocatedIoWeight() > pool.getFairShareIo();
    
    if (!overFairShare) return false;
    
    // Check timeout
    long allocationAge = currentTime - allocation.getAllocationTime();
    return allocationAge > preemptionTimeoutMs;
  }
  
  private void preemptAllocation(ResourceAllocation allocation) {
    LOG.info("Preempting allocation: {}", allocation.getRequest().getRequestId());
    
    releaseResources(allocation);
    totalPreemptedRequests.incrementAndGet();
    
    // Re-queue the request with lower priority
    ResourceRequest request = allocation.getRequest();
    String poolName = determineResourcePool(request);
    ResourcePool pool = resourcePools.get(poolName);
    
    if (pool != null) {
      queueRequest(request, pool);
    }
  }
  
  private int compareRequests(ResourceRequest r1, ResourceRequest r2) {
    // Compare requests based on scheduling policy
    switch (schedulerPolicy) {
      case FIFO:
        return Long.compare(r1.getSubmissionTime(), r2.getSubmissionTime());
      case FAIR_SHARE:
      case DRF:
      default:
        // Higher score = higher priority (reverse order for min-heap)
        return Double.compare(r2.getResourceScore(), r1.getResourceScore());
    }
  }
}