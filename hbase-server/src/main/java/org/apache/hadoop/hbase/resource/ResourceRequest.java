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
 * Represents a resource allocation request in the hierarchical resource management system
 */
@InterfaceAudience.Public
@InterfaceStability.Unstable
public class ResourceRequest {
  
  public enum Priority {
    CRITICAL(4),
    HIGH(3),
    NORMAL(2),
    LOW(1);
    
    private final int value;
    
    Priority(int value) {
      this.value = value;
    }
    
    public int getValue() {
      return value;
    }
  }
  
  public enum ResourceType {
    SCAN,
    PUT,
    GET,
    DELETE,
    COMPACTION,
    FLUSH,
    SPLIT,
    ADMIN,
    REPLICATION
  }
  
  private final String requestId;
  private final String userId;
  private final String namespace;
  private final String tableName;
  private final String resourcePool;
  private final ResourceType resourceType;
  private final Priority priority;
  private final long memoryMB;
  private final long cpuCores;
  private final long ioWeight;
  private final long estimatedDurationMs;
  private final long submissionTime;
  
  public ResourceRequest(String requestId, String userId, String namespace, String tableName,
      String resourcePool, ResourceType resourceType, Priority priority, 
      long memoryMB, long cpuCores, long ioWeight, long estimatedDurationMs) {
    this.requestId = Objects.requireNonNull(requestId, "Request ID cannot be null");
    this.userId = Objects.requireNonNull(userId, "User ID cannot be null");
    this.namespace = namespace;
    this.tableName = tableName;
    this.resourcePool = resourcePool != null ? resourcePool : "root.user.normal";
    this.resourceType = Objects.requireNonNull(resourceType, "Resource type cannot be null");
    this.priority = Objects.requireNonNull(priority, "Priority cannot be null");
    this.memoryMB = Math.max(0, memoryMB);
    this.cpuCores = Math.max(0, cpuCores);
    this.ioWeight = Math.max(0, ioWeight);
    this.estimatedDurationMs = Math.max(0, estimatedDurationMs);
    this.submissionTime = System.currentTimeMillis();
  }
  
  public String getRequestId() {
    return requestId;
  }
  
  public String getUserId() {
    return userId;
  }
  
  public String getNamespace() {
    return namespace;
  }
  
  public String getTableName() {
    return tableName;
  }
  
  public String getResourcePool() {
    return resourcePool;
  }
  
  public ResourceType getResourceType() {
    return resourceType;
  }
  
  public Priority getPriority() {
    return priority;
  }
  
  public long getMemoryMB() {
    return memoryMB;
  }
  
  public long getCpuCores() {
    return cpuCores;
  }
  
  public long getIoWeight() {
    return ioWeight;
  }
  
  public long getEstimatedDurationMs() {
    return estimatedDurationMs;
  }
  
  public long getSubmissionTime() {
    return submissionTime;
  }
  
  public long getWaitTime() {
    return System.currentTimeMillis() - submissionTime;
  }
  
  /**
   * Calculate resource score for scheduling priority
   */
  public double getResourceScore() {
    // Higher score = higher priority
    double score = priority.getValue() * 1000.0;
    
    // Favor requests that have been waiting longer
    score += Math.min(getWaitTime() / 1000.0, 100.0);
    
    // Favor smaller requests for better throughput
    double resourceSize = (memoryMB / 1024.0) + cpuCores + (ioWeight / 100.0);
    score += Math.max(0, 50.0 - resourceSize);
    
    return score;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ResourceRequest that = (ResourceRequest) o;
    return Objects.equals(requestId, that.requestId);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(requestId);
  }
  
  @Override
  public String toString() {
    return "ResourceRequest{" +
        "requestId='" + requestId + '\'' +
        ", userId='" + userId + '\'' +
        ", namespace='" + namespace + '\'' +
        ", tableName='" + tableName + '\'' +
        ", resourcePool='" + resourcePool + '\'' +
        ", resourceType=" + resourceType +
        ", priority=" + priority +
        ", memoryMB=" + memoryMB +
        ", cpuCores=" + cpuCores +
        ", ioWeight=" + ioWeight +
        ", estimatedDurationMs=" + estimatedDurationMs +
        ", submissionTime=" + submissionTime +
        '}';
  }
  
  /**
   * Builder for creating ResourceRequest instances
   */
  public static class Builder {
    private String requestId;
    private String userId;
    private String namespace;
    private String tableName;
    private String resourcePool;
    private ResourceType resourceType = ResourceType.GET;
    private Priority priority = Priority.NORMAL;
    private long memoryMB = 0;
    private long cpuCores = 1;
    private long ioWeight = 100;
    private long estimatedDurationMs = 1000;
    
    public Builder setRequestId(String requestId) {
      this.requestId = requestId;
      return this;
    }
    
    public Builder setUserId(String userId) {
      this.userId = userId;
      return this;
    }
    
    public Builder setNamespace(String namespace) {
      this.namespace = namespace;
      return this;
    }
    
    public Builder setTableName(String tableName) {
      this.tableName = tableName;
      return this;
    }
    
    public Builder setResourcePool(String resourcePool) {
      this.resourcePool = resourcePool;
      return this;
    }
    
    public Builder setResourceType(ResourceType resourceType) {
      this.resourceType = resourceType;
      return this;
    }
    
    public Builder setPriority(Priority priority) {
      this.priority = priority;
      return this;
    }
    
    public Builder setMemoryMB(long memoryMB) {
      this.memoryMB = memoryMB;
      return this;
    }
    
    public Builder setCpuCores(long cpuCores) {
      this.cpuCores = cpuCores;
      return this;
    }
    
    public Builder setIoWeight(long ioWeight) {
      this.ioWeight = ioWeight;
      return this;
    }
    
    public Builder setEstimatedDurationMs(long estimatedDurationMs) {
      this.estimatedDurationMs = estimatedDurationMs;
      return this;
    }
    
    public ResourceRequest build() {
      return new ResourceRequest(requestId, userId, namespace, tableName, resourcePool,
          resourceType, priority, memoryMB, cpuCores, ioWeight, estimatedDurationMs);
    }
  }
}