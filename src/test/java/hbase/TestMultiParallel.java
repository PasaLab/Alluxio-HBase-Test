package hbase;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Append;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Increment;
import org.apache.hadoop.hbase.client.NonceGenerator;
import org.apache.hadoop.hbase.client.PerClientRandomNonceGenerator;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.RetriesExhaustedWithDetailsException;
import org.apache.hadoop.hbase.client.Row;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.codec.KeyValueCodec;
import org.apache.hadoop.hbase.exceptions.OperationConflictException;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.Threads;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestMultiParallel {
  private static final HBaseUtility util = new HBaseUtility();
  private static final byte[] VALUE = Bytes.toBytes("value");
  private static final byte[] QUALIFIER = Bytes.toBytes("qual");
  private static final String FAMILY = "family";
  private static final TableName TEST_TABLE = TableName.valueOf("multi_test_table");
  private static final byte[] BYTES_FAMILY = Bytes.toBytes(FAMILY);
  private static final byte[] ONE_ROW = Bytes.toBytes("xxx");
  private static final byte [][] KEYS = makeKeys();

  private static Connection CONNECTION;

  @BeforeClass
  public static void beforeClass() throws Exception {
    util.getConfiguration().set(HConstants.RPC_CODEC_CONF_KEY,
        KeyValueCodec.class.getCanonicalName());
    HTable t = (HTable) util.createMultiRegionTable(TEST_TABLE, Bytes.toBytes(FAMILY));
    util.waitTableEnabled(TEST_TABLE);
    t.close();
    CONNECTION = ConnectionFactory.createConnection(util.getConfiguration());
  }

  @Before
  public void before() throws Exception {
      util.getAdmin().balancer();
      Thread.sleep(500);
  }

  private static byte[][] makeKeys() {
    byte [][] starterKeys = HBaseUtility.KEYS;
    // Create a "non-uniform" test set with the following characteristics:
    // a) Unequal number of keys per region

    // Don't use integer as a multiple, so that we have a number of keys that is
    // not a multiple of the number of regions
    int numKeys = (int) ((float) starterKeys.length * 10.33F);

    List<byte[]> keys = new ArrayList<>();
    for (int i = 0; i < numKeys; i++) {
      int kIdx = i % starterKeys.length;
      byte[] k = starterKeys[kIdx];
      byte[] cp = new byte[k.length + 1];
      System.arraycopy(k, 0, cp, 0, k.length);
      cp[k.length] = new Integer(i % 256).byteValue();
      keys.add(cp);
    }

    // b) Same duplicate keys (showing multiple Gets/Puts to the same row, which
    // should work)
    // c) keys are not in sorted order (within a region), to ensure that the
    // sorting code and index mapping doesn't break the functionality
    for (int i = 0; i < 100; i++) {
      int kIdx = i % starterKeys.length;
      byte[] k = starterKeys[kIdx];
      byte[] cp = new byte[k.length + 1];
      System.arraycopy(k, 0, cp, 0, k.length);
      cp[k.length] = new Integer(i % 256).byteValue();
      keys.add(cp);
    }
    return keys.toArray(new byte [][] {new byte [] {}});
  }

  @Test
  public void testBatchWithGet() throws Exception {
    Table table = new HTable(util.getConfiguration(), TEST_TABLE);

    // load test data
    List<Put> puts = constructPutRequests();
    table.batch(puts);

    // create a list of gets and run it
    List<Row> gets = new ArrayList<>();
    for (byte[] k : KEYS) {
      Get get = new Get(k);
      get.addColumn(BYTES_FAMILY, QUALIFIER);
      gets.add(get);
    }
    Result[] multiRes = new Result[gets.size()];
    table.batch(gets, multiRes);

    // Same gets using individual call API
    List<Result> singleRes = new ArrayList<>();
    for (Row get : gets) {
      singleRes.add(table.get((Get) get));
    }
    // Compare results
    Assert.assertEquals(singleRes.size(), multiRes.length);
    for (int i = 0; i < singleRes.size(); i++) {
      Assert.assertTrue(singleRes.get(i).containsColumn(BYTES_FAMILY, QUALIFIER));
      Cell[] singleKvs = singleRes.get(i).rawCells();
      Cell[] multiKvs = multiRes[i].rawCells();
      for (int j = 0; j < singleKvs.length; j++) {
        Assert.assertEquals(singleKvs[j], multiKvs[j]);
        Assert.assertEquals(0, Bytes.compareTo(CellUtil.cloneValue(singleKvs[j]),
            CellUtil.cloneValue(multiKvs[j])));
      }
    }
    table.close();
  }

  @Test
  public void testBadFam() throws Exception {
    Table table = new HTable(util.getConfiguration(), TEST_TABLE);

    List<Row> actions = new ArrayList<>();
    Put p = new Put(Bytes.toBytes("row1"));
    p.add(Bytes.toBytes("bad_family"), Bytes.toBytes("qual"), Bytes.toBytes("value"));
    actions.add(p);
    p = new Put(Bytes.toBytes("row2"));
    p.add(BYTES_FAMILY, Bytes.toBytes("qual"), Bytes.toBytes("value"));
    actions.add(p);

    // row1 and row2 should be in the same region.
    Object [] r = new Object[actions.size()];
    try {
      table.batch(actions, r);
      fail();
    } catch (RetriesExhaustedWithDetailsException ex) {
      // good!
      assertFalse(ex.mayHaveClusterIssues());
    }
    assertEquals(2, r.length);
    assertTrue(r[0] instanceof Throwable);
    assertTrue(r[1] instanceof Result);
    table.close();
  }

  @Test
  public void testFlushCommitsNoAbort() throws Exception {
    doTestFlushCommits(false);
  }

  @Test
  public void testFlushCommitsWithAbort() throws Exception {
    doTestFlushCommits(true);
  }

  private void doTestFlushCommits(boolean doAbort) throws Exception {
    // Load the data
    Table table = util.getConnection().getTable(TEST_TABLE);
    table.setWriteBufferSize(10 * 1024 * 1024);

    List<Put> puts = constructPutRequests();
    table.put(puts);

    if (doAbort) {
      puts = constructPutRequests();
      table.put(puts);
    }

    validateLoadedData(table);

    // Validate server and region count

    if (doAbort) {
    }

    table.close();
  }

  @Test
  public void testBatchWithPut() throws Exception {
    Table table = CONNECTION.getTable(TEST_TABLE);
    // put multiple rows using a batch
    List<Put> puts = constructPutRequests();

    Object[] results = table.batch(puts);
    validateSizeAndEmpty(results, KEYS.length);

      puts = constructPutRequests();
      try {
        results = table.batch(puts);
      } catch (RetriesExhaustedWithDetailsException ree) {
        table.close();
        throw ree;
      }
    validateSizeAndEmpty(results, KEYS.length);
    validateLoadedData(table);
    table.close();
  }

  @Test
  public void testBatchWithDelete() throws Exception {
    Table table = new HTable(util.getConfiguration(), TEST_TABLE);

    // Load some data
    List<Put> puts = constructPutRequests();
    Object[] results = table.batch(puts);
    validateSizeAndEmpty(results, KEYS.length);

    // Deletes
    List<Row> deletes = new ArrayList<>();
    for (int i = 0; i < KEYS.length; i++) {
      Delete delete = new Delete(KEYS[i]);
      delete.addFamily(BYTES_FAMILY);
      deletes.add(delete);
    }
    results = table.batch(deletes);
    validateSizeAndEmpty(results, KEYS.length);

    // Get to make sure ...
    for (byte[] k : KEYS) {
      Get get = new Get(k);
      get.addColumn(BYTES_FAMILY, QUALIFIER);
      Assert.assertFalse(table.exists(get));
    }
    table.close();
  }

  @Test
  public void testHTableDeleteWithList() throws Exception {
    Table table = new HTable(util.getConfiguration(), TEST_TABLE);

    // Load some data
    List<Put> puts = constructPutRequests();
    Object[] results = table.batch(puts);
    validateSizeAndEmpty(results, KEYS.length);

    // Deletes
    ArrayList<Delete> deletes = new ArrayList<>();
    for (int i = 0; i < KEYS.length; i++) {
      Delete delete = new Delete(KEYS[i]);
      delete.deleteFamily(BYTES_FAMILY);
      deletes.add(delete);
    }
    table.delete(deletes);
    Assert.assertTrue(deletes.isEmpty());

    // Get to make sure ...
    for (byte[] k : KEYS) {
      Get get = new Get(k);
      get.addColumn(BYTES_FAMILY, QUALIFIER);
      Assert.assertFalse(table.exists(get));
    }
    table.close();
  }

  @Test
  public void testBatchWithManyColsInOneRowGetAndPut() throws Exception {
    Table table = new HTable(util.getConfiguration(), TEST_TABLE);

    List<Row> puts = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      Put put = new Put(ONE_ROW);
      byte[] qual = Bytes.toBytes("column" + i);
      put.add(BYTES_FAMILY, qual, VALUE);
      puts.add(put);
    }
    Object[] results = table.batch(puts);

    // validate
    validateSizeAndEmpty(results, 100);

    // get the data back and validate that it is correct
    List<Row> gets = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      Get get = new Get(ONE_ROW);
      byte[] qual = Bytes.toBytes("column" + i);
      get.addColumn(BYTES_FAMILY, qual);
      gets.add(get);
    }

    Object[] multiRes = table.batch(gets);

    int idx = 0;
    for (Object r : multiRes) {
      byte[] qual = Bytes.toBytes("column" + idx);
      validateResult(r, qual, VALUE);
      idx++;
    }
    table.close();
  }

  @Test
  public void testBatchWithIncrementAndAppend() throws Exception {
    final byte[] QUAL1 = Bytes.toBytes("qual1");
    final byte[] QUAL2 = Bytes.toBytes("qual2");
    final byte[] QUAL3 = Bytes.toBytes("qual3");
    final byte[] QUAL4 = Bytes.toBytes("qual4");
    Table table = new HTable(util.getConfiguration(), TEST_TABLE);
    Delete d = new Delete(ONE_ROW);
    table.delete(d);
    Put put = new Put(ONE_ROW);
    put.add(BYTES_FAMILY, QUAL1, Bytes.toBytes("abc"));
    put.add(BYTES_FAMILY, QUAL2, Bytes.toBytes(1L));
    table.put(put);

    Increment inc = new Increment(ONE_ROW);
    inc.addColumn(BYTES_FAMILY, QUAL2, 1);
    inc.addColumn(BYTES_FAMILY, QUAL3, 1);

    Append a = new Append(ONE_ROW);
    a.add(BYTES_FAMILY, QUAL1, Bytes.toBytes("def"));
    a.add(BYTES_FAMILY, QUAL4, Bytes.toBytes("xyz"));
    List<Row> actions = new ArrayList<>();
    actions.add(inc);
    actions.add(a);

    Object[] multiRes = table.batch(actions);
    validateResult(multiRes[1], QUAL1, Bytes.toBytes("abcdef"));
    validateResult(multiRes[1], QUAL4, Bytes.toBytes("xyz"));
    validateResult(multiRes[0], QUAL2, Bytes.toBytes(2L));
    validateResult(multiRes[0], QUAL3, Bytes.toBytes(1L));
    table.close();
  }

  @Test
  public void testNonceCollision() throws Exception {
    final Connection connection = ConnectionFactory.createConnection(util.getConfiguration());
    Table table = connection.getTable(TEST_TABLE);
    Put put = new Put(ONE_ROW);
    put.add(BYTES_FAMILY, QUALIFIER, Bytes.toBytes(0L));

    // Replace nonce manager with the one that returns each nonce twice.
    NonceGenerator cnm = new PerClientRandomNonceGenerator() {
      long lastNonce = -1;
      @Override
      public synchronized long newNonce() {
        long nonce = 0;
        if (lastNonce == -1) {
          lastNonce = nonce = super.newNonce();
        } else {
          nonce = lastNonce;
          lastNonce = -1L;
        }
        return nonce;
      }
    };

      // First test sequential requests.
      Increment inc = new Increment(ONE_ROW);
      inc.addColumn(BYTES_FAMILY, QUALIFIER, 1L);
      table.increment(inc);
      inc = new Increment(ONE_ROW);
      inc.addColumn(BYTES_FAMILY, QUALIFIER, 1L);
      try {
        table.increment(inc);
      } catch (OperationConflictException ex) {
        //Expected
      }
      Get get = new Get(ONE_ROW);
      get.addColumn(BYTES_FAMILY, QUALIFIER);
      Result result = table.get(get);
      validateResult(result, QUALIFIER, Bytes.toBytes(1L));

      // Now run a bunch of requests in parallel, exactly half should succeed.
      int numRequests = 40;
      final CountDownLatch startedLatch = new CountDownLatch(numRequests);
      final CountDownLatch startLatch = new CountDownLatch(1);
      final CountDownLatch doneLatch = new CountDownLatch(numRequests);
      for (int i = 0; i < numRequests; ++i) {
        Runnable r = new Runnable() {
          @Override
          public void run() {
            Table table = null;
            try {
              table = connection.getTable(TEST_TABLE);
            } catch (IOException e) {
              fail("Not expected");
            }
            Increment inc = new Increment(ONE_ROW);
            inc.addColumn(BYTES_FAMILY, QUALIFIER, 1L);
            startedLatch.countDown();
            try {
              startLatch.await();
            } catch (InterruptedException e) {
              fail("Not expected");
            }
            try {
              table.increment(inc);
            } catch (OperationConflictException ex) { // Some threads are expected to fail.
            } catch (IOException ioEx) {
              fail("Not expected");
            }
            doneLatch.countDown();
          }
        };
        Threads.setDaemonThreadRunning(new Thread(r));
      }
      startedLatch.await(); // Wait until all threads are ready...
      startLatch.countDown(); // ...and unleash the herd!
      doneLatch.await();
      // Now verify
      get = new Get(ONE_ROW);
      get.addColumn(BYTES_FAMILY, QUALIFIER);
      result = table.get(get);
      validateResult(result, QUALIFIER, Bytes.toBytes((numRequests / 2) + 1L));
      table.close();
  }

  @Test
  public void testBatchWithMixedActions() throws Exception {
    Table table = new HTable(util.getConfiguration(), TEST_TABLE);

    // Load some data to start
    Object[] results = table.batch(constructPutRequests());
    validateSizeAndEmpty(results, KEYS.length);

    // Batch: get, get, put(new col), delete, get, get of put, get of deleted,
    // put
    List<Row> actions = new ArrayList<>();

    byte[] qual2 = Bytes.toBytes("qual2");
    byte[] val2 = Bytes.toBytes("putvalue2");

    // 0 get
    Get get = new Get(KEYS[10]);
    get.addColumn(BYTES_FAMILY, QUALIFIER);
    actions.add(get);

    // 1 get
    get = new Get(KEYS[11]);
    get.addColumn(BYTES_FAMILY, QUALIFIER);
    actions.add(get);

    // 2 put of new column
    Put put = new Put(KEYS[10]);
    put.add(BYTES_FAMILY, qual2, val2);
    actions.add(put);

    // 3 delete
    Delete delete = new Delete(KEYS[20]);
    delete.deleteFamily(BYTES_FAMILY);
    actions.add(delete);

    // 4 get
    get = new Get(KEYS[30]);
    get.addColumn(BYTES_FAMILY, QUALIFIER);
    actions.add(get);

    // There used to be a 'get' of a previous put here, but removed
    // since this API really cannot guarantee order in terms of mixed
    // get/puts.

    // 5 put of new column
    put = new Put(KEYS[40]);
    put.add(BYTES_FAMILY, qual2, val2);
    actions.add(put);

    results = table.batch(actions);

    // Validation
    validateResult(results[0]);
    validateResult(results[1]);
    validateEmpty(results[2]);
    validateEmpty(results[3]);
    validateResult(results[4]);
    validateEmpty(results[5]);

    // validate last put, externally from the batch
    get = new Get(KEYS[40]);
    get.addColumn(BYTES_FAMILY, qual2);
    Result r = table.get(get);
    validateResult(r, qual2, val2);

    table.close();
  }

  private void validateResult(Object r) {
    validateResult(r, QUALIFIER, VALUE);
  }

  private void validateResult(Object r1, byte[] qual, byte[] val) {
    Result r = (Result)r1;
    Assert.assertTrue(r.containsColumn(BYTES_FAMILY, qual));
    byte[] value = r.getValue(BYTES_FAMILY, qual);
    if (0 != Bytes.compareTo(val, value)) {
      System.out.println("Expected [" + Bytes.toStringBinary(val)
          + "] and got [" + Bytes.toStringBinary(value) + "]");
    }
  }

  private List<Put> constructPutRequests() {
    List<Put> puts = new ArrayList<>();
    for (byte[] k : KEYS) {
      Put put = new Put(k);
      put.add(BYTES_FAMILY, QUALIFIER, VALUE);
      puts.add(put);
    }
    return puts;
  }

  private void validateLoadedData(Table table) throws IOException {
    // get the data back and validate that it is correct
    List<Get> gets = new ArrayList<>();
    for (byte[] k : KEYS) {
      Get get = new Get(k);
      get.addColumn(BYTES_FAMILY, QUALIFIER);
      gets.add(get);
    }
    int retryNum = 10;
    Result[] results;
    do  {
      results = table.get(gets);
      boolean finished = true;
      for (Result result : results) {
        if (result.isEmpty()) {
          finished = false;
          break;
        }
      }
      if (finished) {
        break;
      }
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
      }
      retryNum--;
    } while (retryNum > 0);

    if (retryNum == 0) {
      fail("Timeout for validate data");
    } else {
      if (results != null) {
        for (Result r : results) {
          Assert.assertTrue(r.containsColumn(BYTES_FAMILY, QUALIFIER));
          Assert.assertEquals(0, Bytes.compareTo(VALUE, r
            .getValue(BYTES_FAMILY, QUALIFIER)));
        }
      }
    }
  }

  private void validateEmpty(Object r1) {
    Result result = (Result)r1;
    Assert.assertTrue(result != null);
    Assert.assertTrue(result.getRow() == null);
    Assert.assertEquals(0, result.rawCells().length);
  }

  private void validateSizeAndEmpty(Object[] results, int expectedSize) {
    // Validate got back the same number of Result objects, all empty
    Assert.assertEquals(expectedSize, results.length);
    for (Object result : results) {
      validateEmpty(result);
    }
  }
}
