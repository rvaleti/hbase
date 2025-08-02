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

import java.io.IOException;
import javax.servlet.ServletException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.http.InfoServer;
import org.apache.hadoop.hbase.resource.http.ResourceManagementController;
import org.apache.hadoop.hbase.util.Threads;
import org.apache.yetus.audience.InterfaceAudience;
import org.apache.yetus.audience.InterfaceStability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service that manages the hierarchical resource manager lifecycle and web interface
 */
@InterfaceAudience.Private
@InterfaceStability.Unstable
public class ResourceManagerService {
  private static final Logger LOG = LoggerFactory.getLogger(ResourceManagerService.class);
  
  // Configuration keys
  public static final String RESOURCE_MANAGER_HTTP_PORT = "hbase.resource.manager.http.port";
  public static final String RESOURCE_MANAGER_HTTP_ADDRESS = "hbase.resource.manager.http.address";
  public static final String RESOURCE_MANAGER_WEB_UI_ENABLED = "hbase.resource.manager.web.ui.enabled";
  
  // Default values
  public static final int DEFAULT_RESOURCE_MANAGER_HTTP_PORT = 16030;
  public static final String DEFAULT_RESOURCE_MANAGER_HTTP_ADDRESS = "0.0.0.0";
  public static final boolean DEFAULT_RESOURCE_MANAGER_WEB_UI_ENABLED = true;
  
  private final Configuration conf;
  private final HierarchicalResourceManager resourceManager;
  private InfoServer infoServer;
  private final boolean webUiEnabled;
  
  public ResourceManagerService(Configuration conf) {
    this.conf = conf;
    this.resourceManager = new HierarchicalResourceManager(conf);
    this.webUiEnabled = conf.getBoolean(RESOURCE_MANAGER_WEB_UI_ENABLED, 
        DEFAULT_RESOURCE_MANAGER_WEB_UI_ENABLED);
  }
  
  /**
   * Start the resource manager service
   */
  public void start() throws IOException {
    LOG.info("Starting Resource Manager Service");
    
    // Start the hierarchical resource manager
    resourceManager.start();
    
    // Initialize default resource pools
    resourceManager.setResourcePools(resourceManager.getResourcePools());
    
    // Start web interface if enabled
    if (webUiEnabled) {
      startWebInterface();
    }
    
    LOG.info("Resource Manager Service started successfully");
  }
  
  /**
   * Stop the resource manager service
   */
  public void stop() {
    LOG.info("Stopping Resource Manager Service");
    
    // Stop web interface
    if (infoServer != null) {
      try {
        infoServer.stop();
      } catch (Exception e) {
        LOG.warn("Error stopping web interface", e);
      }
    }
    
    // Stop the hierarchical resource manager
    if (resourceManager != null) {
      resourceManager.stop();
    }
    
    LOG.info("Resource Manager Service stopped");
  }
  
  /**
   * Get the resource manager instance
   */
  public HierarchicalResourceManager getResourceManager() {
    return resourceManager;
  }
  
  /**
   * Check if the service is running
   */
  public boolean isRunning() {
    return resourceManager != null && resourceManager.isRunning();
  }
  
  /**
   * Get the web interface URL
   */
  public String getWebInterfaceUrl() {
    if (infoServer != null) {
      return infoServer.getUrl();
    }
    return null;
  }
  
  // Private helper methods
  
  private void startWebInterface() throws IOException {
    String address = conf.get(RESOURCE_MANAGER_HTTP_ADDRESS, DEFAULT_RESOURCE_MANAGER_HTTP_ADDRESS);
    int port = conf.getInt(RESOURCE_MANAGER_HTTP_PORT, DEFAULT_RESOURCE_MANAGER_HTTP_PORT);
    
    try {
      // Create and start the info server
      infoServer = new InfoServer("resource-manager", address, port, false, conf);
      infoServer.addServlet("resource-manager", "/resource-manager/*", 
          ResourceManagementController.class);
      
      // Set servlet context attributes
      infoServer.setAttribute("resource.manager", resourceManager);
      
      // Start the server
      infoServer.start();
      
      LOG.info("Resource Manager web interface started at: {}", infoServer.getUrl());
      
    } catch (Exception e) {
      LOG.error("Failed to start Resource Manager web interface", e);
      throw new IOException("Failed to start web interface", e);
    }
  }
}