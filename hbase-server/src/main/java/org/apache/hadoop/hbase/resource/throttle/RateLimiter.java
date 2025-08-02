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
 * Interface for rate limiting functionality
 */
@InterfaceAudience.Public
@InterfaceStability.Unstable
public interface RateLimiter {
  
  /**
   * Try to acquire a permit without blocking
   * @return true if permit was acquired, false otherwise
   */
  boolean tryAcquire();
  
  /**
   * Try to acquire multiple permits without blocking
   * @param permits number of permits to acquire
   * @return true if permits were acquired, false otherwise
   */
  boolean tryAcquire(int permits);
  
  /**
   * Acquire a permit, blocking if necessary
   */
  void acquire();
  
  /**
   * Acquire multiple permits, blocking if necessary
   * @param permits number of permits to acquire
   */
  void acquire(int permits);
  
  /**
   * Set the rate (permits per second)
   * @param permitsPerSecond new rate
   */
  void setRate(long permitsPerSecond);
  
  /**
   * Get the current rate
   * @return permits per second
   */
  long getRate();
  
  /**
   * Get the number of available permits
   * @return available permits
   */
  long getAvailablePermits();
  
  /**
   * Refill the bucket with permits (called periodically)
   */
  void refill();
  
  /**
   * Get the last time this rate limiter was used
   * @return timestamp of last use
   */
  long getLastUsedTime();
}