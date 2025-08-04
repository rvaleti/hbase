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
package org.apache.hadoop.hbase.resource.throttle;

import org.apache.yetus.audience.InterfaceAudience;
import org.apache.yetus.audience.InterfaceStability;

/**
 * Statistics for throttling operations
 */
@InterfaceAudience.Public
@InterfaceStability.Unstable
public class ThrottlingStats {
  
  private final boolean enabled;
  private final long globalRpsLimit;
  private final long userRpsLimit;
  private final long namespaceRpsLimit;
  private final long tableRpsLimit;
  private final double memoryThreshold;
  private final double cpuThreshold;
  private final double ioThreshold;
  private final long totalThrottledRequests;
  private final long globalThrottledRequests;
  private final long userThrottledRequests;
  private final long namespaceThrottledRequests;
  private final long tableThrottledRequests;
  private final long resourceThrottledRequests;
  private final int activeUserLimiters;
  private final int activeNamespaceLimiters;
  private final int activeTableLimiters;
  private final long timestamp;
  
  public ThrottlingStats(boolean enabled, long globalRpsLimit, long userRpsLimit,
      long namespaceRpsLimit, long tableRpsLimit, double memoryThreshold,
      double cpuThreshold, double ioThreshold, long totalThrottledRequests,
      long globalThrottledRequests, long userThrottledRequests,
      long namespaceThrottledRequests, long tableThrottledRequests,
      long resourceThrottledRequests, int activeUserLimiters,
      int activeNamespaceLimiters, int activeTableLimiters) {
    this.enabled = enabled;
    this.globalRpsLimit = globalRpsLimit;
    this.userRpsLimit = userRpsLimit;
    this.namespaceRpsLimit = namespaceRpsLimit;
    this.tableRpsLimit = tableRpsLimit;
    this.memoryThreshold = memoryThreshold;
    this.cpuThreshold = cpuThreshold;
    this.ioThreshold = ioThreshold;
    this.totalThrottledRequests = totalThrottledRequests;
    this.globalThrottledRequests = globalThrottledRequests;
    this.userThrottledRequests = userThrottledRequests;
    this.namespaceThrottledRequests = namespaceThrottledRequests;
    this.tableThrottledRequests = tableThrottledRequests;
    this.resourceThrottledRequests = resourceThrottledRequests;
    this.activeUserLimiters = activeUserLimiters;
    this.activeNamespaceLimiters = activeNamespaceLimiters;
    this.activeTableLimiters = activeTableLimiters;
    this.timestamp = System.currentTimeMillis();
  }
  
  public boolean isEnabled() {
    return enabled;
  }
  
  public long getGlobalRpsLimit() {
    return globalRpsLimit;
  }
  
  public long getUserRpsLimit() {
    return userRpsLimit;
  }
  
  public long getNamespaceRpsLimit() {
    return namespaceRpsLimit;
  }
  
  public long getTableRpsLimit() {
    return tableRpsLimit;
  }
  
  public double getMemoryThreshold() {
    return memoryThreshold;
  }
  
  public double getCpuThreshold() {
    return cpuThreshold;
  }
  
  public double getIoThreshold() {
    return ioThreshold;
  }
  
  public long getTotalThrottledRequests() {
    return totalThrottledRequests;
  }
  
  public long getGlobalThrottledRequests() {
    return globalThrottledRequests;
  }
  
  public long getUserThrottledRequests() {
    return userThrottledRequests;
  }
  
  public long getNamespaceThrottledRequests() {
    return namespaceThrottledRequests;
  }
  
  public long getTableThrottledRequests() {
    return tableThrottledRequests;
  }
  
  public long getResourceThrottledRequests() {
    return resourceThrottledRequests;
  }
  
  public int getActiveUserLimiters() {
    return activeUserLimiters;
  }
  
  public int getActiveNamespaceLimiters() {
    return activeNamespaceLimiters;
  }
  
  public int getActiveTableLimiters() {
    return activeTableLimiters;
  }
  
  public long getTimestamp() {
    return timestamp;
  }
  
  @Override
  public String toString() {
    return "ThrottlingStats{" +
        "enabled=" + enabled +
        ", globalRpsLimit=" + globalRpsLimit +
        ", userRpsLimit=" + userRpsLimit +
        ", namespaceRpsLimit=" + namespaceRpsLimit +
        ", tableRpsLimit=" + tableRpsLimit +
        ", memoryThreshold=" + memoryThreshold +
        ", cpuThreshold=" + cpuThreshold +
        ", ioThreshold=" + ioThreshold +
        ", totalThrottledRequests=" + totalThrottledRequests +
        ", globalThrottledRequests=" + globalThrottledRequests +
        ", userThrottledRequests=" + userThrottledRequests +
        ", namespaceThrottledRequests=" + namespaceThrottledRequests +
        ", tableThrottledRequests=" + tableThrottledRequests +
        ", resourceThrottledRequests=" + resourceThrottledRequests +
        ", activeUserLimiters=" + activeUserLimiters +
        ", activeNamespaceLimiters=" + activeNamespaceLimiters +
        ", activeTableLimiters=" + activeTableLimiters +
        ", timestamp=" + timestamp +
        '}';
  }
}