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
package org.apache.hadoop.hbase.regionserver;

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.exceptions.RequestTooBigException;
import org.apache.hadoop.hbase.ipc.RpcCallContext;
import org.apache.hadoop.hbase.ipc.RpcServer;
import org.apache.hadoop.hbase.resource.HierarchicalResourceManager;
import org.apache.hadoop.hbase.resource.ResourceAllocation;
import org.apache.hadoop.hbase.resource.ResourceManagerService;
import org.apache.hadoop.hbase.resource.rpc.ResourceManagementInterceptor;
import org.apache.hadoop.hbase.shaded.protobuf.generated.ClientProtos.*;
import org.apache.hadoop.hbase.util.EnvironmentEdgeManager;
import org.apache.yetus.audience.InterfaceAudience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.RpcController;
import com.google.protobuf.ServiceException;

/**
 * Enhanced RSRpcServices with integrated hierarchical resource management
 */
@InterfaceAudience.Private
public class RSRpcServicesWithResourceManagement extends RSRpcServices {
  private static final Logger LOG = LoggerFactory.getLogger(RSRpcServicesWithResourceManagement.class);
  
  private final ResourceManagementInterceptor resourceInterceptor;
  private final HierarchicalResourceManager resourceManager;
  private final boolean resourceManagementEnabled;
  
  public RSRpcServicesWithResourceManagement(final HRegionServer rs) throws IOException {
    super(rs);
    
    // Initialize resource management
    Configuration conf = rs.getConfiguration();
    this.resourceManagementEnabled = conf.getBoolean("hbase.resource.hierarchical.enabled", true);
    
    if (resourceManagementEnabled) {
      // Get the resource manager from the region server
      this.resourceManager = getResourceManagerFromServer(rs);
      this.resourceInterceptor = new ResourceManagementInterceptor(resourceManager);
      LOG.info("Hierarchical resource management enabled in RSRpcServices");
    } else {
      this.resourceManager = null;
      this.resourceInterceptor = null;
      LOG.info("Hierarchical resource management disabled in RSRpcServices");
    }
  }
  
  @Override
  public GetResponse get(final RpcController controller, final GetRequest request)
      throws ServiceException {
    
    ResourceAllocation allocation = null;
    long startTime = EnvironmentEdgeManager.currentTime();
    boolean success = false;
    Throwable error = null;
    
    try {
      // Intercept and allocate resources for this GET request
      if (resourceInterceptor != null) {
        TableName tableName = getTableNameFromRequest(request);
        int numColumns = request.getGet().getColumnCount();
        allocation = resourceInterceptor.interceptGet(tableName, null, numColumns);
        
        // Check if the request was denied due to resource constraints
        if (!allocation.isGranted()) {
          throw new RequestTooBigException("Resource allocation denied: " + allocation.getReason());
        }
      }
      
      // Execute the original GET request
      GetResponse response = super.get(controller, request);
      success = true;
      return response;
      
    } catch (Exception e) {
      error = e;
      throw e;
    } finally {
      // Release allocated resources
      if (resourceInterceptor != null && allocation != null) {
        long executionTime = EnvironmentEdgeManager.currentTime() - startTime;
        resourceInterceptor.releaseResources(allocation, success, 0, error);
        
        LOG.debug("GET request completed: success={}, duration={}ms, allocation={}", 
            success, executionTime, allocation.getRequest().getRequestId());
      }
    }
  }
  
  @Override
  public MutateResponse mutate(final RpcController controller, final MutateRequest request)
      throws ServiceException {
    
    ResourceAllocation allocation = null;
    long startTime = EnvironmentEdgeManager.currentTime();
    boolean success = false;
    Throwable error = null;
    
    try {
      // Intercept and allocate resources for this PUT/DELETE request
      if (resourceInterceptor != null) {
        TableName tableName = getTableNameFromMutateRequest(request);
        
        if (request.getMutation().getMutateType() == MutationProto.MutationType.PUT) {
          int numCells = request.getMutation().getColumnValueCount();
          long dataSize = estimateDataSize(request.getMutation());
          allocation = resourceInterceptor.interceptPut(tableName, numCells, dataSize);
        } else if (request.getMutation().getMutateType() == MutationProto.MutationType.DELETE) {
          int numDeletes = request.getMutation().getColumnValueCount();
          allocation = resourceInterceptor.interceptDelete(tableName, numDeletes);
        } else {
          // Default allocation for other mutation types
          allocation = resourceInterceptor.interceptPut(tableName, 1, 1024);
        }
        
        // Check if the request was denied due to resource constraints
        if (!allocation.isGranted()) {
          throw new RequestTooBigException("Resource allocation denied: " + allocation.getReason());
        }
      }
      
      // Execute the original MUTATE request
      MutateResponse response = super.mutate(controller, request);
      success = true;
      return response;
      
    } catch (Exception e) {
      error = e;
      throw e;
    } finally {
      // Release allocated resources
      if (resourceInterceptor != null && allocation != null) {
        long executionTime = EnvironmentEdgeManager.currentTime() - startTime;
        long estimatedMemory = allocation.getRequest().getMemoryMB();
        resourceInterceptor.releaseResources(allocation, success, estimatedMemory, error);
        
        LOG.debug("MUTATE request completed: success={}, duration={}ms, allocation={}", 
            success, executionTime, allocation.getRequest().getRequestId());
      }
    }
  }
  
