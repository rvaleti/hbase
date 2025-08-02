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

import org.apache.hadoop.hbase.resource.scheduler.SchedulerPolicy;
import org.apache.yetus.audience.InterfaceAudience;
import org.apache.yetus.audience.InterfaceStability;

/**
 * Configuration for the hierarchical scheduler
 */
@InterfaceAudience.Public
@InterfaceStability.Unstable
public class SchedulerConfig {
  
  private SchedulerPolicy schedulerPolicy;
  private Boolean preemptionEnabled;
  private Long preemptionTimeoutMs;
  private Long maxAllocationWaitMs;
  private Integer maxQueueSize;
  private Double fairSharePreemptionThreshold;
  private Long rebalanceIntervalMs;
  
  public SchedulerConfig() {
  }
  
  public SchedulerPolicy getSchedulerPolicy() {
    return schedulerPolicy;
  }
  
  public void setSchedulerPolicy(SchedulerPolicy schedulerPolicy) {
    this.schedulerPolicy = schedulerPolicy;
  }
  
  public Boolean getPreemptionEnabled() {
    return preemptionEnabled;
  }
  
  public void setPreemptionEnabled(Boolean preemptionEnabled) {
    this.preemptionEnabled = preemptionEnabled;
  }
  
  public Long getPreemptionTimeoutMs() {
    return preemptionTimeoutMs;
  }
  
  public void setPreemptionTimeoutMs(Long preemptionTimeoutMs) {
    this.preemptionTimeoutMs = preemptionTimeoutMs;
  }
  
  public Long getMaxAllocationWaitMs() {
    return maxAllocationWaitMs;
  }
  
  public void setMaxAllocationWaitMs(Long maxAllocationWaitMs) {
    this.maxAllocationWaitMs = maxAllocationWaitMs;
  }
  
  public Integer getMaxQueueSize() {
    return maxQueueSize;
  }
  
  public void setMaxQueueSize(Integer maxQueueSize) {
    this.maxQueueSize = maxQueueSize;
  }
  
  public Double getFairSharePreemptionThreshold() {
    return fairSharePreemptionThreshold;
  }
  
  public void setFairSharePreemptionThreshold(Double fairSharePreemptionThreshold) {
    this.fairSharePreemptionThreshold = fairSharePreemptionThreshold;
  }
  
  public Long getRebalanceIntervalMs() {
    return rebalanceIntervalMs;
  }
  
  public void setRebalanceIntervalMs(Long rebalanceIntervalMs) {
    this.rebalanceIntervalMs = rebalanceIntervalMs;
  }
  
  @Override
  public String toString() {
    return "SchedulerConfig{" +
        "schedulerPolicy=" + schedulerPolicy +
        ", preemptionEnabled=" + preemptionEnabled +
        ", preemptionTimeoutMs=" + preemptionTimeoutMs +
        ", maxAllocationWaitMs=" + maxAllocationWaitMs +
        ", maxQueueSize=" + maxQueueSize +
        ", fairSharePreemptionThreshold=" + fairSharePreemptionThreshold +
        ", rebalanceIntervalMs=" + rebalanceIntervalMs +
        '}';
  }
}