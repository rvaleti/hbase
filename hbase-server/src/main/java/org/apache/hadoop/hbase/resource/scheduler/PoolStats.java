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

import org.apache.yetus.audience.InterfaceAudience;
import org.apache.yetus.audience.InterfaceStability;

/**
 * Statistics for an individual resource pool
 */
@InterfaceAudience.Public
@InterfaceStability.Unstable
public class PoolStats {
  
  private final long totalRequests;
  private final long grantedRequests;
  private final long rejectedRequests;
  private final int queuedRequests;
  private final long allocatedMemoryMB;
  private final long allocatedCpuCores;
  private final long allocatedIoWeight;
  private final double fairShareMemory;
  private final double fairShareCpu;
  private final double fairShareIo;
  
  public PoolStats(long totalRequests, long grantedRequests, long rejectedRequests,
      int queuedRequests, long allocatedMemoryMB, long allocatedCpuCores,
      long allocatedIoWeight, double fairShareMemory, double fairShareCpu,
      double fairShareIo) {
    this.totalRequests = totalRequests;
    this.grantedRequests = grantedRequests;
    this.rejectedRequests = rejectedRequests;
    this.queuedRequests = queuedRequests;
    this.allocatedMemoryMB = allocatedMemoryMB;
    this.allocatedCpuCores = allocatedCpuCores;
    this.allocatedIoWeight = allocatedIoWeight;
    this.fairShareMemory = fairShareMemory;
    this.fairShareCpu = fairShareCpu;
    this.fairShareIo = fairShareIo;
  }
  
  public long getTotalRequests() {
    return totalRequests;
  }
  
  public long getGrantedRequests() {
    return grantedRequests;
  }
  
  public long getRejectedRequests() {
    return rejectedRequests;
  }
  
  public int getQueuedRequests() {
    return queuedRequests;
  }
  
  public long getAllocatedMemoryMB() {
    return allocatedMemoryMB;
  }
  
  public long getAllocatedCpuCores() {
    return allocatedCpuCores;
  }
  
  public long getAllocatedIoWeight() {
    return allocatedIoWeight;
  }
  
  public double getFairShareMemory() {
    return fairShareMemory;
  }
  
  public double getFairShareCpu() {
    return fairShareCpu;
  }
  
  public double getFairShareIo() {
    return fairShareIo;
  }
  
  public double getSuccessRate() {
    return totalRequests == 0 ? 1.0 : (double) grantedRequests / totalRequests;
  }
  
  public double getMemoryUtilizationVsFairShare() {
    return fairShareMemory == 0 ? 0.0 : allocatedMemoryMB / fairShareMemory;
  }
  
  public double getCpuUtilizationVsFairShare() {
    return fairShareCpu == 0 ? 0.0 : allocatedCpuCores / fairShareCpu;
  }
  
  public double getIoUtilizationVsFairShare() {
    return fairShareIo == 0 ? 0.0 : allocatedIoWeight / fairShareIo;
  }
  
  @Override
  public String toString() {
    return "PoolStats{" +
        "totalRequests=" + totalRequests +
        ", grantedRequests=" + grantedRequests +
        ", rejectedRequests=" + rejectedRequests +
        ", queuedRequests=" + queuedRequests +
        ", allocatedMemoryMB=" + allocatedMemoryMB +
        ", allocatedCpuCores=" + allocatedCpuCores +
        ", allocatedIoWeight=" + allocatedIoWeight +
        ", fairShareMemory=" + fairShareMemory +
        ", fairShareCpu=" + fairShareCpu +
        ", fairShareIo=" + fairShareIo +
        ", successRate=" + String.format("%.2f%%", getSuccessRate() * 100) +
        '}';
  }
}