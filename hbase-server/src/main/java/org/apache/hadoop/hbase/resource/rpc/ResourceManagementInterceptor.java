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
package org.apache.hadoop.hbase.resource.rpc;

import java.util.concurrent.atomic.AtomicLong;

import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.ipc.RpcCallContext;
import org.apache.hadoop.hbase.ipc.RpcServer;
import org.apache.hadoop.hbase.resource.*;
import org.apache.hadoop.hbase.security.User;
import org.apache.hadoop.hbase.util.EnvironmentEdgeManager;
import org.apache.yetus.audience.InterfaceAudience;
import org.apache.yetus.audience.InterfaceStability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RPC interceptor that integrates hierarchical resource management into HBase RPC layer
 */
@InterfaceAudience.Private
@InterfaceStability.Unstable
public class ResourceManagementInterceptor {
  private static final Logger LOG = LoggerFactory.getLogger(ResourceManagementInterceptor.class);
  
  private final HierarchicalResourceManager resourceManager;
  private final AtomicLong requestIdGenerator = new AtomicLong(0);
  
  public ResourceManagementInterceptor(HierarchicalResourceManager resourceManager) {
    this.resourceManager = resourceManager;
  }
  
  /**
   * Intercept and manage a GET request
   */
  public ResourceAllocation interceptGet(TableName tableName, byte[] row, int numColumns) {
    return interceptRequest("GET", tableName, null, estimateGetResources(numColumns));
  }
  
  /**
   * Intercept and manage a PUT request
   */
  public ResourceAllocation interceptPut(TableName tableName, int numCells, long dataSize) {
    return interceptRequest("PUT", tableName, null, estimatePutResources(numCells, dataSize));
  }
  
  /**
   * Intercept and manage a SCAN request
   */
  public ResourceAllocation interceptScan(TableName tableName, int batchSize, long maxResultSize) {
    return interceptRequest("SCAN", tableName, null, estimateScanResources(batchSize, maxResultSize));
  }
  
  /**
   * Intercept and manage a DELETE request
   */
  public ResourceAllocation interceptDelete(TableName tableName, int numDeletes) {
    return interceptRequest("DELETE", tableName, null, estimateDeleteResources(numDeletes));
  }
  
  /**
   * Intercept and manage an ADMIN request
   */
  public ResourceAllocation interceptAdmin(String operation, TableName tableName) {
    return interceptRequest("ADMIN", tableName, operation, estimateAdminResources(operation));
  }
  
  /**
   * Generic request interception method
   */
  private ResourceAllocation interceptRequest(String operationType, TableName tableName, 
      String operation, ResourceEstimate estimate) {
    
    if (resourceManager == null || !resourceManager.isEnabled()) {
      // Return a granted allocation when resource management is disabled
      ResourceRequest dummyRequest = createDummyRequest(operationType);
      return new ResourceAllocation(dummyRequest, true, "Resource management disabled");
    }
    
    try {
      // Get current RPC context
      RpcCallContext context = RpcServer.getCurrentCall().orElse(null);
      String userId = extractUserId(context);
      String namespace = extractNamespace(tableName);
      
      // Generate unique request ID
      String requestId = generateRequestId(operationType, userId);
      
      // Determine resource pool based on user and operation
      String resourcePool = determineResourcePool(userId, operationType, namespace);
      
      // Create resource request
      ResourceRequest request = new ResourceRequest.Builder()
          .setRequestId(requestId)
          .setUserId(userId)
          .setNamespace(namespace)
          .setTableName(tableName != null ? tableName.getNameAsString() : null)
          .setResourcePool(resourcePool)
          .setResourceType(mapOperationType(operationType))
          .setPriority(determinePriority(userId, operationType))
          .setMemoryMB(estimate.getMemoryMB())
          .setCpuCores(estimate.getCpuCores())
          .setIoWeight(estimate.getIoWeight())
          .setEstimatedDurationMs(estimate.getEstimatedDurationMs())
          .build();
      
      LOG.debug("Intercepted {} request: user={}, table={}, pool={}, memory={}MB, cpu={}, io={}", 
          operationType, userId, tableName, resourcePool, 
          estimate.getMemoryMB(), estimate.getCpuCores(), estimate.getIoWeight());
      
      // Allocate resources through our hierarchical manager
      ResourceAllocation allocation = resourceManager.allocateResources(request);
      
      if (allocation.isGranted()) {
        allocation.setStartTime(EnvironmentEdgeManager.currentTime());
        LOG.debug("Resource allocation granted for request: {}", requestId);
      } else {
        LOG.info("Resource allocation denied for request: {} - {}", requestId, allocation.getReason());
      }
      
      return allocation;
      
    } catch (Exception e) {
      LOG.error("Error in resource management interception", e);
      // Fall back to allowing the request
      ResourceRequest dummyRequest = createDummyRequest(operationType);
      return new ResourceAllocation(dummyRequest, true, "Fallback due to error: " + e.getMessage());
    }
  }
  