  @Override
  public ScanResponse scan(final RpcController controller, final ScanRequest request)
      throws ServiceException {
    
    ResourceAllocation allocation = null;
    long startTime = EnvironmentEdgeManager.currentTime();
    boolean success = false;
    Throwable error = null;
    
    try {
      // Intercept and allocate resources for this SCAN request
      if (resourceInterceptor != null) {
        TableName tableName = getTableNameFromScanRequest(request);
        int batchSize = request.hasScan() && request.getScan().hasBatchSize() ? 
            request.getScan().getBatchSize() : 100;
        long maxResultSize = request.hasScan() && request.getScan().hasMaxResultSize() ? 
            request.getScan().getMaxResultSize() : 2 * 1024 * 1024; // 2MB default
            
        allocation = resourceInterceptor.interceptScan(tableName, batchSize, maxResultSize);
        
        // Check if the request was denied due to resource constraints
        if (!allocation.isGranted()) {
          throw new RequestTooBigException("Resource allocation denied: " + allocation.getReason());
        }
      }
      
      // Execute the original SCAN request
      ScanResponse response = super.scan(controller, request);
      success = true;
      return response;
      
    } catch (Exception e) {
      error = e;
      throw e;
    } finally {
      // Release allocated resources
      if (resourceInterceptor != null && allocation != null) {
        long executionTime = EnvironmentEdgeManager.currentTime() - startTime;
        long estimatedMemory = allocation.getRequest().getMemoryMB();
        resourceInterceptor.releaseResources(allocation, success, estimatedMemory, error);
        
        LOG.debug("SCAN request completed: success={}, duration={}ms, allocation={}", 
            success, executionTime, allocation.getRequest().getRequestId());
      }
    }
  }
  
  @Override
  public MultiResponse multi(final RpcController controller, final MultiRequest request)
      throws ServiceException {
    
    ResourceAllocation allocation = null;
    long startTime = EnvironmentEdgeManager.currentTime();
    boolean success = false;
    Throwable error = null;
    
    try {
      // Intercept and allocate resources for this MULTI request
      if (resourceInterceptor != null) {
        // Estimate resources based on the number of actions in the multi request
        int totalActions = 0;
        long totalDataSize = 0;
        
        for (RegionAction regionAction : request.getRegionActionList()) {
          totalActions += regionAction.getActionCount();
          for (Action action : regionAction.getActionList()) {
            if (action.hasMutation()) {
              totalDataSize += estimateDataSize(action.getMutation());
            }
          }
        }
        
        // Use PUT estimation for multi requests (conservative approach)
        TableName tableName = getTableNameFromMultiRequest(request);
        allocation = resourceInterceptor.interceptPut(tableName, totalActions, totalDataSize);
        
        // Check if the request was denied due to resource constraints
        if (!allocation.isGranted()) {
          throw new RequestTooBigException("Resource allocation denied: " + allocation.getReason());
        }
      }
      
      // Execute the original MULTI request
      MultiResponse response = super.multi(controller, request);
      success = true;
      return response;
      
    } catch (Exception e) {
      error = e;
      throw e;
    } finally {
      // Release allocated resources
      if (resourceInterceptor != null && allocation != null) {
        long executionTime = EnvironmentEdgeManager.currentTime() - startTime;
        long estimatedMemory = allocation.getRequest().getMemoryMB();
        resourceInterceptor.releaseResources(allocation, success, estimatedMemory, error);
        
        LOG.debug("MULTI request completed: success={}, duration={}ms, allocation={}", 
            success, executionTime, allocation.getRequest().getRequestId());
      }
    }
  }
  
  // Helper methods for extracting information from requests
  
  private TableName getTableNameFromRequest(GetRequest request) {
    try {
      return TableName.valueOf(request.getRegion().getValue().toByteArray());
    } catch (Exception e) {
      return null;
    }
  }
  
  private TableName getTableNameFromMutateRequest(MutateRequest request) {
    try {
      return TableName.valueOf(request.getRegion().getValue().toByteArray());
    } catch (Exception e) {
      return null;
    }
  }
  
  private TableName getTableNameFromScanRequest(ScanRequest request) {
    try {
      return TableName.valueOf(request.getRegion().getValue().toByteArray());
    } catch (Exception e) {
      return null;
    }
  }
  
  private TableName getTableNameFromMultiRequest(MultiRequest request) {
    try {
      if (!request.getRegionActionList().isEmpty()) {
        RegionAction firstAction = request.getRegionActionList().get(0);
        return TableName.valueOf(firstAction.getRegion().getValue().toByteArray());
      }
    } catch (Exception e) {
      // Fall through
    }
    return null;
  }
  
  private long estimateDataSize(MutationProto mutation) {
    long size = 0;
    for (ColumnValue cv : mutation.getColumnValueList()) {
      for (QualifierValue qv : cv.getQualifierValueList()) {
        size += qv.getValue().size();
        size += qv.getQualifier().size();
      }
      size += cv.getFamily().size();
    }
    return size;
  }
  
  private HierarchicalResourceManager getResourceManagerFromServer(HRegionServer rs) {
    // In a real implementation, the resource manager would be initialized in the RegionServer
    // For this demo, we'll create a new instance
    try {
      Configuration conf = rs.getConfiguration();
      HierarchicalResourceManager manager = new HierarchicalResourceManager(conf);
      manager.start();
      return manager;
    } catch (Exception e) {
      LOG.error("Failed to initialize resource manager", e);
      return null;
    }
  }
  
  /**
   * Get the resource manager instance
   */
  public HierarchicalResourceManager getResourceManager() {
    return resourceManager;
  }
  
  /**
   * Get resource management statistics for monitoring
   */
  public String getResourceManagementStats() {
    if (resourceManager != null && resourceManager.isRunning()) {
      return resourceManager.getResourceUsageStats().toString();
    }
    return "Resource management not available";
  }
}