# HBase RPC Integration with Hierarchical Resource Management

## Overview

This guide demonstrates the successful integration of the Hierarchical Resource Management framework with the HBase RPC layer, enabling real-time interception, monitoring, and management of all HBase operations (GET, PUT, SCAN, DELETE, ADMIN).

## What We've Implemented

### 1. RPC Interception Layer
- **`ResourceManagementInterceptor`**: Intercepts all RPC calls and estimates resource requirements
- **`RSRpcServicesWithResourceManagement`**: Enhanced RPC services that integrate resource management
- **`HRegionServerWithResourceManagement`**: Custom RegionServer that automatically uses enhanced RPC services

### 2. Real-time Metrics Collection
- **Request-level metrics**: Total requests, granted/denied, active/queued
- **Performance metrics**: Response times (P50, P95, P99, average)
- **Operation breakdown**: Metrics by operation type (GET, PUT, SCAN, DELETE, ADMIN)
- **Resource utilization**: Memory and CPU usage tracking
- **Throughput monitoring**: Requests per second calculation

### 3. Enhanced Web Interface
- **Real-time dashboard**: Updates every 5 seconds with live metrics
- **Operation breakdown charts**: Visual representation of request types
- **Performance percentiles**: Detailed response time analysis
- **Resource utilization bars**: Memory and CPU usage visualization
- **System status**: Active requests, queue depth, uptime tracking

## Running the System

### Configuration
The system is configured in `conf/hbase-site.xml` with:

```xml
<!-- Enable hierarchical resource management -->
<property>
  <name>hbase.resource.hierarchical.enabled</name>
  <value>true</value>
</property>

<!-- Use enhanced RegionServer -->
<property>
  <name>hbase.regionserver.impl</name>
  <value>org.apache.hadoop.hbase.regionserver.HRegionServerWithResourceManagement</value>
</property>

<!-- Web UI Configuration -->
<property>
  <name>hbase.resource.web.ui.enabled</name>
  <value>true</value>
</property>

<property>
  <name>hbase.resource.web.ui.port</name>
  <value>16030</value>
</property>
```

### Starting HBase with Resource Management

1. **Using the Test Framework**:
   ```bash
   cd hbase-server
   mvn test -Dtest=TestRpcIntegration#main
   ```

2. **Manual Configuration**:
   ```bash
   # Start HBase with custom RegionServer
   ./bin/hbase regionserver
   ```

3. **Access the Web Interface**:
   - URL: `http://localhost:16030/resource-manager/`
   - Navigate to the "Metrics" tab to see real-time RPC metrics

## Real-time Metrics Demo

### Running the Demo Client

The `TestRpcIntegration` class provides a continuous load generator that demonstrates the system in action:

```java
// Run various operations to generate metrics
executor.scheduleAtFixedRate(() -> performGetOperations(), 1, 2, TimeUnit.SECONDS);
executor.scheduleAtFixedRate(() -> performPutOperations(), 0, 3, TimeUnit.SECONDS);
executor.scheduleAtFixedRate(() -> performScanOperations(), 2, 5, TimeUnit.SECONDS);
```

### What You'll See

1. **Request Interception**: Every HBase operation is intercepted and tracked
2. **Resource Allocation**: Requests are evaluated against available resources
3. **Real-time Updates**: Metrics update every 5 seconds in the web interface
4. **Operation Breakdown**: See the distribution of GET, PUT, SCAN, DELETE operations
5. **Performance Monitoring**: Response time percentiles and throughput tracking

## Key Integration Points

### 1. RPC Method Interception
```java
@Override
public GetResponse get(final RpcController controller, final GetRequest request)
    throws ServiceException {
  
  ResourceAllocation allocation = null;
  try {
    // Intercept and allocate resources
    if (resourceInterceptor != null) {
      allocation = resourceInterceptor.interceptGet(tableName, null, numColumns);
      if (!allocation.isGranted()) {
        throw new RequestTooBigException("Resource allocation denied");
      }
    }
    
    // Execute original request
    return super.get(controller, request);
    
  } finally {
    // Release resources
    if (resourceInterceptor != null && allocation != null) {
      resourceInterceptor.releaseResources(allocation, success, 0, error);
    }
  }
}
```

### 2. Resource Estimation
```java
private ResourceEstimate estimateGetResources(int numColumns) {
  long memoryMB = Math.max(1, numColumns / 10); // ~100KB per 10 columns
  long cpuCores = 1;
  long ioWeight = 100 + numColumns * 5;
  long durationMs = 50 + numColumns * 2;
  
  return new ResourceEstimate(memoryMB, cpuCores, ioWeight, durationMs);
}
```

### 3. Real-time Metrics API
```javascript
// Fetch real-time metrics from /resource-manager/metrics
const response = await fetch('/resource-manager/metrics');
const data = await response.json();

// Update UI with live data
metrics.totalRequests = requestMetrics.total_requests;
metrics.avgResponseTime = responseTimeMetrics.average.toFixed(1);
metrics.throughputPerSec = data.throughput_per_second.toFixed(1);
```

## Architecture Flow

```
Client Request → RSRpcServicesWithResourceManagement
                 ↓
              ResourceManagementInterceptor
                 ↓
              HierarchicalResourceManager (Resource Allocation)
                 ↓
              Original HBase Processing
                 ↓
              Resource Release & Metrics Update
                 ↓
              Real-time Web Dashboard (Auto-refresh every 5s)
```

## Observed Metrics

### Request Metrics
- **Total Requests**: Cumulative count of all intercepted requests
- **Granted Requests**: Successfully allocated requests
- **Denied Requests**: Requests denied due to resource constraints
- **Success Rate**: Percentage of granted requests
- **Active Requests**: Currently executing requests
- **Queued Requests**: Requests waiting for resources

### Performance Metrics
- **Average Response Time**: Mean execution time
- **P50 (Median)**: 50th percentile response time
- **P95**: 95th percentile response time  
- **P99**: 99th percentile response time
- **Throughput**: Requests processed per second

### Resource Utilization
- **Memory Usage**: Percentage of allocated memory pool
- **CPU Usage**: Percentage of allocated CPU resources
- **Operation Breakdown**: Distribution by operation type

### System Status
- **Uptime**: How long the resource manager has been running
- **Queue Depth**: Number of requests waiting for allocation
- **Pool Statistics**: Resource pool utilization details

## Benefits Demonstrated

1. **Visibility**: Complete visibility into all HBase operations
2. **Resource Control**: Ability to limit and manage resource consumption
3. **Performance Monitoring**: Real-time performance metrics and bottleneck identification
4. **Fair Scheduling**: Ensures equitable resource distribution across users/operations
5. **Throttling**: Prevents resource exhaustion through intelligent rate limiting
6. **Operational Insights**: Deep understanding of workload patterns and resource usage

## Next Steps

With the RPC integration complete and metrics flowing in real-time, operators can:

1. **Monitor Production Workloads**: Observe actual resource usage patterns
2. **Tune Resource Limits**: Adjust scheduler policies based on observed metrics
3. **Identify Bottlenecks**: Use performance metrics to optimize system configuration
4. **Implement Custom Policies**: Extend the framework for specific organizational needs
5. **Scale Resources**: Make informed decisions about cluster capacity planning

The system now provides complete end-to-end resource management with real-time visibility into all HBase operations, enabling sophisticated resource governance and performance optimization.