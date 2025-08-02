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
package org.apache.hadoop.hbase.resource.config;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.resource.SchedulerConfig;
import org.apache.hadoop.hbase.resource.ThrottlingConfig;
import org.apache.yetus.audience.InterfaceAudience;
import org.apache.yetus.audience.InterfaceStability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration manager for the hierarchical resource management system
 */
@InterfaceAudience.Private
@InterfaceStability.Unstable
public class ResourceConfigManager {
  private static final Logger LOG = LoggerFactory.getLogger(ResourceConfigManager.class);
  
  // Configuration keys
  public static final String CONFIG_STORAGE_TYPE = "hbase.resource.config.storage.type";
  public static final String CONFIG_REFRESH_INTERVAL = "hbase.resource.config.refresh.interval.ms";
  
  // Default values
  public static final String DEFAULT_CONFIG_STORAGE_TYPE = "memory";
  public static final long DEFAULT_CONFIG_REFRESH_INTERVAL = 30000; // 30 seconds
  
  private final Configuration conf;
  private final String storageType;
  private final long refreshInterval;
  
  // In-memory configuration storage
  private final Map<String, SchedulerConfig> schedulerConfigs = new ConcurrentHashMap<>();
  private final Map<String, ThrottlingConfig> throttlingConfigs = new ConcurrentHashMap<>();
  
  // State
  private volatile boolean running = false;
  
  public ResourceConfigManager(Configuration conf) {
    this.conf = conf;
    this.storageType = conf.get(CONFIG_STORAGE_TYPE, DEFAULT_CONFIG_STORAGE_TYPE);
    this.refreshInterval = conf.getLong(CONFIG_REFRESH_INTERVAL, DEFAULT_CONFIG_REFRESH_INTERVAL);
    
    // Initialize with default configurations
    initializeDefaultConfigs();
  }
  
  public void start() throws IOException {
    LOG.info("Starting Resource Config Manager with storage type: {}", storageType);
    running = true;
  }
  
  public void stop() {
    LOG.info("Stopping Resource Config Manager");
    running = false;
    
    // Clear configurations
    schedulerConfigs.clear();
    throttlingConfigs.clear();
  }
  
  /**
   * Update scheduler configuration
   */
  public void updateSchedulerConfig(SchedulerConfig config) throws IOException {
    if (!running) {
      throw new IOException("Config manager not running");
    }
    
    schedulerConfigs.put("default", config);
    
    // In a production system, this would persist to storage
    LOG.info("Updated scheduler configuration: {}", config);
  }
  
  /**
   * Get scheduler configuration
   */
  public SchedulerConfig getSchedulerConfig() {
    return schedulerConfigs.get("default");
  }
  
  /**
   * Update throttling configuration
   */
  public void updateThrottlingConfig(ThrottlingConfig config) throws IOException {
    if (!running) {
      throw new IOException("Config manager not running");
    }
    
    throttlingConfigs.put("default", config);
    
    // In a production system, this would persist to storage
    LOG.info("Updated throttling configuration: {}", config);
  }
  
  /**
   * Get throttling configuration
   */
  public ThrottlingConfig getThrottlingConfig() {
    return throttlingConfigs.get("default");
  }
  
  /**
   * Get all scheduler configurations
   */
  public Map<String, SchedulerConfig> getAllSchedulerConfigs() {
    return new ConcurrentHashMap<>(schedulerConfigs);
  }
  
  /**
   * Get all throttling configurations
   */
  public Map<String, ThrottlingConfig> getAllThrottlingConfigs() {
    return new ConcurrentHashMap<>(throttlingConfigs);
  }
  
  /**
   * Refresh configurations from storage
   */
  public void refreshConfigurations() {
    if (!running) {
      return;
    }
    
    try {
      // In a production system, this would reload from persistent storage
      LOG.debug("Refreshing configurations from storage");
      
    } catch (Exception e) {
      LOG.warn("Error refreshing configurations", e);
    }
  }
  
  // Private helper methods
  
  private void initializeDefaultConfigs() {
    // Initialize default scheduler configuration
    SchedulerConfig defaultSchedulerConfig = new SchedulerConfig();
    schedulerConfigs.put("default", defaultSchedulerConfig);
    
    // Initialize default throttling configuration
    ThrottlingConfig defaultThrottlingConfig = new ThrottlingConfig();
    throttlingConfigs.put("default", defaultThrottlingConfig);
  }
}