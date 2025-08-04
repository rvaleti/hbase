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

import org.apache.yetus.audience.InterfaceAudience;
import org.apache.yetus.audience.InterfaceStability;

/**
 * Resource usage statistics for the hierarchical resource management system
 */
@InterfaceAudience.Public
@InterfaceStability.Unstable
public class ResourceUsageStats {
  
  private final long totalAllocatedMemoryMB;
  private final long totalAllocatedCpuCores;
  private final long totalAllocatedIoWeight;
  private final int totalResourcePools;
  private final boolean resourceManagerRunning;
  private final long timestamp;
  
  public ResourceUsageStats(long totalAllocatedMemoryMB, long totalAllocatedCpuCores,
      long totalAllocatedIoWeight, int totalResourcePools, boolean resourceManagerRunning) {
    this.totalAllocatedMemoryMB = totalAllocatedMemoryMB;
    this.totalAllocatedCpuCores = totalAllocatedCpuCores;
    this.totalAllocatedIoWeight = totalAllocatedIoWeight;
    this.totalResourcePools = totalResourcePools;
    this.resourceManagerRunning = resourceManagerRunning;
    this.timestamp = System.currentTimeMillis();
  }
  
  public long getTotalAllocatedMemoryMB() {
    return totalAllocatedMemoryMB;
  }
  
  public long getTotalAllocatedCpuCores() {
    return totalAllocatedCpuCores;
  }
  
  public long getTotalAllocatedIoWeight() {
    return totalAllocatedIoWeight;
  }
  
  public int getTotalResourcePools() {
    return totalResourcePools;
  }
  
  public boolean isResourceManagerRunning() {
    return resourceManagerRunning;
  }
  
  public long getTimestamp() {
    return timestamp;
  }
  
  @Override
  public String toString() {
    return "ResourceUsageStats{" +
        "totalAllocatedMemoryMB=" + totalAllocatedMemoryMB +
        ", totalAllocatedCpuCores=" + totalAllocatedCpuCores +
        ", totalAllocatedIoWeight=" + totalAllocatedIoWeight +
        ", totalResourcePools=" + totalResourcePools +
        ", resourceManagerRunning=" + resourceManagerRunning +
        ", timestamp=" + timestamp +
        '}';
  }
}