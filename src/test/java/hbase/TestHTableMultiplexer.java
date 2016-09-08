package hbase;

import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.HTableMultiplexer;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestHTableMultiplexer {
  private static byte[] FAMILY = Bytes.toBytes("testFamily");
  private static byte[] QUALIFIER = Bytes.toBytes("testQualifier");
  private static byte[] VALUE1 = Bytes.toBytes("testValue1");
  private static byte[] VALUE2 = Bytes.toBytes("testValue2");
  private static int PER_REGIONSERVER_QUEUE_SIZE = 100000;

  private static HBaseUtility util = new HBaseUtility();
  private static Admin admin;
  @BeforeClass
  public static void setUp() throws Exception {
    util.getConfiguration().setBoolean("hbase.online.schema.update.enable", true);
    admin = util.getAdmin();
  }

  private static void checkExistence(HTable htable, byte[] row, byte[] family, byte[] quality)
      throws Exception {
    // verify that the Get returns the correct result
    Result r;
    Get get = new Get(row);
    get.addColumn(FAMILY, QUALIFIER);
    int nbTry = 0;
    do {
      assertTrue("Fail to get from " + htable.getName() + " after " + nbTry + " tries", nbTry < 50);
      nbTry++;
      Thread.sleep(100);
      r = htable.get(get);
    } while (r == null || r.getValue(FAMILY, QUALIFIER) == null);
    assertEquals(Bytes.toStringBinary(VALUE1), Bytes.toStringBinary(r.getValue(FAMILY, QUALIFIER)));
  }

  @Test
  public void testHTableMultiplexer() throws Exception {
    TableName TABLE_1 = TableName.valueOf("testHTableMultiplexer_1");
    TableName TABLE_2 = TableName.valueOf("testHTableMultiplexer_2");
    final int NUM_REGIONS = 10;
    final int VERSION = 3;
    List<Put> failedPuts;
    boolean success;
    
    HTableMultiplexer multiplexer = new HTableMultiplexer(util.getConfiguration(),
        PER_REGIONSERVER_QUEUE_SIZE);

    HTable htable1 =
        util.createTable(TABLE_1, new byte[][] { FAMILY }, VERSION,
        Bytes.toBytes("aaaaa"), Bytes.toBytes("zzzzz"), NUM_REGIONS);
    HTable htable2 =
        util.createTable(TABLE_2, new byte[][] { FAMILY }, VERSION, Bytes.toBytes("aaaaa"),
          Bytes.toBytes("zzzzz"), NUM_REGIONS);
    util.waitUntilAllRegionsAssigned(TABLE_1);
    util.waitUntilAllRegionsAssigned(TABLE_2);

    byte[][] startRows = htable1.getStartKeys();
    byte[][] endRows = htable1.getEndKeys();

    // SinglePut case
    for (int i = 0; i < NUM_REGIONS; i++) {
      byte [] row = startRows[i];
      if (row == null || row.length <= 0) continue;
      Put put = new Put(row).add(FAMILY, QUALIFIER, VALUE1);
      success = multiplexer.put(TABLE_1, put);
      assertTrue("multiplexer.put returns", success);

      put = new Put(row).add(FAMILY, QUALIFIER, VALUE1);
      success = multiplexer.put(TABLE_2, put);
      assertTrue("multiplexer.put failed", success);

      // verify that the Get returns the correct result
      checkExistence(htable1, startRows[i], FAMILY, QUALIFIER);
      checkExistence(htable2, startRows[i], FAMILY, QUALIFIER);
    }

    // MultiPut case
    List<Put> multiput = new ArrayList<Put>();
    for (int i = 0; i < NUM_REGIONS; i++) {
      byte [] row = endRows[i];
      if (row == null || row.length <= 0) continue;
      Put put = new Put(row);
      put.add(FAMILY, QUALIFIER, VALUE2);
      multiput.add(put);
    }
    failedPuts = multiplexer.put(TABLE_1, multiput);
    assertTrue(failedPuts == null);

    // verify that the Get returns the correct result
    for (int i = 0; i < NUM_REGIONS; i++) {
      byte [] row = endRows[i];
      if (row == null || row.length <= 0) continue;
      Get get = new Get(row);
      get.addColumn(FAMILY, QUALIFIER);
      Result r;
      int nbTry = 0;
      do {
        assertTrue(nbTry++ < 50);
        Thread.sleep(100);
        r = htable1.get(get);
      } while (r == null || r.getValue(FAMILY, QUALIFIER) == null ||
          Bytes.compareTo(VALUE2, r.getValue(FAMILY, QUALIFIER)) != 0);
    }
  }
}