  /**
   * Release resources after request completion
   */
  public void releaseResources(ResourceAllocation allocation, boolean success, 
      long actualMemoryUsed, Throwable error) {
    
    if (allocation == null || !allocation.isGranted() || resourceManager == null) {
      return;
    }
    
    try {
      allocation.setEndTime(EnvironmentEdgeManager.currentTime());
      
      // Update allocation with actual resource usage if available
      if (actualMemoryUsed > 0) {
        // Could create a new allocation with actual usage, but for simplicity we'll use the estimate
      }
      
      resourceManager.releaseResources(allocation);
      
      LOG.debug("Released resources for request: {} (success={}, duration={}ms)", 
          allocation.getRequest().getRequestId(), success, allocation.getExecutionTimeMs());
      
    } catch (Exception e) {
      LOG.warn("Error releasing resources for allocation: " + allocation.getRequest().getRequestId(), e);
    }
  }
  
  // Helper methods for resource estimation
  
  private ResourceEstimate estimateGetResources(int numColumns) {
    // Base resources for GET operation
    long memoryMB = Math.max(1, numColumns / 10); // ~100KB per 10 columns
    long cpuCores = 1;
    long ioWeight = 100 + numColumns * 5; // Base IO + column overhead
    long durationMs = 50 + numColumns * 2; // Estimated duration
    
    return new ResourceEstimate(memoryMB, cpuCores, ioWeight, durationMs);
  }
  
  private ResourceEstimate estimatePutResources(int numCells, long dataSize) {
    // Base resources for PUT operation
    long memoryMB = Math.max(1, dataSize / (1024 * 1024)) + 1; // Data size + overhead
    long cpuCores = 1;
    long ioWeight = 200 + numCells * 10; // Higher IO weight for writes
    long durationMs = 100 + numCells * 5; // Estimated duration
    
    return new ResourceEstimate(memoryMB, cpuCores, ioWeight, durationMs);
  }
  
  private ResourceEstimate estimateScanResources(int batchSize, long maxResultSize) {
    // Base resources for SCAN operation - typically more expensive
    long memoryMB = Math.max(2, maxResultSize / (1024 * 1024)) + 2; // Result size + overhead
    long cpuCores = 1;
    long ioWeight = 300 + batchSize * 15; // Higher IO weight for scans
    long durationMs = 500 + batchSize * 10; // Longer estimated duration
    
    return new ResourceEstimate(memoryMB, cpuCores, ioWeight, durationMs);
  }
  
  private ResourceEstimate estimateDeleteResources(int numDeletes) {
    // Base resources for DELETE operation
    long memoryMB = Math.max(1, numDeletes / 20); // Memory for delete processing
    long cpuCores = 1;
    long ioWeight = 150 + numDeletes * 8; // Moderate IO weight
    long durationMs = 75 + numDeletes * 3; // Estimated duration
    
    return new ResourceEstimate(memoryMB, cpuCores, ioWeight, durationMs);
  }
  
