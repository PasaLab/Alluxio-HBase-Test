package hbase;

import com.google.common.collect.Maps;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import static org.junit.Assert.assertEquals;

public class TestSizeFailures {
  protected final static HBaseUtility util = new HBaseUtility();
  private static byte [] FAMILY = Bytes.toBytes("testFamily");
  private static TableName TABLENAME;
  private static final int NUM_ROWS = 1000 * 1000, NUM_COLS = 10;

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    Configuration conf = util.getConfiguration();
    conf.setBoolean("hbase.table.sanity.checks", true); // ignore sanity checks in the server

    // Write a bunch of data
    TABLENAME = TableName.valueOf("testSizeFailures");
    List<byte[]> qualifiers = new ArrayList<>();
    for (int i = 1; i <= 10; i++) {
      qualifiers.add(Bytes.toBytes(Integer.toString(i)));
    }

    HColumnDescriptor hcd = new HColumnDescriptor(FAMILY);
    HTableDescriptor desc = new HTableDescriptor(TABLENAME);
    desc.addFamily(hcd);
    byte[][] splits = new byte[9][2];
    for (int i = 1; i < 10; i++) {
      int split = 48 + i;
      splits[i - 1][0] = (byte) (split >>> 8);
      splits[i - 1][0] = (byte) (split);
    }
    util.deleteTableIfExists(TABLENAME);
    util.getAdmin().createTable(desc, splits);
    Connection conn = util.getConnection();

    try (Table table = conn.getTable(TABLENAME)) {
      List<Put> puts = new LinkedList<>();
      for (int i = 0; i < NUM_ROWS; i++) {
        Put p = new Put(Bytes.toBytes(Integer.toString(i)));
        for (int j = 0; j < NUM_COLS; j++) {
          byte[] value = new byte[50];
          Bytes.random(value);
          p.addColumn(FAMILY, Bytes.toBytes(Integer.toString(j)), value);
        }
        puts.add(p);

        if (puts.size() == 1000) {
          table.batch(puts, new Object[1000]);
          puts.clear();
        }
      }

      if (puts.size() > 0) {
        table.batch(puts, new Object[puts.size()]);
      }
    }
  }

  @Test
  public void testScannerSeesAllRecords() throws Exception {
    Connection conn = util.getConnection();
    try (Table table = conn.getTable(TABLENAME)) {
      Scan s = new Scan();
      s.addFamily(FAMILY);
      s.setMaxResultSize(-1);
      s.setBatch(-1);
      s.setCaching(500);
      Entry<Long,Long> entry = sumTable(table.getScanner(s));
      long rowsObserved = entry.getKey();
      long entriesObserved = entry.getValue();

      // Verify that we see 1M rows and 10M cells
      assertEquals(NUM_ROWS, rowsObserved);
      assertEquals(NUM_ROWS * NUM_COLS, entriesObserved);
    }
  }

  @Test
  public void testSmallScannerSeesAllRecords() throws Exception {
    Connection conn = util.getConnection();
    try (Table table = conn.getTable(TABLENAME)) {
      Scan s = new Scan();
      s.setSmall(true);
      s.addFamily(FAMILY);
      s.setMaxResultSize(-1);
      s.setBatch(-1);
      s.setCaching(500);
      Entry<Long,Long> entry = sumTable(table.getScanner(s));
      long rowsObserved = entry.getKey();
      long entriesObserved = entry.getValue();

      // Verify that we see 1M rows and 10M cells
      assertEquals(NUM_ROWS, rowsObserved);
      assertEquals(NUM_ROWS * NUM_COLS, entriesObserved);
    }
  }

  /**
   * Count the number of rows and the number of entries from a scanner
   *
   * @param scanner
   *          The Scanner
   * @return An entry where the first item is rows observed and the second is entries observed.
   */
  private Entry<Long,Long> sumTable(ResultScanner scanner) {
    long rowsObserved = 0l;
    long entriesObserved = 0l;

    // Read all the records in the table
    for (Result result : scanner) {
      rowsObserved++;
      while (result.advance()) {
        entriesObserved++;
      }
    }
    return Maps.immutableEntry(rowsObserved,entriesObserved);
  }
}
