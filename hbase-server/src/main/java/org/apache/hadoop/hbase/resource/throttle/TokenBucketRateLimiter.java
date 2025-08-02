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

import java.util.concurrent.atomic.AtomicLong;
import org.apache.yetus.audience.InterfaceAudience;
import org.apache.yetus.audience.InterfaceStability;

/**
 * Token bucket rate limiter implementation
 */
@InterfaceAudience.Private
@InterfaceStability.Unstable
public class TokenBucketRateLimiter implements RateLimiter {
  
  private volatile long permitsPerSecond;
  private volatile long maxPermits;
  private final AtomicLong availablePermits;
  private volatile long lastRefillTime;
  private volatile long lastUsedTime;
  
  public TokenBucketRateLimiter(long permitsPerSecond) {
    this.permitsPerSecond = Math.max(1, permitsPerSecond);
    this.maxPermits = this.permitsPerSecond;
    this.availablePermits = new AtomicLong(this.maxPermits);
    this.lastRefillTime = System.currentTimeMillis();
    this.lastUsedTime = System.currentTimeMillis();
  }
  
  @Override
  public boolean tryAcquire() {
    return tryAcquire(1);
  }
  
  @Override
  public boolean tryAcquire(int permits) {
    if (permits <= 0) {
      return true;
    }
    
    refill();
    
    long current = availablePermits.get();
    if (current >= permits) {
      if (availablePermits.compareAndSet(current, current - permits)) {
        lastUsedTime = System.currentTimeMillis();
        return true;
      }
      // Retry once in case of race condition
      current = availablePermits.get();
      if (current >= permits && availablePermits.compareAndSet(current, current - permits)) {
        lastUsedTime = System.currentTimeMillis();
        return true;
      }
    }
    
    return false;
  }
  
  @Override
  public void acquire() {
    acquire(1);
  }
  
  @Override
  public void acquire(int permits) {
    if (permits <= 0) {
      return;
    }
    
    while (!tryAcquire(permits)) {
      try {
        Thread.sleep(1); // Sleep for 1ms before retrying
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Interrupted while acquiring permits", e);
      }
    }
  }
  
  @Override
  public void setRate(long permitsPerSecond) {
    this.permitsPerSecond = Math.max(1, permitsPerSecond);
    this.maxPermits = this.permitsPerSecond;
    
    // Adjust available permits if necessary
    long current = availablePermits.get();
    if (current > maxPermits) {
      availablePermits.compareAndSet(current, maxPermits);
    }
  }
  
  @Override
  public long getRate() {
    return permitsPerSecond;
  }
  
  @Override
  public long getAvailablePermits() {
    refill();
    return Math.max(0, availablePermits.get());
  }
  
  @Override
  public void refill() {
    long now = System.currentTimeMillis();
    long timeSinceLastRefill = now - lastRefillTime;
    
    if (timeSinceLastRefill <= 0) {
      return;
    }
    
    // Calculate how many permits to add based on elapsed time
    long permitsToAdd = (timeSinceLastRefill * permitsPerSecond) / 1000;
    
    if (permitsToAdd > 0) {
      long current = availablePermits.get();
      long newPermits = Math.min(maxPermits, current + permitsToAdd);
      
      if (availablePermits.compareAndSet(current, newPermits)) {
        lastRefillTime = now;
      }
    }
  }
  
  @Override
  public long getLastUsedTime() {
    return lastUsedTime;
  }
  
  @Override
  public String toString() {
    return "TokenBucketRateLimiter{" +
        "permitsPerSecond=" + permitsPerSecond +
        ", maxPermits=" + maxPermits +
        ", availablePermits=" + availablePermits.get() +
        ", lastRefillTime=" + lastRefillTime +
        ", lastUsedTime=" + lastUsedTime +
        '}';
  }
}