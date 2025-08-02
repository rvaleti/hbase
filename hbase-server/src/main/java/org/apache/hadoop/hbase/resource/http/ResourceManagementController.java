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
package org.apache.hadoop.hbase.resource.http;

import java.io.IOException;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hadoop.hbase.resource.*;
import org.apache.hadoop.hbase.resource.scheduler.SchedulerStats;
import org.apache.hadoop.hbase.resource.throttle.ThrottlingStats;
import org.apache.yetus.audience.InterfaceAudience;
import org.apache.yetus.audience.InterfaceStability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST API controller for resource management
 */
@InterfaceAudience.Private
@InterfaceStability.Unstable
public class ResourceManagementController extends HttpServlet {
  private static final Logger LOG = LoggerFactory.getLogger(ResourceManagementController.class);
  
  private final HierarchicalResourceManager resourceManager;
  private final ObjectMapper objectMapper = new ObjectMapper();
  
  public ResourceManagementController(HierarchicalResourceManager resourceManager) {
    this.resourceManager = resourceManager;
  }
  
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) 
      throws ServletException, IOException {
    
    String pathInfo = req.getPathInfo();
    if (pathInfo == null) {
      pathInfo = "/";
    }
    
    resp.setContentType("application/json");
    resp.setCharacterEncoding("UTF-8");
    
    try {
      switch (pathInfo) {
        case "/":
        case "/status":
          handleStatus(req, resp);
          break;
        case "/pools":
          handleResourcePools(req, resp);
          break;
        case "/stats":
          handleStats(req, resp);
          break;
        case "/scheduler":
          handleSchedulerStats(req, resp);
          break;
        case "/throttling":
          handleThrottlingStats(req, resp);
          break;
        case "/metrics":
          handleMetrics(req, resp);
          break;
        default:
          resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
          writeErrorResponse(resp, "Endpoint not found: " + pathInfo);
      }
    } catch (Exception e) {
      LOG.error("Error handling request: " + pathInfo, e);
      resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      writeErrorResponse(resp, "Internal server error: " + e.getMessage());
    }
  }
  
  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) 
      throws ServletException, IOException {
    
    String pathInfo = req.getPathInfo();
    if (pathInfo == null) {
      resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      writeErrorResponse(resp, "Path info required");
      return;
    }
    
    resp.setContentType("application/json");
    resp.setCharacterEncoding("UTF-8");
    
    try {
      switch (pathInfo) {
        case "/scheduler/config":
          handleUpdateSchedulerConfig(req, resp);
          break;
        case "/throttling/config":
          handleUpdateThrottlingConfig(req, resp);
          break;
        default:
          resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
          writeErrorResponse(resp, "Endpoint not found: " + pathInfo);
      }
    } catch (Exception e) {
      LOG.error("Error handling POST request: " + pathInfo, e);
      resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      writeErrorResponse(resp, "Internal server error: " + e.getMessage());
    }
  }
  
  private void handleStatus(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    Map<String, Object> status = Map.of(
        "enabled", resourceManager.isEnabled(),
        "running", resourceManager.isRunning(),
        "timestamp", System.currentTimeMillis()
    );
    
    writeJsonResponse(resp, status);
  }
  
  private void handleResourcePools(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    Map<String, ResourcePool> pools = resourceManager.getResourcePools();
    writeJsonResponse(resp, pools);
  }
  
  private void handleStats(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    ResourceUsageStats stats = resourceManager.getResourceUsageStats();
    writeJsonResponse(resp, stats);
  }
  
  private void handleSchedulerStats(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    // Get scheduler stats (would need to expose this through resource manager)
    Map<String, Object> stats = Map.of(
        "message", "Scheduler stats endpoint",
        "timestamp", System.currentTimeMillis()
    );
    
    writeJsonResponse(resp, stats);
  }
  
  private void handleThrottlingStats(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    // Get throttling stats (would need to expose this through resource manager)
    Map<String, Object> stats = Map.of(
        "message", "Throttling stats endpoint",
        "timestamp", System.currentTimeMillis()
    );
    
    writeJsonResponse(resp, stats);
  }
  
  private void handleMetrics(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    // Get all metrics (would need to expose this through resource manager)
    Map<String, Object> metrics = Map.of(
        "message", "Metrics endpoint",
        "timestamp", System.currentTimeMillis()
    );
    
    writeJsonResponse(resp, metrics);
  }
  
  private void handleUpdateSchedulerConfig(HttpServletRequest req, HttpServletResponse resp) 
      throws IOException {
    
    try {
      SchedulerConfig config = objectMapper.readValue(req.getInputStream(), SchedulerConfig.class);
      resourceManager.updateSchedulerConfig(config);
      
      Map<String, Object> response = Map.of(
          "success", true,
          "message", "Scheduler configuration updated successfully",
          "timestamp", System.currentTimeMillis()
      );
      
      writeJsonResponse(resp, response);
      
    } catch (Exception e) {
      resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      writeErrorResponse(resp, "Invalid scheduler configuration: " + e.getMessage());
    }
  }
  
  private void handleUpdateThrottlingConfig(HttpServletRequest req, HttpServletResponse resp) 
      throws IOException {
    
    try {
      ThrottlingConfig config = objectMapper.readValue(req.getInputStream(), ThrottlingConfig.class);
      resourceManager.updateThrottlingConfig(config);
      
      Map<String, Object> response = Map.of(
          "success", true,
          "message", "Throttling configuration updated successfully",
          "timestamp", System.currentTimeMillis()
      );
      
      writeJsonResponse(resp, response);
      
    } catch (Exception e) {
      resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      writeErrorResponse(resp, "Invalid throttling configuration: " + e.getMessage());
    }
  }
  
  private void writeJsonResponse(HttpServletResponse resp, Object data) throws IOException {
    String json = objectMapper.writeValueAsString(data);
    resp.getWriter().write(json);
  }
  
  private void writeErrorResponse(HttpServletResponse resp, String message) throws IOException {
    Map<String, Object> error = Map.of(
        "error", true,
        "message", message,
        "timestamp", System.currentTimeMillis()
    );
    
    writeJsonResponse(resp, error);
  }
}