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

import java.util.Objects;
import org.apache.yetus.audience.InterfaceAudience;
import org.apache.yetus.audience.InterfaceStability;

/**
 * Represents an allocated resource in the hierarchical resource management system
 */
@InterfaceAudience.Public
@InterfaceStability.Unstable
public class ResourceAllocation {
  
  private final ResourceRequest request;
  private final boolean granted;
  private final String reason;
  private final long allocationTime;
  private final String allocatedPool;
  private final long actualMemoryMB;
  private final long actualCpuCores;
  private final long actualIoWeight;
  private volatile long startTime = -1;
  private volatile long endTime = -1;
  
  public ResourceAllocation(ResourceRequest request, boolean granted, String reason) {
    this(request, granted, reason, request.getResourcePool(),
         request.getMemoryMB(), request.getCpuCores(), request.getIoWeight());
  }
  
  public ResourceAllocation(ResourceRequest request, boolean granted, String reason,
      String allocatedPool, long actualMemoryMB, long actualCpuCores, long actualIoWeight) {
    this.request = Objects.requireNonNull(request, "Request cannot be null");
    this.granted = granted;
    this.reason = reason;
    this.allocationTime = System.currentTimeMillis();
    this.allocatedPool = allocatedPool;
    this.actualMemoryMB = actualMemoryMB;
    this.actualCpuCores = actualCpuCores;
    this.actualIoWeight = actualIoWeight;
  }
  
  public ResourceRequest getRequest() {
    return request;
  }
  
  public boolean isGranted() {
    return granted;
  }
  
  public String getReason() {
    return reason;
  }
  
  public long getAllocationTime() {
    return allocationTime;
  }
  
  public String getAllocatedPool() {
    return allocatedPool;
  }
  
  public long getActualMemoryMB() {
    return actualMemoryMB;
  }
  
  public long getActualCpuCores() {
    return actualCpuCores;
  }
  
  public long getActualIoWeight() {
    return actualIoWeight;
  }
  
  public long getStartTime() {
    return startTime;
  }
  
  public void setStartTime(long startTime) {
    this.startTime = startTime;
  }
  
  public long getEndTime() {
    return endTime;
  }
  
  public void setEndTime(long endTime) {
    this.endTime = endTime;
  }
  
  public long getWaitTimeMs() {
    return allocationTime - request.getSubmissionTime();
  }
  
  public long getExecutionTimeMs() {
    if (startTime == -1) {
      return -1;
    }
    if (endTime == -1) {
      return System.currentTimeMillis() - startTime;
    }
    return endTime - startTime;
  }
  
  public long getTotalTimeMs() {
    if (endTime == -1) {
      return System.currentTimeMillis() - request.getSubmissionTime();
    }
    return endTime - request.getSubmissionTime();
  }
  
  public boolean isActive() {
    return granted && startTime != -1 && endTime == -1;
  }
  
  public boolean isCompleted() {
    return endTime != -1;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ResourceAllocation that = (ResourceAllocation) o;
    return Objects.equals(request.getRequestId(), that.request.getRequestId());
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(request.getRequestId());
  }
  
  @Override
  public String toString() {
    return "ResourceAllocation{" +
        "requestId=" + request.getRequestId() +
        ", granted=" + granted +
        ", reason='" + reason + '\'' +
        ", allocationTime=" + allocationTime +
        ", allocatedPool='" + allocatedPool + '\'' +
        ", actualMemoryMB=" + actualMemoryMB +
        ", actualCpuCores=" + actualCpuCores +
        ", actualIoWeight=" + actualIoWeight +
        ", startTime=" + startTime +
        ", endTime=" + endTime +
        ", waitTimeMs=" + getWaitTimeMs() +
        ", executionTimeMs=" + getExecutionTimeMs() +
        '}';
  }
}