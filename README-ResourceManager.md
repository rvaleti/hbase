# HBase Hierarchical Resource Management System

## Overview

The HBase Hierarchical Resource Management System provides comprehensive resource allocation, fair scheduling, and throttling capabilities for Apache HBase. This system enables administrators to:

- **Fair Resource Scheduling**: Implement configurable fair share, FIFO, or Dominant Resource Fairness (DRF) scheduling policies
- **Multi-level Throttling**: Apply rate limits at global, user, namespace, and table levels with resource utilization thresholds
- **Hierarchical Resource Pools**: Organize resources into hierarchical pools with configurable weights and limits
- **Real-time Monitoring**: Track resource usage, allocation patterns, and performance metrics
- **Web-based Administration**: Beautiful dashboard for monitoring and configuration management

## Architecture

### Core Components

1. **HierarchicalResourceManager**: Main coordinator for all resource management activities
2. **HierarchicalScheduler**: Implements configurable scheduling policies (Fair Share, FIFO, DRF)
3. **ResourceThrottleManager**: Multi-level throttling with rate limiting and resource thresholds
4. **ResourceMetricsCollector**: Comprehensive metrics collection and histogram tracking
5. **ResourceConfigManager**: Dynamic configuration management with hot-reload capabilities
6. **ResourceManagerService**: Integration service for HBase server lifecycle management

### Resource Pool Hierarchy

```
root
├── root.system (30% - System operations)
└── root.user (70% - User operations)
    ├── root.user.high (40% - High priority users)
    ├── root.user.normal (40% - Normal priority users)
    └── root.user.low (20% - Low priority users)
```

## Features

### Scheduling Policies

- **Fair Share**: Proportional resource allocation based on configured weights with preemption support
- **FIFO**: First-in-first-out processing for predictable ordering
- **DRF**: Dominant Resource Fairness for multi-resource environments

### Throttling Mechanisms

- **Rate Limiting**: Token bucket-based rate limiting at multiple levels
- **Resource Thresholds**: Memory, CPU, and I/O utilization-based throttling
- **Adaptive Throttling**: Dynamic adjustment based on system load

### Metrics and Monitoring

- **Real-time Metrics**: Counters, gauges, and histograms for all operations
- **Performance Tracking**: Wait times, execution times, success rates
- **Resource Utilization**: Memory, CPU, and I/O usage across pools
- **Historical Data**: Configurable retention of metric history

## Configuration

### Basic Configuration

```xml
<!-- Enable hierarchical resource management -->
<property>
  <name>hbase.resource.hierarchical.enabled</name>
  <value>true</value>
</property>

<!-- Scheduling policy -->
<property>
  <name>hbase.resource.hierarchical.scheduler.policy</name>
  <value>FAIR_SHARE</value>
</property>

<!-- Enable throttling -->
<property>
  <name>hbase.resource.hierarchical.throttle.enabled</name>
  <value>true</value>
</property>
```

### Rate Limiting Configuration

```xml
<!-- Global rate limit -->
<property>
  <name>hbase.resource.throttle.global.rps.limit</name>
  <value>10000</value>
</property>

<!-- Per-user rate limit -->
<property>
  <name>hbase.resource.throttle.user.rps.limit</name>
  <value>1000</value>
</property>
```

### Resource Thresholds

```xml
<!-- Memory threshold for throttling -->
<property>
  <name>hbase.resource.throttle.memory.threshold</name>
  <value>0.8</value>
</property>

<!-- CPU threshold for throttling -->
<property>
  <name>hbase.resource.throttle.cpu.threshold</name>
  <value>0.8</value>
</property>
```

## Web Dashboard

The system includes a beautiful web-based dashboard accessible at `http://hostname:16030/resource-manager/` with the following features:

### Overview Tab
- Real-time system status with animated indicators
- Resource allocation metrics (Memory, CPU, I/O)
- Resource pool table with utilization bars and health status
- Interactive charts and graphs

### Scheduler Tab
- Dynamic scheduler configuration
- Policy selection (Fair Share, FIFO, DRF)
- Preemption settings
- Real-time configuration updates

### Throttling Tab
- Rate limit configuration
- Resource threshold settings
- Enable/disable throttling controls
- Multi-level rate limit management

### Metrics Tab
- Resource allocation trend charts
- Pool utilization visualizations
- Historical performance data
- Interactive Chart.js visualizations

## REST API

