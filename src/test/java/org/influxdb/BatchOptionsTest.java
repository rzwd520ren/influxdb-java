package org.influxdb;

import org.influxdb.InfluxDB.ConsistencyLevel;
import org.influxdb.InfluxDBException.DatabaseNotFoundError;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;


@RunWith(JUnitPlatform.class)
public class BatchOptionsTest {

  private InfluxDB influxDB;

  @BeforeEach
  public void setUp() throws InterruptedException, IOException {
    this.influxDB = TestUtils.connectToInfluxDB();
  }

  /**
   * Test the implementation of {@link InfluxDB#enableBatch(int, int, TimeUnit, ThreadFactory)}.
   */
  @Test
  public void testBatchEnabledWithDefaultSettings() {
    try {
      this.influxDB.enableBatch();

    }
    finally {
      this.influxDB.disableBatch();
    }
  }

  @Test
  public void testParametersSet() {
    BatchOptions options = BatchOptions.DEFAULTS.actions(3);
    Assertions.assertEquals(3, options.getActions());
    options=options.consistency(InfluxDB.ConsistencyLevel.ANY);
    Assertions.assertEquals(InfluxDB.ConsistencyLevel.ANY, options.getConsistency());
    options=options.flushDuration(1001);
    Assertions.assertEquals(1001, options.getFlushDuration());
    options=options.bufferLimit(7070);
    Assertions.assertEquals(7070, options.getBufferLimit());
    options=options.jitterDuration(104);
    Assertions.assertEquals(104, options.getJitterDuration());
    BiConsumer<Iterable<Point>, Throwable> handler=new BiConsumer<Iterable<Point>, Throwable>() {
      @Override
      public void accept(Iterable<Point> points, Throwable throwable) {

      }
    };
    options=options.exceptionHandler(handler);
    Assertions.assertEquals(handler, options.getExceptionHandler());
    ThreadFactory tf=Executors.defaultThreadFactory();
    options=options.threadFactory(tf);
    Assertions.assertEquals(tf, options.getThreadFactory());
  }

  /**
   * Test the implementation of {@link BatchOptions#actions(int)} }.
   */
  @Test
  public void testActionsSetting() throws InterruptedException {
    String dbName = "write_unittest_" + System.currentTimeMillis();
    try {
      BatchOptions options = BatchOptions.DEFAULTS.actions(3);

      this.influxDB.enableBatch(options);
      this.influxDB.createDatabase(dbName);
      this.influxDB.setDatabase(dbName);
      for (int j = 0; j < 5; j++) {
        Point point = Point.measurement("cpu")
                .time(j,TimeUnit.MILLISECONDS)
                .addField("idle", (double) j)
                .addField("user", 2.0 * j)
                .addField("system", 3.0 * j).build();
        this.influxDB.write(point);
      }
      Thread.sleep(500);
      QueryResult result=influxDB.query(new Query("select * from cpu", dbName));
      Assertions.assertEquals(3, result.getResults().get(0).getSeries().get(0).getValues().size());
    }
    finally {
      this.influxDB.disableBatch();
      this.influxDB.deleteDatabase(dbName);
    }
  }

  /**
   * Test the implementation of {@link BatchOptions#flushDuration(int)} }.
   * @throws InterruptedException 
   */
  @Test
  public void testFlushDuration() throws InterruptedException {
    String dbName = "write_unittest_" + System.currentTimeMillis();
    try {
      BatchOptions options = BatchOptions.DEFAULTS.flushDuration(500);
      influxDB.createDatabase(dbName);
      influxDB.setDatabase(dbName);
      influxDB.enableBatch(options);
      write20Points(influxDB);
      
      QueryResult result = influxDB.query(new Query("select * from weather", dbName));
      Assertions.assertNull(result.getResults().get(0).getSeries());
      Assertions.assertNull(result.getResults().get(0).getError());
      
      Thread.sleep(1000);
      result = influxDB.query(new Query("select * from weather", dbName));
      Assertions.assertEquals(20, result.getResults().get(0).getSeries().get(0).getValues().size());
    }
    finally {
      this.influxDB.disableBatch();
      this.influxDB.deleteDatabase(dbName);
    }
  }
  
