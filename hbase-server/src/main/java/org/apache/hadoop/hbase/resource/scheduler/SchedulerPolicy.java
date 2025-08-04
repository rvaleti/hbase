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
package org.apache.hadoop.hbase.resource.scheduler;

import org.apache.yetus.audience.InterfaceAudience;
import org.apache.yetus.audience.InterfaceStability;

/**
 * Enumeration of scheduling policies supported by the hierarchical scheduler
 */
@InterfaceAudience.Public
@InterfaceStability.Unstable
public enum SchedulerPolicy {
  
  /**
   * Fair Share scheduling - Allocates resources proportionally based on configured weights
   * and ensures no single user/queue can monopolize resources
   */
  FAIR_SHARE("fair", "Fair Share Scheduling"),
  
  /**
   * First In, First Out scheduling - Processes requests in the order they arrive
   */
  FIFO("fifo", "First In, First Out"),
  
  /**
   * Dominant Resource Fairness - Advanced fair scheduling that considers multiple resource types
   * and allocates based on the dominant (most constraining) resource for each user
   */
  DRF("drf", "Dominant Resource Fairness");
  
  private final String code;
  private final String description;
  
  SchedulerPolicy(String code, String description) {
    this.code = code;
    this.description = description;
  }
  
  public String getCode() {
    return code;
  }
  
  public String getDescription() {
    return description;
  }
  
  /**
   * Get SchedulerPolicy by code
   */
  public static SchedulerPolicy fromCode(String code) {
    for (SchedulerPolicy policy : values()) {
      if (policy.code.equalsIgnoreCase(code)) {
        return policy;
      }
    }
    throw new IllegalArgumentException("Unknown scheduler policy code: " + code);
  }
  
  @Override
  public String toString() {
    return description + " (" + code + ")";
  }
}