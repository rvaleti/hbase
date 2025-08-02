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

import org.apache.yetus.audience.InterfaceAudience;
import org.apache.yetus.audience.InterfaceStability;

/**
 * Configuration for throttling settings
 */
@InterfaceAudience.Public
@InterfaceStability.Unstable
public class ThrottlingConfig {
  
  private Boolean enabled;
  private Long globalRpsLimit;
  private Long userRpsLimit;
  private Long namespaceRpsLimit;
  private Long tableRpsLimit;
  private Double memoryThreshold;
  private Double cpuThreshold;
  private Double ioThreshold;
  
  public ThrottlingConfig() {
  }
  
  public Boolean getEnabled() {
    return enabled;
  }
  
  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }
  
  public Long getGlobalRpsLimit() {
    return globalRpsLimit;
  }
  
  public void setGlobalRpsLimit(Long globalRpsLimit) {
    this.globalRpsLimit = globalRpsLimit;
  }
  
  public Long getUserRpsLimit() {
    return userRpsLimit;
  }
  
  public void setUserRpsLimit(Long userRpsLimit) {
    this.userRpsLimit = userRpsLimit;
  }
  
  public Long getNamespaceRpsLimit() {
    return namespaceRpsLimit;
  }
  
  public void setNamespaceRpsLimit(Long namespaceRpsLimit) {
    this.namespaceRpsLimit = namespaceRpsLimit;
  }
  
  public Long getTableRpsLimit() {
    return tableRpsLimit;
  }
  
  public void setTableRpsLimit(Long tableRpsLimit) {
    this.tableRpsLimit = tableRpsLimit;
  }
  
  public Double getMemoryThreshold() {
    return memoryThreshold;
  }
  
  public void setMemoryThreshold(Double memoryThreshold) {
    this.memoryThreshold = memoryThreshold;
  }
  
  public Double getCpuThreshold() {
    return cpuThreshold;
  }
  
  public void setCpuThreshold(Double cpuThreshold) {
    this.cpuThreshold = cpuThreshold;
  }
  
  public Double getIoThreshold() {
    return ioThreshold;
  }
  
  public void setIoThreshold(Double ioThreshold) {
    this.ioThreshold = ioThreshold;
  }
  
  @Override
  public String toString() {
    return "ThrottlingConfig{" +
        "enabled=" + enabled +
        ", globalRpsLimit=" + globalRpsLimit +
        ", userRpsLimit=" + userRpsLimit +
        ", namespaceRpsLimit=" + namespaceRpsLimit +
        ", tableRpsLimit=" + tableRpsLimit +
        ", memoryThreshold=" + memoryThreshold +
        ", cpuThreshold=" + cpuThreshold +
        ", ioThreshold=" + ioThreshold +
        '}';
  }
}