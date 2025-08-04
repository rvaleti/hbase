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
import org.apache.hadoop.hbase.resource.ResourceManagerService;
import org.apache.yetus.audience.InterfaceAudience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enhanced HRegionServer with integrated hierarchical resource management
 */
@InterfaceAudience.Private
public class HRegionServerWithResourceManagement extends HRegionServer {
  private static final Logger LOG = LoggerFactory.getLogger(HRegionServerWithResourceManagement.class);
  
  private ResourceManagerService resourceManagerService;
  private RSRpcServicesWithResourceManagement enhancedRpcServices;
  
  public HRegionServerWithResourceManagement(Configuration conf) throws IOException {
    super(conf);
  }
  
  @Override
  protected RSRpcServices createRpcServices() throws IOException {
    // Create our enhanced RPC services with resource management
    this.enhancedRpcServices = new RSRpcServicesWithResourceManagement(this);
    LOG.info("Created RSRpcServicesWithResourceManagement for hierarchical resource management");
    return this.enhancedRpcServices;
  }
  
  @Override
  protected void startServiceThreads() throws IOException {
    super.startServiceThreads();
    
    // Start the resource manager service for web UI and API
    if (conf.getBoolean("hbase.resource.hierarchical.enabled", true)) {
      try {
        // Get the resource manager from our enhanced RPC services
        if (enhancedRpcServices != null && enhancedRpcServices.getResourceManager() != null) {
          this.resourceManagerService = new ResourceManagerService(
              enhancedRpcServices.getResourceManager(), conf);
          this.resourceManagerService.start();
          LOG.info("Started ResourceManagerService for web UI and REST API");
        } else {
          LOG.warn("Could not start ResourceManagerService - enhanced RPC services not available");
        }
      } catch (Exception e) {
        LOG.error("Failed to start ResourceManagerService", e);
      }
    }
  }
  
  @Override
  protected void stopServiceThreads() {
    // Stop resource manager service first
    if (resourceManagerService != null) {
      try {
        resourceManagerService.stop();
        LOG.info("Stopped ResourceManagerService");
      } catch (Exception e) {
        LOG.warn("Error stopping ResourceManagerService", e);
      }
    }
    
    super.stopServiceThreads();
  }
  
  /**
   * Get the resource manager service for monitoring and management
   */
  public ResourceManagerService getResourceManagerService() {
    return resourceManagerService;
  }
  
  /**
   * Get resource management statistics for JMX/monitoring
   */
  public String getResourceManagementStats() {
    if (enhancedRpcServices != null) {
      return enhancedRpcServices.getResourceManagementStats();
    }
    return "Resource management not available";
  }
  
  /**
   * Main method to run HRegionServer with resource management
   */
  public static void main(String[] args) throws Exception {
    LOG.info("Starting HRegionServer with hierarchical resource management");
    doMain(args, HRegionServerWithResourceManagement.class);
  }
}