package hbase;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.CategoryBasedTimeout;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.DoNotRetryIOException;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Increment;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.coprocessor.CoprocessorHost;
import org.apache.hadoop.hbase.coprocessor.MultiRowMutationEndpoint;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestIncrementsFromClientSide {
  protected final static HBaseUtility util = new HBaseUtility();
  private static byte [] ROW = Bytes.toBytes("testRow");
  private static byte [] FAMILY = Bytes.toBytes("testFamily");

  private static Admin admin;

  @Rule public TestName name = new TestName();
  @Rule public final TestRule timeout = CategoryBasedTimeout.builder().withTimeout(this.getClass()).
      withLookingForStuckThread(true).build();

  @BeforeClass
  public static void beforeClass() throws Exception {
    Configuration conf = util.getConfiguration();
    conf.setStrings(CoprocessorHost.REGION_COPROCESSOR_CONF_KEY,
        MultiRowMutationEndpoint.class.getName());
    conf.setBoolean("hbase.table.sanity.checks", true); // enable for below tests
    admin = util.getAdmin();
  }

  @Test
  public void testIncrementWithDeletes() throws Exception {
    final TableName TABLENAME =
        TableName.valueOf(filterStringSoTableNameSafe(this.name.getMethodName()));
    Table ht = util.createTable(TABLENAME, FAMILY);
    final byte[] COLUMN = Bytes.toBytes("column");

    ht.incrementColumnValue(ROW, FAMILY, COLUMN, 5);
    admin.flush(TABLENAME);


    Delete del = new Delete(ROW);
    ht.delete(del);

    ht.incrementColumnValue(ROW, FAMILY, COLUMN, 5);

    Get get = new Get(ROW);
    Result r = ht.get(get);
    assertEquals(1, r.size());
    assertEquals(5, Bytes.toLong(r.getValue(FAMILY, COLUMN)));
  }

  @Test
  public void testIncrementingInvalidValue() throws Exception {
    final TableName TABLENAME =
        TableName.valueOf(filterStringSoTableNameSafe(this.name.getMethodName()));
    Table ht = util.createTable(TABLENAME, FAMILY);
    final byte[] COLUMN = Bytes.toBytes("column");
    Put p = new Put(ROW);
    // write an integer here (not a Long)
    p.add(FAMILY, COLUMN, Bytes.toBytes(5));
    ht.put(p);
    try {
      ht.incrementColumnValue(ROW, FAMILY, COLUMN, 5);
      fail("Should have thrown DoNotRetryIOException");
    } catch (DoNotRetryIOException iox) {
      // success
    }
    Increment inc = new Increment(ROW);
    inc.addColumn(FAMILY, COLUMN, 5);
    try {
      ht.increment(inc);
      fail("Should have thrown DoNotRetryIOException");
    } catch (DoNotRetryIOException iox) {
      // Expected
    }
  }

  @Test
  public void testIncrementInvalidArguments() throws Exception {
    final TableName TABLENAME =
        TableName.valueOf(filterStringSoTableNameSafe(this.name.getMethodName()));
    Table ht = util.createTable(TABLENAME, FAMILY);
    final byte[] COLUMN = Bytes.toBytes("column");
    try {
      // try null row
      ht.incrementColumnValue(null, FAMILY, COLUMN, 5);
      fail("Should have thrown IOException");
    } catch (IOException iox) {
      // Expected
    }
    try {
      // try null family
      ht.incrementColumnValue(ROW, null, COLUMN, 5);
      fail("Should have thrown IOException");
    } catch (IOException iox) {
      // Expected
    }
    try {
      // try null qualifier
      ht.incrementColumnValue(ROW, FAMILY, null, 5);
      fail("Should have thrown IOException");
    } catch (IOException iox) {
      // Expected
    }
    // try null row
    try {
      Increment incNoRow = new Increment((byte [])null);
      incNoRow.addColumn(FAMILY, COLUMN, 5);
      fail("Should have thrown IllegalArgumentException");
    } catch (IllegalArgumentException iax) {
      // Expected
    } catch (NullPointerException npe) {
      // Expected
    }
    // try null family
    try {
      Increment incNoFamily = new Increment(ROW);
      incNoFamily.addColumn(null, COLUMN, 5);
      fail("Should have thrown IllegalArgumentException");
    } catch (IllegalArgumentException iax) {
      // Expected
    }
    // try null qualifier
    try {
      Increment incNoQualifier = new Increment(ROW);
      incNoQualifier.addColumn(FAMILY, null, 5);
      fail("Should have thrown IllegalArgumentException");
    } catch (IllegalArgumentException iax) {
      // Expected
    }
  }

  @Test
  public void testIncrementOutOfOrder() throws Exception {
    final TableName TABLENAME =
        TableName.valueOf(filterStringSoTableNameSafe(this.name.getMethodName()));
    Table ht = util.createTable(TABLENAME, FAMILY);

    byte [][] QUALIFIERS = new byte [][] {
      Bytes.toBytes("B"), Bytes.toBytes("A"), Bytes.toBytes("C")
    };

    Increment inc = new Increment(ROW);
    for (int i=0; i<QUALIFIERS.length; i++) {
      inc.addColumn(FAMILY, QUALIFIERS[i], 1);
    }
    ht.increment(inc);

    // Verify expected results
    Get get = new Get(ROW);
    Result r = ht.get(get);
    Cell[] kvs = r.rawCells();
    assertEquals(3, kvs.length);
    assertIncrementKey(kvs[0], ROW, FAMILY, QUALIFIERS[1], 1);
    assertIncrementKey(kvs[1], ROW, FAMILY, QUALIFIERS[0], 1);
    assertIncrementKey(kvs[2], ROW, FAMILY, QUALIFIERS[2], 1);

    // Now try multiple columns again
    inc = new Increment(ROW);
    for (int i=0; i<QUALIFIERS.length; i++) {
      inc.addColumn(FAMILY, QUALIFIERS[i], 1);
    }
    ht.increment(inc);

    // Verify
    r = ht.get(get);
    kvs = r.rawCells();
    assertEquals(3, kvs.length);
    assertIncrementKey(kvs[0], ROW, FAMILY, QUALIFIERS[1], 2);
    assertIncrementKey(kvs[1], ROW, FAMILY, QUALIFIERS[0], 2);
    assertIncrementKey(kvs[2], ROW, FAMILY, QUALIFIERS[2], 2);
  }

  @Test
  public void testIncrementOnSameColumn() throws Exception {
    final byte[] TABLENAME = Bytes.toBytes(filterStringSoTableNameSafe(this.name.getMethodName()));
    HTable ht = util.createTable(TABLENAME, FAMILY);

    byte[][] QUALIFIERS =
        new byte[][] { Bytes.toBytes("A"), Bytes.toBytes("B"), Bytes.toBytes("C") };

    Increment inc = new Increment(ROW);
    for (int i = 0; i < QUALIFIERS.length; i++) {
      inc.addColumn(FAMILY, QUALIFIERS[i], 1);
      inc.addColumn(FAMILY, QUALIFIERS[i], 1);
    }
    ht.increment(inc);

    // Verify expected results
    Get get = new Get(ROW);
    Result r = ht.get(get);
    Cell[] kvs = r.rawCells();
    assertEquals(3, kvs.length);
    assertIncrementKey(kvs[0], ROW, FAMILY, QUALIFIERS[0], 1);
    assertIncrementKey(kvs[1], ROW, FAMILY, QUALIFIERS[1], 1);
    assertIncrementKey(kvs[2], ROW, FAMILY, QUALIFIERS[2], 1);

    // Now try multiple columns again
    inc = new Increment(ROW);
    for (int i = 0; i < QUALIFIERS.length; i++) {
      inc.addColumn(FAMILY, QUALIFIERS[i], 1);
      inc.addColumn(FAMILY, QUALIFIERS[i], 1);
    }
    ht.increment(inc);

    // Verify
    r = ht.get(get);
    kvs = r.rawCells();
    assertEquals(3, kvs.length);
    assertIncrementKey(kvs[0], ROW, FAMILY, QUALIFIERS[0], 2);
    assertIncrementKey(kvs[1], ROW, FAMILY, QUALIFIERS[1], 2);
    assertIncrementKey(kvs[2], ROW, FAMILY, QUALIFIERS[2], 2);

    ht.close();
  }

  @Test
  public void testIncrement() throws Exception {
    final TableName TABLENAME =
        TableName.valueOf(filterStringSoTableNameSafe(this.name.getMethodName()));
    Table ht = util.createTable(TABLENAME, FAMILY);

    byte [][] ROWS = new byte [][] {
        Bytes.toBytes("a"), Bytes.toBytes("b"), Bytes.toBytes("c"),
        Bytes.toBytes("d"), Bytes.toBytes("e"), Bytes.toBytes("f"),
        Bytes.toBytes("g"), Bytes.toBytes("h"), Bytes.toBytes("i")
    };
    byte [][] QUALIFIERS = new byte [][] {
        Bytes.toBytes("a"), Bytes.toBytes("b"), Bytes.toBytes("c"),
        Bytes.toBytes("d"), Bytes.toBytes("e"), Bytes.toBytes("f"),
        Bytes.toBytes("g"), Bytes.toBytes("h"), Bytes.toBytes("i")
    };

    ht.incrementColumnValue(ROW, FAMILY, QUALIFIERS[0], 1);
    ht.incrementColumnValue(ROW, FAMILY, QUALIFIERS[1], 2);
    ht.incrementColumnValue(ROW, FAMILY, QUALIFIERS[2], 3);
    ht.incrementColumnValue(ROW, FAMILY, QUALIFIERS[3], 4);

    // Now increment things incremented with old and do some new
    Increment inc = new Increment(ROW);
    inc.addColumn(FAMILY, QUALIFIERS[1], 1);
    inc.addColumn(FAMILY, QUALIFIERS[3], 1);
    inc.addColumn(FAMILY, QUALIFIERS[4], 1);
    ht.increment(inc);

    // Verify expected results
    Get get = new Get(ROW);
    Result r = ht.get(get);
    Cell[] kvs = r.rawCells();
    assertEquals(5, kvs.length);
    assertIncrementKey(kvs[0], ROW, FAMILY, QUALIFIERS[0], 1);
    assertIncrementKey(kvs[1], ROW, FAMILY, QUALIFIERS[1], 3);
    assertIncrementKey(kvs[2], ROW, FAMILY, QUALIFIERS[2], 3);
    assertIncrementKey(kvs[3], ROW, FAMILY, QUALIFIERS[3], 5);
    assertIncrementKey(kvs[4], ROW, FAMILY, QUALIFIERS[4], 1);

    // Now try multiple columns by different amounts
    inc = new Increment(ROWS[0]);
    for (int i=0;i<QUALIFIERS.length;i++) {
      inc.addColumn(FAMILY, QUALIFIERS[i], i+1);
    }
    ht.increment(inc);
    // Verify
    get = new Get(ROWS[0]);
    r = ht.get(get);
    kvs = r.rawCells();
    assertEquals(QUALIFIERS.length, kvs.length);
    for (int i=0;i<QUALIFIERS.length;i++) {
      assertIncrementKey(kvs[i], ROWS[0], FAMILY, QUALIFIERS[i], i+1);
    }

    // Re-increment them
    inc = new Increment(ROWS[0]);
    for (int i=0;i<QUALIFIERS.length;i++) {
      inc.addColumn(FAMILY, QUALIFIERS[i], i+1);
    }
    ht.increment(inc);
    // Verify
    r = ht.get(get);
    kvs = r.rawCells();
    assertEquals(QUALIFIERS.length, kvs.length);
    for (int i=0;i<QUALIFIERS.length;i++) {
      assertIncrementKey(kvs[i], ROWS[0], FAMILY, QUALIFIERS[i], 2*(i+1));
    }

    // Verify that an Increment of an amount of zero, returns current count; i.e. same as for above
    // test, that is: 2 * (i + 1).
    inc = new Increment(ROWS[0]);
    for (int i = 0; i < QUALIFIERS.length; i++) {
      inc.addColumn(FAMILY, QUALIFIERS[i], 0);
    }
    ht.increment(inc);
    r = ht.get(get);
    kvs = r.rawCells();
    assertEquals(QUALIFIERS.length, kvs.length);
    for (int i = 0; i < QUALIFIERS.length; i++) {
      assertIncrementKey(kvs[i], ROWS[0], FAMILY, QUALIFIERS[i], 2*(i+1));
    }
  }

  static void assertIncrementKey(Cell key, byte [] row, byte [] family,
                                 byte [] qualifier, long value) throws Exception {
    assertTrue("Expected row [" + Bytes.toString(row) + "] " +
                    "Got row [" + Bytes.toString(CellUtil.cloneRow(key)) +"]",
            equals(row, CellUtil.cloneRow(key)));
    assertTrue("Expected family [" + Bytes.toString(family) + "] " +
                    "Got family [" + Bytes.toString(CellUtil.cloneFamily(key)) + "]",
            equals(family, CellUtil.cloneFamily(key)));
    assertTrue("Expected qualifier [" + Bytes.toString(qualifier) + "] " +
                    "Got qualifier [" + Bytes.toString(CellUtil.cloneQualifier(key)) + "]",
            equals(qualifier, CellUtil.cloneQualifier(key)));
    assertTrue("Expected value [" + value + "] " +
                    "Got value [" + Bytes.toLong(CellUtil.cloneValue(key)) + "]",
            Bytes.toLong(CellUtil.cloneValue(key)) == value);
  }

  static boolean equals(byte [] left, byte [] right) {
    if (left == null && right == null) return true;
    if (left == null && right.length == 0) return true;
    if (right == null && left.length == 0) return true;
    return Bytes.equals(left, right);
  }

  public static String filterStringSoTableNameSafe(final String str) {
    return str.replaceAll("\\[fast\\=(.*)\\]", ".FAST.is.$1");
  }
}
