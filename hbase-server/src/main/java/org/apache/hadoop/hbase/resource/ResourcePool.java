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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.Objects;
import org.apache.yetus.audience.InterfaceAudience;
import org.apache.yetus.audience.InterfaceStability;

/**
 * Represents a resource pool in the hierarchical resource management system
 */
@InterfaceAudience.Public
@InterfaceStability.Unstable
public class ResourcePool {
  
  private final String name;
  private final String parentName;
  private final float shareWeight;
  private final Map<String, ResourcePool> children = new ConcurrentHashMap<>();
  
  // Resource limits and usage
  private final AtomicLong maxMemoryMB = new AtomicLong(Long.MAX_VALUE);
  private final AtomicLong maxCpuCores = new AtomicLong(Long.MAX_VALUE);
  private final AtomicLong maxIoWeight = new AtomicLong(Long.MAX_VALUE);
  
  private final AtomicLong usedMemoryMB = new AtomicLong(0);
  private final AtomicLong usedCpuCores = new AtomicLong(0);
  private final AtomicLong usedIoWeight = new AtomicLong(0);
  
  private final AtomicLong allocatedMemoryMB = new AtomicLong(0);
  private final AtomicLong allocatedCpuCores = new AtomicLong(0);
  private final AtomicLong allocatedIoWeight = new AtomicLong(0);
  
  // Scheduling metrics
  private final AtomicLong totalRequests = new AtomicLong(0);
  private final AtomicLong grantedRequests = new AtomicLong(0);
  private final AtomicLong rejectedRequests = new AtomicLong(0);
  private final AtomicLong queuedRequests = new AtomicLong(0);
  
  // Fair share calculations
  private final AtomicReference<Double> fairShareMemory = new AtomicReference<>(0.0);
  private final AtomicReference<Double> fairShareCpu = new AtomicReference<>(0.0);
  private final AtomicReference<Double> fairShareIo = new AtomicReference<>(0.0);
  
  private final AtomicReference<Double> steadyFairShareMemory = new AtomicReference<>(0.0);
  private final AtomicReference<Double> steadyFairShareCpu = new AtomicReference<>(0.0);
  private final AtomicReference<Double> steadyFairShareIo = new AtomicReference<>(0.0);
  
  // Configuration
  private volatile boolean preemptionEnabled = true;
  private volatile int maxRunningApps = Integer.MAX_VALUE;
  private volatile long schedulingPolicy = 0; // 0=FAIR, 1=FIFO, 2=DRF
  
  public ResourcePool(String name, String parentName, float shareWeight) {
    this.name = Objects.requireNonNull(name, "Pool name cannot be null");
    this.parentName = parentName;
    this.shareWeight = Math.max(0.0f, shareWeight);
  }
  
  public String getName() {
    return name;
  }
  
  public String getParentName() {
    return parentName;
  }
  
  public float getShareWeight() {
    return shareWeight;
  }
  
  public boolean isRoot() {
    return parentName == null;
  }
  
  public boolean isLeaf() {
    return children.isEmpty();
  }
  
  // Child management
  public void addChild(ResourcePool child) {
    if (child != null && !child.getName().equals(this.name)) {
      children.put(child.getName(), child);
    }
  }
  
  public void removeChild(String childName) {
    children.remove(childName);
  }
  
  public ResourcePool getChild(String childName) {
    return children.get(childName);
  }
  
  public Set<String> getChildNames() {
    return Collections.unmodifiableSet(children.keySet());
  }
  
  public Map<String, ResourcePool> getChildren() {
    return Collections.unmodifiableMap(children);
  }
  
  // Resource limits
  public long getMaxMemoryMB() {
    return maxMemoryMB.get();
  }
  
  public void setMaxMemoryMB(long maxMemoryMB) {
    this.maxMemoryMB.set(Math.max(0, maxMemoryMB));
  }
  
  public long getMaxCpuCores() {
    return maxCpuCores.get();
  }
  
  public void setMaxCpuCores(long maxCpuCores) {
    this.maxCpuCores.set(Math.max(0, maxCpuCores));
  }
  
  public long getMaxIoWeight() {
    return maxIoWeight.get();
  }
  
  public void setMaxIoWeight(long maxIoWeight) {
    this.maxIoWeight.set(Math.max(0, maxIoWeight));
  }
  
  // Resource usage
  public long getUsedMemoryMB() {
    return usedMemoryMB.get();
  }
  
  public long getUsedCpuCores() {
    return usedCpuCores.get();
  }
  
  public long getUsedIoWeight() {
    return usedIoWeight.get();
  }
  
  public long getAllocatedMemoryMB() {
    return allocatedMemoryMB.get();
  }
  
  public long getAllocatedCpuCores() {
    return allocatedCpuCores.get();
  }
  
  public long getAllocatedIoWeight() {
    return allocatedIoWeight.get();
  }
  
  // Resource allocation/deallocation
  public boolean allocateResources(long memoryMB, long cpuCores, long ioWeight) {
    if (canAllocate(memoryMB, cpuCores, ioWeight)) {
      allocatedMemoryMB.addAndGet(memoryMB);
      allocatedCpuCores.addAndGet(cpuCores);
      allocatedIoWeight.addAndGet(ioWeight);
      return true;
    }
    return false;
  }
  
  public void deallocateResources(long memoryMB, long cpuCores, long ioWeight) {
    allocatedMemoryMB.addAndGet(-memoryMB);
    allocatedCpuCores.addAndGet(-cpuCores);
    allocatedIoWeight.addAndGet(-ioWeight);
  }
  
