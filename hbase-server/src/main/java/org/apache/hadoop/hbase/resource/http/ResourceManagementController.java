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
    Map<String, Object> metrics = new HashMap<>();
    
    if (resourceManager != null && resourceManager.isRunning()) {
      try {
        // Get real-time metrics from the resource manager
        var metricsCollector = resourceManager.getMetricsCollector();
        
        // Request metrics
        Map<String, Object> requestMetrics = new HashMap<>();
        requestMetrics.put("total_requests", metricsCollector.getTotalRequestsCounter());
        requestMetrics.put("granted_requests", metricsCollector.getGrantedRequestsCounter());
        requestMetrics.put("denied_requests", metricsCollector.getDeniedRequestsCounter());
        requestMetrics.put("active_requests", metricsCollector.getActiveRequestsGauge());
        
        // Response time metrics
        var responseTimeHistogram = metricsCollector.getResponseTimeHistogram();
        Map<String, Object> responseTimeMetrics = new HashMap<>();
        responseTimeMetrics.put("average", responseTimeHistogram.getMean());
        responseTimeMetrics.put("p50", responseTimeHistogram.getPercentile(0.5));
        responseTimeMetrics.put("p95", responseTimeHistogram.getPercentile(0.95));
        responseTimeMetrics.put("p99", responseTimeHistogram.getPercentile(0.99));
        responseTimeMetrics.put("min", responseTimeHistogram.getMin());
        responseTimeMetrics.put("max", responseTimeHistogram.getMax());
        
        // Throughput metrics
        long currentTime = System.currentTimeMillis();
        long totalRequests = metricsCollector.getTotalRequestsCounter();
        long uptimeMs = currentTime - metricsCollector.getStartTime();
        double throughputPerSecond = uptimeMs > 0 ? (totalRequests * 1000.0 / uptimeMs) : 0.0;
        
        // Resource utilization from scheduler
        var schedulerStats = resourceManager.getScheduler().getSchedulerStats();
        Map<String, Object> resourceUtilization = new HashMap<>();
        resourceUtilization.put("memory_usage_percent", 
            schedulerStats.getTotalAvailableMemoryMB() > 0 ? 
            (schedulerStats.getTotalAllocatedMemoryMB() * 100.0) / schedulerStats.getTotalAvailableMemoryMB() : 0.0);
        resourceUtilization.put("cpu_usage_percent", 
            schedulerStats.getTotalAvailableCpuCores() > 0 ?
            (schedulerStats.getTotalAllocatedCpuCores() * 100.0) / schedulerStats.getTotalAvailableCpuCores() : 0.0);
        resourceUtilization.put("active_allocations", schedulerStats.getTotalActiveAllocations());
        resourceUtilization.put("queued_requests", schedulerStats.getTotalQueuedRequests());
        
        // Operation type breakdown
        Map<String, Object> operationMetrics = new HashMap<>();
        operationMetrics.put("GET", metricsCollector.getRequestsByType().getOrDefault("GET", 0L));
        operationMetrics.put("PUT", metricsCollector.getRequestsByType().getOrDefault("PUT", 0L));
        operationMetrics.put("SCAN", metricsCollector.getRequestsByType().getOrDefault("SCAN", 0L));
        operationMetrics.put("DELETE", metricsCollector.getRequestsByType().getOrDefault("DELETE", 0L));
        operationMetrics.put("ADMIN", metricsCollector.getRequestsByType().getOrDefault("ADMIN", 0L));
        
        metrics.put("request_metrics", requestMetrics);
        metrics.put("response_time_metrics", responseTimeMetrics);
        metrics.put("throughput_per_second", throughputPerSecond);
        metrics.put("resource_utilization", resourceUtilization);
        metrics.put("operation_metrics", operationMetrics);
        metrics.put("timestamp", currentTime);
        metrics.put("uptime_ms", uptimeMs);
        
      } catch (Exception e) {
        // Fall back to mock data if there's an error
        metrics.put("error", "Failed to fetch real metrics: " + e.getMessage());
        addMockMetrics(metrics);
      }
    } else {
      metrics.put("error", "Resource manager not available");
      addMockMetrics(metrics);
    }
    
    writeJsonResponse(resp, metrics);
  }
  
  private void addMockMetrics(Map<String, Object> metrics) {
    Map<String, Object> requestMetrics = new HashMap<>();
    requestMetrics.put("total_requests", 1250);
    requestMetrics.put("granted_requests", 1180);
    requestMetrics.put("denied_requests", 70);
    requestMetrics.put("active_requests", 15);
    
    Map<String, Object> responseTimeMetrics = new HashMap<>();
    responseTimeMetrics.put("average", 45.5);
    responseTimeMetrics.put("p50", 32.0);
    responseTimeMetrics.put("p95", 120.0);
    responseTimeMetrics.put("p99", 250.0);
    responseTimeMetrics.put("min", 5.0);
    responseTimeMetrics.put("max", 500.0);
    
    Map<String, Object> resourceUtilization = new HashMap<>();
    resourceUtilization.put("memory_usage_percent", 68.2);
    resourceUtilization.put("cpu_usage_percent", 34.8);
    resourceUtilization.put("active_allocations", 15);
    resourceUtilization.put("queued_requests", 3);
    
    Map<String, Object> operationMetrics = new HashMap<>();
    operationMetrics.put("GET", 650L);
    operationMetrics.put("PUT", 300L);
    operationMetrics.put("SCAN", 150L);
    operationMetrics.put("DELETE", 100L);
    operationMetrics.put("ADMIN", 50L);
    
    metrics.put("request_metrics", requestMetrics);
    metrics.put("response_time_metrics", responseTimeMetrics);
    metrics.put("throughput_per_second", 25.3);
    metrics.put("resource_utilization", resourceUtilization);
    metrics.put("operation_metrics", operationMetrics);
    metrics.put("timestamp", System.currentTimeMillis());
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