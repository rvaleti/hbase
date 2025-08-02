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
package org.apache.hadoop.hbase.resource.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.resource.ResourceAllocation;
import org.apache.hadoop.hbase.resource.ResourcePool;
import org.apache.hadoop.hbase.resource.ResourceUsageStats;
import org.apache.yetus.audience.InterfaceAudience;
import org.apache.yetus.audience.InterfaceStability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Metrics collector for the hierarchical resource management system
 */
@InterfaceAudience.Private
@InterfaceStability.Unstable
public class ResourceMetricsCollector {
  private static final Logger LOG = LoggerFactory.getLogger(ResourceMetricsCollector.class);
  
  // Configuration keys
  public static final String METRICS_ENABLED = "hbase.resource.metrics.enabled";
  public static final String METRICS_HISTORY_SIZE = "hbase.resource.metrics.history.size";
  
  // Default values
  public static final boolean DEFAULT_METRICS_ENABLED = true;
  public static final int DEFAULT_METRICS_HISTORY_SIZE = 1000;
  
  private final Configuration conf;
  private final boolean enabled;
  private final int historySize;
  
  // Metrics storage
  private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
  private final Map<String, AtomicLong> gauges = new ConcurrentHashMap<>();
  private final Map<String, HistogramMetric> histograms = new ConcurrentHashMap<>();
  
  // State
  private volatile boolean running = false;
  
  public ResourceMetricsCollector(Configuration conf) {
    this.conf = conf;
    this.enabled = conf.getBoolean(METRICS_ENABLED, DEFAULT_METRICS_ENABLED);
    this.historySize = conf.getInt(METRICS_HISTORY_SIZE, DEFAULT_METRICS_HISTORY_SIZE);
    
    // Initialize metrics
    initializeMetrics();
  }
  
  public void start() {
    LOG.info("Starting Resource Metrics Collector");
    running = true;
  }
  
  public void stop() {
    LOG.info("Stopping Resource Metrics Collector");
    running = false;
    
    // Clear metrics
    counters.clear();
    gauges.clear();
    histograms.clear();
  }
  
  /**
   * Record a resource allocation
   */
  public void recordAllocation(ResourceAllocation allocation) {
    if (!enabled || !running) {
      return;
    }
    
    try {
      // Update counters
      incrementCounter("allocations.total");
      incrementCounter("allocations.granted");
      
      // Update gauges
      updateGauge("allocations.memory.total", allocation.getActualMemoryMB());
      updateGauge("allocations.cpu.total", allocation.getActualCpuCores());
      updateGauge("allocations.io.total", allocation.getActualIoWeight());
      
      // Update histograms
      long waitTime = allocation.getWaitTimeMs();
      updateHistogram("allocation.wait.time.ms", waitTime);
      
      String poolName = allocation.getAllocatedPool();
      incrementCounter("allocations.pool." + poolName);
      
    } catch (Exception e) {
      LOG.warn("Error recording allocation metrics", e);
    }
  }
  
  /**
   * Record a resource release
   */
  public void recordRelease(ResourceAllocation allocation) {
    if (!enabled || !running) {
      return;
    }
    
    try {
      // Update counters
      incrementCounter("releases.total");
      
      // Update gauges (subtract from totals)
      decrementGauge("allocations.memory.total", allocation.getActualMemoryMB());
      decrementGauge("allocations.cpu.total", allocation.getActualCpuCores());
      decrementGauge("allocations.io.total", allocation.getActualIoWeight());
      
      // Update histograms
      long executionTime = allocation.getExecutionTimeMs();
      if (executionTime > 0) {
        updateHistogram("allocation.execution.time.ms", executionTime);
      }
      
      long totalTime = allocation.getTotalTimeMs();
      updateHistogram("allocation.total.time.ms", totalTime);
      
    } catch (Exception e) {
      LOG.warn("Error recording release metrics", e);
    }
  }
  
