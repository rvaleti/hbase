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

import java.util.Map;
import org.apache.yetus.audience.InterfaceAudience;
import org.apache.yetus.audience.InterfaceStability;

/**
 * Statistics for the hierarchical scheduler
 */
@InterfaceAudience.Public
@InterfaceStability.Unstable
public class SchedulerStats {
  
  private final SchedulerPolicy policy;
  private final long totalScheduledRequests;
  private final long totalGrantedRequests;
  private final long totalRejectedRequests;
  private final long totalPreemptedRequests;
  private final int activeAllocations;
  private final Map<String, PoolStats> poolStats;
  private final long timestamp;
  
  public SchedulerStats(SchedulerPolicy policy, long totalScheduledRequests, 
      long totalGrantedRequests, long totalRejectedRequests, long totalPreemptedRequests,
      int activeAllocations, Map<String, PoolStats> poolStats) {
    this.policy = policy;
    this.totalScheduledRequests = totalScheduledRequests;
    this.totalGrantedRequests = totalGrantedRequests;
    this.totalRejectedRequests = totalRejectedRequests;
    this.totalPreemptedRequests = totalPreemptedRequests;
    this.activeAllocations = activeAllocations;
    this.poolStats = poolStats;
    this.timestamp = System.currentTimeMillis();
  }
  
  public SchedulerPolicy getPolicy() {
    return policy;
  }
  
  public long getTotalScheduledRequests() {
    return totalScheduledRequests;
  }
  
  public long getTotalGrantedRequests() {
    return totalGrantedRequests;
  }
  
  public long getTotalRejectedRequests() {
    return totalRejectedRequests;
  }
  
  public long getTotalPreemptedRequests() {
    return totalPreemptedRequests;
  }
  
  public int getActiveAllocations() {
    return activeAllocations;
  }
  
  public Map<String, PoolStats> getPoolStats() {
    return poolStats;
  }
  
  public long getTimestamp() {
    return timestamp;
  }
  
  public double getSuccessRate() {
    return totalScheduledRequests == 0 ? 1.0 : 
        (double) totalGrantedRequests / totalScheduledRequests;
  }
  
  public double getPreemptionRate() {
    return totalGrantedRequests == 0 ? 0.0 : 
        (double) totalPreemptedRequests / totalGrantedRequests;
  }
  
  @Override
  public String toString() {
    return "SchedulerStats{" +
        "policy=" + policy +
        ", totalScheduledRequests=" + totalScheduledRequests +
        ", totalGrantedRequests=" + totalGrantedRequests +
        ", totalRejectedRequests=" + totalRejectedRequests +
        ", totalPreemptedRequests=" + totalPreemptedRequests +
        ", activeAllocations=" + activeAllocations +
        ", successRate=" + String.format("%.2f%%", getSuccessRate() * 100) +
        ", preemptionRate=" + String.format("%.2f%%", getPreemptionRate() * 100) +
        ", timestamp=" + timestamp +
        '}';
  }
}