  public void updateUsage(long memoryMB, long cpuCores, long ioWeight) {
    usedMemoryMB.set(Math.max(0, memoryMB));
    usedCpuCores.set(Math.max(0, cpuCores));
    usedIoWeight.set(Math.max(0, ioWeight));
  }
  
  // Resource availability checks
  public boolean canAllocate(long memoryMB, long cpuCores, long ioWeight) {
    return (allocatedMemoryMB.get() + memoryMB <= maxMemoryMB.get()) &&
           (allocatedCpuCores.get() + cpuCores <= maxCpuCores.get()) &&
           (allocatedIoWeight.get() + ioWeight <= maxIoWeight.get());
  }
  
  public long getAvailableMemoryMB() {
    return Math.max(0, maxMemoryMB.get() - allocatedMemoryMB.get());
  }
  
  public long getAvailableCpuCores() {
    return Math.max(0, maxCpuCores.get() - allocatedCpuCores.get());
  }
  
  public long getAvailableIoWeight() {
    return Math.max(0, maxIoWeight.get() - allocatedIoWeight.get());
  }
  
  // Usage ratios
  public double getMemoryUtilization() {
    long max = maxMemoryMB.get();
    return max == 0 ? 0.0 : (double) usedMemoryMB.get() / max;
  }
  
  public double getCpuUtilization() {
    long max = maxCpuCores.get();
    return max == 0 ? 0.0 : (double) usedCpuCores.get() / max;
  }
  
  public double getIoUtilization() {
    long max = maxIoWeight.get();
    return max == 0 ? 0.0 : (double) usedIoWeight.get() / max;
  }
  
  // Metrics
  public long getTotalRequests() {
    return totalRequests.get();
  }
  
  public long getGrantedRequests() {
    return grantedRequests.get();
  }
  
  public long getRejectedRequests() {
    return rejectedRequests.get();
  }
  
  public long getQueuedRequests() {
    return queuedRequests.get();
  }
  
  public void incrementTotalRequests() {
    totalRequests.incrementAndGet();
  }
  
  public void incrementGrantedRequests() {
    grantedRequests.incrementAndGet();
  }
  
  public void incrementRejectedRequests() {
    rejectedRequests.incrementAndGet();
  }
  
  public void setQueuedRequests(long count) {
    queuedRequests.set(Math.max(0, count));
  }
  
  public double getSuccessRate() {
    long total = totalRequests.get();
    return total == 0 ? 1.0 : (double) grantedRequests.get() / total;
  }
  
  // Fair share
  public double getFairShareMemory() {
    return fairShareMemory.get();
  }
  
  public void setFairShareMemory(double fairShare) {
    this.fairShareMemory.set(Math.max(0.0, fairShare));
  }
  
  public double getFairShareCpu() {
    return fairShareCpu.get();
  }
  
  public void setFairShareCpu(double fairShare) {
    this.fairShareCpu.set(Math.max(0.0, fairShare));
  }
  
  public double getFairShareIo() {
    return fairShareIo.get();
  }
  
  public void setFairShareIo(double fairShare) {
    this.fairShareIo.set(Math.max(0.0, fairShare));
  }
  
  public double getSteadyFairShareMemory() {
    return steadyFairShareMemory.get();
  }
  
  public void setSteadyFairShareMemory(double steadyFairShare) {
    this.steadyFairShareMemory.set(Math.max(0.0, steadyFairShare));
  }
  
  public double getSteadyFairShareCpu() {
    return steadyFairShareCpu.get();
  }
  
  public void setSteadyFairShareCpu(double steadyFairShare) {
    this.steadyFairShareCpu.set(Math.max(0.0, steadyFairShare));
  }
  
  public double getSteadyFairShareIo() {
    return steadyFairShareIo.get();
  }
  
  public void setSteadyFairShareIo(double steadyFairShare) {
    this.steadyFairShareIo.set(Math.max(0.0, steadyFairShare));
  }
  
  // Configuration
  public boolean isPreemptionEnabled() {
    return preemptionEnabled;
  }
  
  public void setPreemptionEnabled(boolean preemptionEnabled) {
    this.preemptionEnabled = preemptionEnabled;
  }
  
  public int getMaxRunningApps() {
    return maxRunningApps;
  }
  
  public void setMaxRunningApps(int maxRunningApps) {
    this.maxRunningApps = Math.max(0, maxRunningApps);
  }
  
  public long getSchedulingPolicy() {
    return schedulingPolicy;
  }
  
  public void setSchedulingPolicy(long schedulingPolicy) {
    this.schedulingPolicy = schedulingPolicy;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ResourcePool that = (ResourcePool) o;
    return Objects.equals(name, that.name);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(name);
  }
  
  @Override
  public String toString() {
    return "ResourcePool{" +
        "name='" + name + '\'' +
        ", parentName='" + parentName + '\'' +
        ", shareWeight=" + shareWeight +
        ", maxMemoryMB=" + maxMemoryMB.get() +
        ", maxCpuCores=" + maxCpuCores.get() +
        ", maxIoWeight=" + maxIoWeight.get() +
        ", usedMemoryMB=" + usedMemoryMB.get() +
        ", usedCpuCores=" + usedCpuCores.get() +
        ", usedIoWeight=" + usedIoWeight.get() +
        ", allocatedMemoryMB=" + allocatedMemoryMB.get() +
        ", allocatedCpuCores=" + allocatedCpuCores.get() +
        ", allocatedIoWeight=" + allocatedIoWeight.get() +
        ", children=" + children.keySet() +
        '}';
  }
}