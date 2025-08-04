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
package org.apache.hadoop.hbase.resource.metrics;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.yetus.audience.InterfaceAudience;
import org.apache.yetus.audience.InterfaceStability;

/**
 * Histogram metric implementation for tracking value distributions
 */
@InterfaceAudience.Private
@InterfaceStability.Unstable
public class HistogramMetric {
  
  private final int maxSize;
  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  
  private long[] values;
  private int count = 0;
  private boolean sorted = true;
  
  private final AtomicLong totalCount = new AtomicLong(0);
  private final AtomicLong sum = new AtomicLong(0);
  private volatile long min = Long.MAX_VALUE;
  private volatile long max = Long.MIN_VALUE;
  
  public HistogramMetric(int maxSize) {
    this.maxSize = Math.max(10, maxSize);
    this.values = new long[this.maxSize];
  }
  
  /**
   * Update the histogram with a new value
   */
  public void update(long value) {
    lock.writeLock().lock();
    try {
      // Update global statistics
      totalCount.incrementAndGet();
      sum.addAndGet(value);
      
      if (value < min) {
        min = value;
      }
      if (value > max) {
        max = value;
      }
      
      // Add to values array (circular buffer)
      if (count < maxSize) {
        values[count] = value;
        count++;
      } else {
        // Replace oldest value
        int index = (int) (totalCount.get() % maxSize);
        values[index] = value;
      }
      
      sorted = false;
      
    } finally {
      lock.writeLock().unlock();
    }
  }
  
  /**
   * Get the total count of values
   */
  public long getCount() {
    return totalCount.get();
  }
  
  /**
   * Get the minimum value
   */
  public long getMin() {
    return min == Long.MAX_VALUE ? 0 : min;
  }
  
  /**
   * Get the maximum value
   */
  public long getMax() {
    return max == Long.MIN_VALUE ? 0 : max;
  }
  
  /**
   * Get the mean value
   */
  public double getMean() {
    long totalValues = totalCount.get();
    return totalValues == 0 ? 0.0 : (double) sum.get() / totalValues;
  }
  
  /**
   * Get the value at the specified percentile
   */
  public long getPercentile(double percentile) {
    if (percentile < 0 || percentile > 100) {
      throw new IllegalArgumentException("Percentile must be between 0 and 100");
    }
    
    lock.readLock().lock();
    try {
      if (count == 0) {
        return 0;
      }
      
      if (count == 1) {
        return values[0];
      }
      
      // Sort values if necessary
      if (!sorted) {
        lock.readLock().unlock();
        lock.writeLock().lock();
        try {
          if (!sorted) {
            Arrays.sort(values, 0, count);
            sorted = true;
          }
          lock.readLock().lock();
        } finally {
          lock.writeLock().unlock();
        }
      }
      
      // Calculate percentile index
      double index = (percentile / 100.0) * (count - 1);
      int lowerIndex = (int) Math.floor(index);
      int upperIndex = (int) Math.ceil(index);
      
      if (lowerIndex == upperIndex) {
        return values[lowerIndex];
      }
      
      // Interpolate between the two values
      double weight = index - lowerIndex;
      return (long) (values[lowerIndex] * (1 - weight) + values[upperIndex] * weight);
      
    } finally {
      lock.readLock().unlock();
    }
  }
  
  /**
   * Get snapshot of current values
   */
  public long[] getSnapshot() {
    lock.readLock().lock();
    try {
      return Arrays.copyOf(values, count);
    } finally {
      lock.readLock().unlock();
    }
  }
  
  /**
   * Clear all values
   */
  public void clear() {
    lock.writeLock().lock();
    try {
      count = 0;
      totalCount.set(0);
      sum.set(0);
      min = Long.MAX_VALUE;
      max = Long.MIN_VALUE;
      sorted = true;
    } finally {
      lock.writeLock().unlock();
    }
  }
  
  @Override
  public String toString() {
    return "HistogramMetric{" +
        "count=" + getCount() +
        ", min=" + getMin() +
        ", max=" + getMax() +
        ", mean=" + String.format("%.2f", getMean()) +
        ", p50=" + getPercentile(50) +
        ", p95=" + getPercentile(95) +
        ", p99=" + getPercentile(99) +
        '}';
  }
}