  private ResourceEstimate estimateAdminResources(String operation) {
    // Administrative operations typically need more resources
    long memoryMB = 5; // Base admin memory
    long cpuCores = 1;
    long ioWeight = 500; // High IO weight for admin ops
    long durationMs = 1000; // Longer duration for admin ops
    
    // Adjust based on operation type
    if (operation != null) {
      if (operation.contains("split") || operation.contains("merge")) {
        memoryMB = 10;
        ioWeight = 1000;
        durationMs = 5000;
      } else if (operation.contains("compact")) {
        memoryMB = 20;
        ioWeight = 2000;
        durationMs = 10000;
      }
    }
    
    return new ResourceEstimate(memoryMB, cpuCores, ioWeight, durationMs);
  }
  
  // Helper methods for context extraction
  
  private String extractUserId(RpcCallContext context) {
    if (context != null) {
      User user = context.getRequestUser();
      if (user != null) {
        return user.getShortName();
      }
    }
    return "unknown";
  }
  
  private String extractNamespace(TableName tableName) {
    if (tableName != null) {
      return tableName.getNamespaceAsString();
    }
    return "default";
  }
  
  private String generateRequestId(String operationType, String userId) {
    long id = requestIdGenerator.incrementAndGet();
    return String.format("%s-%s-%d-%d", operationType.toLowerCase(), userId, 
        System.currentTimeMillis() / 1000, id);
  }
  
  private String determineResourcePool(String userId, String operationType, String namespace) {
    // Simple pool assignment logic - can be made more sophisticated
    if ("admin".equals(userId) || "ADMIN".equals(operationType)) {
      return "root.system";
    }
    
    // Assign based on namespace if available
    if (namespace != null && !"default".equals(namespace)) {
      return "root.user." + namespace;
    }
    
    // Default to normal user pool
    return "root.user.normal";
  }
  
  private ResourceRequest.ResourceType mapOperationType(String operationType) {
    switch (operationType.toUpperCase()) {
      case "GET": return ResourceRequest.ResourceType.GET;
      case "PUT": return ResourceRequest.ResourceType.PUT;
      case "SCAN": return ResourceRequest.ResourceType.SCAN;
      case "DELETE": return ResourceRequest.ResourceType.DELETE;
      case "ADMIN": return ResourceRequest.ResourceType.ADMIN;
      default: return ResourceRequest.ResourceType.GET;
    }
  }
  
  private ResourceRequest.Priority determinePriority(String userId, String operationType) {
    // Simple priority assignment - can be made configurable
    if ("admin".equals(userId)) {
      return ResourceRequest.Priority.HIGH;
    }
    
    if ("ADMIN".equals(operationType)) {
      return ResourceRequest.Priority.HIGH;
    }
    
    if ("SCAN".equals(operationType)) {
      return ResourceRequest.Priority.LOW; // Scans typically lower priority
    }
    
    return ResourceRequest.Priority.NORMAL;
  }
  
  private ResourceRequest createDummyRequest(String operationType) {
    return new ResourceRequest.Builder()
        .setRequestId("dummy-" + operationType.toLowerCase())
        .setUserId("system")
        .setResourceType(mapOperationType(operationType))
        .setPriority(ResourceRequest.Priority.NORMAL)
        .setMemoryMB(1)
        .setCpuCores(1)
        .setIoWeight(100)
        .setEstimatedDurationMs(100)
        .build();
  }
  
  /**
   * Simple class to hold resource estimates
   */
  public static class ResourceEstimate {
    private final long memoryMB;
    private final long cpuCores;
    private final long ioWeight;
    private final long estimatedDurationMs;
    
    public ResourceEstimate(long memoryMB, long cpuCores, long ioWeight, long estimatedDurationMs) {
      this.memoryMB = memoryMB;
      this.cpuCores = cpuCores;
      this.ioWeight = ioWeight;
      this.estimatedDurationMs = estimatedDurationMs;
    }
    
    public long getMemoryMB() { return memoryMB; }
    public long getCpuCores() { return cpuCores; }
    public long getIoWeight() { return ioWeight; }
    public long getEstimatedDurationMs() { return estimatedDurationMs; }
  }
}