The system exposes a comprehensive REST API for programmatic access:

### Status and Monitoring

```bash
# Get system status
GET /resource-manager/status

# Get resource pool information
GET /resource-manager/pools

# Get usage statistics
GET /resource-manager/stats

# Get metrics
GET /resource-manager/metrics
```

### Configuration Management

```bash
# Update scheduler configuration
POST /resource-manager/scheduler/config
Content-Type: application/json
{
  "schedulerPolicy": "FAIR_SHARE",
  "preemptionEnabled": true,
  "preemptionTimeoutMs": 30000
}

# Update throttling configuration
POST /resource-manager/throttling/config
Content-Type: application/json
{
  "enabled": true,
  "globalRpsLimit": 10000,
  "userRpsLimit": 1000
}
```

## Usage Examples

### Allocating Resources

```java
// Create a resource request
ResourceRequest request = new ResourceRequest.Builder()
    .setRequestId("req-001")
    .setUserId("user1")
    .setNamespace("analytics")
    .setTableName("events")
    .setResourceType(ResourceRequest.ResourceType.SCAN)
    .setPriority(ResourceRequest.Priority.HIGH)
    .setMemoryMB(1024)
    .setCpuCores(2)
    .setIoWeight(500)
    .setEstimatedDurationMs(30000)
    .build();

// Allocate resources
ResourceAllocation allocation = resourceManager.allocateResources(request);

if (allocation.isGranted()) {
    // Proceed with operation
    allocation.setStartTime(System.currentTimeMillis());
    
    // ... perform work ...
    
    // Release resources when done
    allocation.setEndTime(System.currentTimeMillis());
    resourceManager.releaseResources(allocation);
}
```

### Monitoring Resource Usage

```java
// Get current resource statistics
ResourceUsageStats stats = resourceManager.getResourceUsageStats();
System.out.println("Total Memory Allocated: " + stats.getTotalAllocatedMemoryMB() + " MB");
System.out.println("Total CPU Cores: " + stats.getTotalAllocatedCpuCores());

// Get resource pool information
Map<String, ResourcePool> pools = resourceManager.getResourcePools();
for (ResourcePool pool : pools.values()) {
    System.out.println("Pool: " + pool.getName() + 
                      ", Utilization: " + pool.getMemoryUtilization() * 100 + "%");
}
```

## Performance Considerations

### Scalability
- Lock-free data structures for high-throughput scenarios
- Efficient token bucket implementation for rate limiting
- Minimal overhead scheduling algorithms
- Configurable metrics collection to balance observability and performance

### Memory Management
- Circular buffer histograms with configurable retention
- Automatic cleanup of inactive rate limiters
- Memory-efficient resource pool tracking
- Configurable garbage collection for metrics

### Monitoring Overhead
- Asynchronous metrics collection
- Batched metric updates
- Configurable collection intervals
- Optional detailed tracking modes

## Troubleshooting

### Common Issues

1. **High Resource Contention**
   - Check fair share calculations
   - Adjust pool weights and limits
   - Consider enabling preemption

2. **Throttling Too Aggressive**
   - Review threshold settings
   - Check rate limit configurations
   - Monitor system resource utilization

3. **Poor Scheduler Performance**
   - Evaluate scheduling policy choice
   - Check queue sizes and wait times
   - Consider adjusting time limits

### Debug Configuration

```xml
<!-- Enable detailed logging -->
<property>
  <name>hbase.resource.debug.enabled</name>
  <value>true</value>
</property>

<!-- Increase metrics collection frequency -->
<property>
  <name>hbase.resource.hierarchical.metrics.update.interval.ms</name>
  <value>1000</value>
</property>
```

## Future Enhancements

- **GPU Resource Management**: Extend to GPU allocation and scheduling
- **Network Bandwidth Allocation**: Include network resources in scheduling decisions
- **Machine Learning Integration**: Predictive resource allocation based on historical patterns
- **Multi-Cluster Resource Management**: Coordinate resources across HBase clusters
- **Advanced Preemption Policies**: More sophisticated preemption algorithms
- **Integration with Kubernetes**: Native Kubernetes resource management integration

## Contributing

When contributing to the resource management system:

1. Follow existing code patterns and conventions
2. Add comprehensive unit tests for new features
3. Update documentation and configuration examples
4. Consider performance impact of changes
5. Test with various workload patterns

## License

This code is part of Apache HBase and is licensed under the Apache License 2.0.