  /**
   * Test the implementation of {@link BatchOptions#jitterDuration(int)} }.
   * @throws InterruptedException 
   */
  @Test
  public void testJitterDuration() throws InterruptedException {
    
   
    String dbName = "write_unittest_" + System.currentTimeMillis();
    try {
      BatchOptions options = BatchOptions.DEFAULTS.flushDuration(1000).jitterDuration(125);
      influxDB.createDatabase(dbName);
      influxDB.setDatabase(dbName);
      influxDB.enableBatch(options);
      write20Points(influxDB);
      
      QueryResult result = influxDB.query(new Query("select * from weather", dbName));
      Assertions.assertNull(result.getResults().get(0).getSeries());
      Assertions.assertNull(result.getResults().get(0).getError());
      
      Thread.sleep(1125);
      result = influxDB.query(new Query("select * from weather", dbName));
      Assertions.assertEquals(20, result.getResults().get(0).getSeries().get(0).getValues().size());
    }
    finally {
      influxDB.disableBatch();
      influxDB.deleteDatabase(dbName);
    }
    
    
  }
  
  /**
   * Test the implementation of {@link BatchOptions#jitterDuration(int)} }.
   */
  @Test
  public void testNegativeJitterDuration() {
    
    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      BatchOptions options = BatchOptions.DEFAULTS.jitterDuration(-10);
      influxDB.enableBatch(options);
      
      influxDB.disableBatch();
      options = BatchOptions.DEFAULTS.jitterDuration(0);
      influxDB.enableBatch();
      influxDB.disableBatch();
    });
  }
  
  
  private void doTestBufferLimit(int bufferLimit, int actions) throws InterruptedException {
    String dbName = "write_unittest_" + System.currentTimeMillis();
    try {
      BatchOptions options = BatchOptions.DEFAULTS.bufferLimit(bufferLimit).actions(actions);

      influxDB.createDatabase(dbName);
      influxDB.setDatabase(dbName);
      influxDB.enableBatch(options);
      write20Points(influxDB);
      
      QueryResult result = influxDB.query(new Query("select * from weather", dbName));
      Thread.sleep(1000);
      result = influxDB.query(new Query("select * from weather", dbName));
      Assertions.assertEquals(20, result.getResults().get(0).getSeries().get(0).getValues().size());
    }
    finally {
      influxDB.disableBatch();
      influxDB.deleteDatabase(dbName);
    }
  }
  
  
  /**
   * Test the implementation of {@link BatchOptions#bufferLimit(int)} }.
   */
  @Test
  public void testBufferLimit1() throws InterruptedException {
    
    doTestBufferLimit(3, 4);
    
  }

  /**
   * Test the implementation of {@link BatchOptions#bufferLimit(int)} }.
   */
  @Test
  public void testBufferLimit2() throws InterruptedException {
    
    doTestBufferLimit(10, 4);
    
  }
  /**
   * Test the implementation of {@link BatchOptions#bufferLimit(int)} }.
   */
  @Test
  public void testNegativeBufferLimit() {
    
    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      BatchOptions options = BatchOptions.DEFAULTS.bufferLimit(-10);
      influxDB.enableBatch(options);
      
      influxDB.disableBatch();
      options = BatchOptions.DEFAULTS.bufferLimit(0);
      influxDB.enableBatch();
      influxDB.disableBatch();
    });
  }
  
  /**
   * Test the implementation of {@link BatchOptions#threadFactory(ThreadFactory)} }.
   * @throws InterruptedException 
   */
  @Test
  public void testThreadFactory() throws InterruptedException {
    
    String dbName = "write_unittest_" + System.currentTimeMillis();
    try {
      BatchOptions options = BatchOptions.DEFAULTS.threadFactory((r) -> {
        return new Thread(r);
      });

      influxDB.createDatabase(dbName);
      influxDB.setDatabase(dbName);
      influxDB.enableBatch(options);
      write20Points(influxDB);
      
      Thread.sleep(3000);
      QueryResult result = influxDB.query(new Query("select * from weather", dbName));
      Assertions.assertEquals(20, result.getResults().get(0).getSeries().get(0).getValues().size());
    } finally {
      this.influxDB.disableBatch();
      this.influxDB.deleteDatabase(dbName);
    }
    
  }
  
  /**
   * Test the implementation of {@link BatchOptions#exceptionHandler(BiConsumer)} }.
   * @throws InterruptedException 
   */
  @Test
  public void testHandlerOnRetryImpossible() throws InterruptedException {
    
    String dbName = "write_unittest_" + System.currentTimeMillis();
    InfluxDB spy = spy(influxDB);
    doThrow(DatabaseNotFoundError.class).when(spy).write(any(BatchPoints.class));
    
    try {
      BiConsumer<Iterable<Point>, Throwable> mockHandler = mock(BiConsumer.class);
      BatchOptions options = BatchOptions.DEFAULTS.exceptionHandler(mockHandler);

      spy.createDatabase(dbName);
      spy.setDatabase(dbName);
      spy.enableBatch(options);
      
      writeSomePoints(spy, 1);
      
      Thread.sleep(1000);
      verify(mockHandler, times(1)).accept(any(), any());
      
      QueryResult result = influxDB.query(new Query("select * from weather", dbName));
      Assertions.assertNull(result.getResults().get(0).getSeries());
      Assertions.assertNull(result.getResults().get(0).getError());
    } finally {
      spy.disableBatch();
      spy.deleteDatabase(dbName);
    }
    
  }
  
  /**
   * Test the implementation of {@link BatchOptions#exceptionHandler(BiConsumer)} }.
   * @throws InterruptedException 
   */
  @Test
  public void testHandlerOnRetryPossible() throws InterruptedException {
    
    String dbName = "write_unittest_" + System.currentTimeMillis();
    InfluxDB spy = spy(influxDB);
    final Map<String, Boolean> map = new HashMap<>(1);
    map.put("firstCall", true);
    doAnswer((invocation) -> {
      if (map.get("firstCall")) {
        map.put("firstCall", false);
        throw new InfluxDBException("error");
      } else {
        return invocation.callRealMethod();
      }

    }).when(spy).write(any(BatchPoints.class));
    
    try {
      BiConsumer<Iterable<Point>, Throwable> mockHandler = mock(BiConsumer.class);
      BatchOptions options = BatchOptions.DEFAULTS.exceptionHandler(mockHandler);

      spy.createDatabase(dbName);
      spy.setDatabase(dbName);
      spy.enableBatch(options);
      
      writeSomePoints(spy, 1);
      
      Thread.sleep(5000);
      verify(mockHandler, never()).accept(any(), any());
      
      QueryResult result = influxDB.query(new Query("select * from weather", dbName));
      Assertions.assertNotNull(result.getResults().get(0).getSeries());
      Assertions.assertEquals(1, result.getResults().get(0).getSeries().get(0).getValues().size());
      
    } finally {
      spy.disableBatch();
      spy.deleteDatabase(dbName);
    }
    
  }

  /**
   * Test the implementation of {@link BatchOptions#consistency(InfluxDB.ConsistencyLevel)} }.
   * @throws InterruptedException 
   */
  @Test
  public void testConsistency() throws InterruptedException {
    String dbName = "write_unittest_" + System.currentTimeMillis();
    influxDB.createDatabase(dbName);
    influxDB.setDatabase(dbName);    
    try {
      int n = 5;
      for (ConsistencyLevel consistencyLevel : ConsistencyLevel.values()) {
        BatchOptions options = BatchOptions.DEFAULTS.consistency(consistencyLevel);

        influxDB.enableBatch(options);
        writeSomePoints(influxDB, n);
        
        Thread.sleep(2000);
        QueryResult result = influxDB.query(new Query("select * from weather", dbName));
        Assertions.assertEquals(n, result.getResults().get(0).getSeries().get(0).getValues().size());
        
        n += 5;
        this.influxDB.disableBatch();
      }
      
    } finally {
      this.influxDB.deleteDatabase(dbName);
    }
  }
  
  private void writeSomePoints(InfluxDB influxDB, int firstIndex, int lastIndex) {
    for (int i = firstIndex; i <= lastIndex; i++) {
      Point point = Point.measurement("weather")
              .time(i,TimeUnit.HOURS)
              .addField("temperature", (double) i)
              .addField("humidity", (double) (i) * 1.1)
              .addField("uv_index", "moderate").build();
      influxDB.write(point);
    }
  }
  
  private void write20Points(InfluxDB influxDB) {
    writeSomePoints(influxDB, 0, 19);
  }
  
  private void writeSomePoints(InfluxDB influxDB, int n) {
    writeSomePoints(influxDB, 0, n - 1);
  }
}