  /**
   * Update resource pool metrics
   */
  public void updateResourcePoolMetrics(Map<String, ResourcePool> pools) {
    if (!enabled || !running) {
      return;
    }
    
    try {
      for (Map.Entry<String, ResourcePool> entry : pools.entrySet()) {
        String poolName = entry.getKey();
        ResourcePool pool = entry.getValue();
        
        // Update pool-specific gauges
        updateGauge("pool." + poolName + ".memory.allocated", pool.getAllocatedMemoryMB());
        updateGauge("pool." + poolName + ".cpu.allocated", pool.getAllocatedCpuCores());
        updateGauge("pool." + poolName + ".io.allocated", pool.getAllocatedIoWeight());
        
        updateGauge("pool." + poolName + ".memory.used", pool.getUsedMemoryMB());
        updateGauge("pool." + poolName + ".cpu.used", pool.getUsedCpuCores());
        updateGauge("pool." + poolName + ".io.used", pool.getUsedIoWeight());
        
        updateGauge("pool." + poolName + ".memory.utilization", 
            (long) (pool.getMemoryUtilization() * 1000)); // Store as per-mille
        updateGauge("pool." + poolName + ".cpu.utilization", 
            (long) (pool.getCpuUtilization() * 1000));
        updateGauge("pool." + poolName + ".io.utilization", 
            (long) (pool.getIoUtilization() * 1000));
        
        updateGauge("pool." + poolName + ".requests.total", pool.getTotalRequests());
        updateGauge("pool." + poolName + ".requests.granted", pool.getGrantedRequests());
        updateGauge("pool." + poolName + ".requests.rejected", pool.getRejectedRequests());
        updateGauge("pool." + poolName + ".requests.queued", pool.getQueuedRequests());
        
        updateGauge("pool." + poolName + ".success.rate", 
            (long) (pool.getSuccessRate() * 1000)); // Store as per-mille
      }
      
    } catch (Exception e) {
      LOG.warn("Error updating resource pool metrics", e);
    }
  }
  
  /**
   * Update global metrics
   */
  public void updateGlobalMetrics(ResourceUsageStats stats) {
    if (!enabled || !running) {
      return;
    }
    
    try {
      updateGauge("global.memory.allocated", stats.getTotalAllocatedMemoryMB());
      updateGauge("global.cpu.allocated", stats.getTotalAllocatedCpuCores());
      updateGauge("global.io.allocated", stats.getTotalAllocatedIoWeight());
      updateGauge("global.pools.total", stats.getTotalResourcePools());
      updateGauge("global.manager.running", stats.isResourceManagerRunning() ? 1 : 0);
      
    } catch (Exception e) {
      LOG.warn("Error updating global metrics", e);
    }
  }
  
  /**
   * Get all metrics as a map
   */
  public Map<String, Object> getAllMetrics() {
    Map<String, Object> allMetrics = new ConcurrentHashMap<>();
    
    // Add counters
    for (Map.Entry<String, AtomicLong> entry : counters.entrySet()) {
      allMetrics.put("counter." + entry.getKey(), entry.getValue().get());
    }
    
    // Add gauges
    for (Map.Entry<String, AtomicLong> entry : gauges.entrySet()) {
      allMetrics.put("gauge." + entry.getKey(), entry.getValue().get());
    }
    
    // Add histograms
    for (Map.Entry<String, HistogramMetric> entry : histograms.entrySet()) {
      String prefix = "histogram." + entry.getKey();
      HistogramMetric histogram = entry.getValue();
      
      allMetrics.put(prefix + ".count", histogram.getCount());
      allMetrics.put(prefix + ".min", histogram.getMin());
      allMetrics.put(prefix + ".max", histogram.getMax());
      allMetrics.put(prefix + ".mean", histogram.getMean());
      allMetrics.put(prefix + ".p50", histogram.getPercentile(50));
      allMetrics.put(prefix + ".p95", histogram.getPercentile(95));
      allMetrics.put(prefix + ".p99", histogram.getPercentile(99));
    }
    
    return allMetrics;
  }
  
  // Private helper methods
  
  private void initializeMetrics() {
    // Initialize common counters
    counters.put("allocations.total", new AtomicLong(0));
    counters.put("allocations.granted", new AtomicLong(0));
    counters.put("releases.total", new AtomicLong(0));
    
    // Initialize common gauges
    gauges.put("allocations.memory.total", new AtomicLong(0));
    gauges.put("allocations.cpu.total", new AtomicLong(0));
    gauges.put("allocations.io.total", new AtomicLong(0));
    
    // Initialize histograms
    histograms.put("allocation.wait.time.ms", new HistogramMetric(historySize));
    histograms.put("allocation.execution.time.ms", new HistogramMetric(historySize));
    histograms.put("allocation.total.time.ms", new HistogramMetric(historySize));
  }
  
  private void incrementCounter(String name) {
    counters.computeIfAbsent(name, k -> new AtomicLong(0)).incrementAndGet();
  }
  
  private void updateGauge(String name, long value) {
    gauges.computeIfAbsent(name, k -> new AtomicLong(0)).set(value);
  }
  
  private void decrementGauge(String name, long value) {
    gauges.computeIfAbsent(name, k -> new AtomicLong(0)).addAndGet(-value);
  }
  
  private void updateHistogram(String name, long value) {
    histograms.computeIfAbsent(name, k -> new HistogramMetric(historySize)).update(value);
  }
}