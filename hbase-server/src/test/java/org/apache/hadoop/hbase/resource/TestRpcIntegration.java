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

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseTestingUtil;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration test to demonstrate hierarchical resource management with RPC interception
 */
@Category(MediumTests.class)
public class TestRpcIntegration {
  
  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestRpcIntegration.class);
    
  private static final Logger LOG = LoggerFactory.getLogger(TestRpcIntegration.class);
  private static final HBaseTestingUtil TEST_UTIL = new HBaseTestingUtil();
  private static final TableName TABLE_NAME = TableName.valueOf("test_resource_management");
  private static final byte[] FAMILY = Bytes.toBytes("cf");
  private static final byte[] QUALIFIER = Bytes.toBytes("data");
  
  private static Connection connection;
  private static Table table;
  
  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    Configuration conf = TEST_UTIL.getConfiguration();
    
    // Enable hierarchical resource management
    conf.setBoolean("hbase.resource.hierarchical.enabled", true);
    conf.set("hbase.resource.scheduler.policy", "FAIR_SHARE");
    conf.setLong("hbase.resource.scheduler.total.memory.mb", 1024);
    conf.setInt("hbase.resource.scheduler.total.cpu.cores", 2);
    conf.setBoolean("hbase.resource.web.ui.enabled", true);
    conf.setInt("hbase.resource.web.ui.port", 16030);
    
    // Use our enhanced RegionServer
    conf.set("hbase.regionserver.impl", 
        "org.apache.hadoop.hbase.regionserver.HRegionServerWithResourceManagement");
    
    TEST_UTIL.startMiniCluster(1);
    
    // Create test table
    connection = ConnectionFactory.createConnection(conf);
    Admin admin = connection.getAdmin();
    
    TableDescriptorBuilder tableBuilder = TableDescriptorBuilder.newBuilder(TABLE_NAME);
    ColumnFamilyDescriptorBuilder cfBuilder = ColumnFamilyDescriptorBuilder.newBuilder(FAMILY);
    tableBuilder.setColumnFamily(cfBuilder.build());
    
    admin.createTable(tableBuilder.build());
    table = connection.getTable(TABLE_NAME);
    
    LOG.info("HBase cluster started with hierarchical resource management");
    LOG.info("Web UI should be available at: http://localhost:16030/resource-manager/");
  }
  
  @AfterClass
  public static void tearDownAfterClass() throws Exception {
    if (table != null) table.close();
    if (connection != null) connection.close();
    TEST_UTIL.shutdownMiniCluster();
  }
  
  @Test
  public void testRpcInterceptionMetrics() throws Exception {
    LOG.info("Starting RPC interception demonstration...");
    LOG.info("Monitor real-time metrics at: http://localhost:16030/resource-manager/");
    
    // Create a scheduled executor for continuous operations
    ScheduledExecutorService executor = Executors.newScheduledThreadPool(3);
    
    try {
      // Schedule GET operations
      executor.scheduleAtFixedRate(() -> {
        try {
          performGetOperations();
        } catch (Exception e) {
          LOG.warn("Error in GET operations", e);
        }
      }, 1, 2, TimeUnit.SECONDS);
      
      // Schedule PUT operations  
      executor.scheduleAtFixedRate(() -> {
        try {
          performPutOperations();
        } catch (Exception e) {
          LOG.warn("Error in PUT operations", e);
        }
      }, 0, 3, TimeUnit.SECONDS);
      
      // Schedule SCAN operations
      executor.scheduleAtFixedRate(() -> {
        try {
          performScanOperations();
        } catch (Exception e) {
          LOG.warn("Error in SCAN operations", e);
        }
      }, 2, 5, TimeUnit.SECONDS);
      
      // Let it run for a while to generate metrics
      LOG.info("Operations running... Check web UI for real-time metrics");
      Thread.sleep(30000); // Run for 30 seconds
      
    } finally {
      executor.shutdown();
      executor.awaitTermination(5, TimeUnit.SECONDS);
    }
    
    LOG.info("RPC interception demonstration completed");
  }
  
  private void performGetOperations() throws IOException {
    for (int i = 0; i < 5; i++) {
      Get get = new Get(Bytes.toBytes("row" + (i % 100)));
      get.addColumn(FAMILY, QUALIFIER);
      
      try {
        table.get(get);
        LOG.debug("GET operation completed for row{}", i % 100);
      } catch (Exception e) {
        LOG.debug("GET operation failed for row{}: {}", i % 100, e.getMessage());
      }
    }
  }
  
  private void performPutOperations() throws IOException {
    for (int i = 0; i < 3; i++) {
      Put put = new Put(Bytes.toBytes("row" + System.currentTimeMillis() + "_" + i));
      put.addColumn(FAMILY, QUALIFIER, Bytes.toBytes("data_" + System.currentTimeMillis()));
      
      try {
        table.put(put);
        LOG.debug("PUT operation completed for new row");
      } catch (Exception e) {
        LOG.debug("PUT operation failed: {}", e.getMessage());
      }
    }
  }
  
  private void performScanOperations() throws IOException {
    Scan scan = new Scan();
    scan.withStartRow(Bytes.toBytes("row0"));
    scan.withStopRow(Bytes.toBytes("row9"));
    scan.addColumn(FAMILY, QUALIFIER);
    scan.setBatch(10);
    
    try (ResultScanner scanner = table.getScanner(scan)) {
      int count = 0;
      for (Result result : scanner) {
        count++;
        if (count >= 10) break; // Limit scan results
      }
      LOG.debug("SCAN operation completed, found {} results", count);
    } catch (Exception e) {
      LOG.debug("SCAN operation failed: {}", e.getMessage());
    }
  }
  
  /**
   * Manual test runner - use this to start the test environment manually
   * and observe metrics in the web UI
   */
  public static void main(String[] args) throws Exception {
    System.setProperty("test.build.data", "target/test-data");
    
    TestRpcIntegration test = new TestRpcIntegration();
    setUpBeforeClass();
    
    try {
      System.out.println("=================================================");
      System.out.println("HBase with Hierarchical Resource Management");
      System.out.println("Web UI: http://localhost:16030/resource-manager/");
      System.out.println("=================================================");
      
      test.testRpcInterceptionMetrics();
      
      System.out.println("Press Enter to continue monitoring (Ctrl+C to stop)...");
      System.in.read();
      
    } finally {
      tearDownAfterClass();
    }
  }
}