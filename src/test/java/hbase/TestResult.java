package hbase;

import junit.framework.AssertionFailedError;
import junit.framework.TestCase;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellScanner;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.protobuf.generated.ClientProtos;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

public class TestResult extends TestCase {

  static KeyValue[] genKVs(final byte[] row, final byte[] family,
                           final byte[] value,
                           final long timestamp,
                           final int cols) {
    KeyValue[] kvs = new KeyValue[cols];

    for (int i = 0; i < cols ; i++) {
      kvs[i] = new KeyValue(
          row, family, Bytes.toBytes(i),
          timestamp,
          Bytes.add(value, Bytes.toBytes(i)));
    }
    return kvs;
  }

  static final byte [] row = Bytes.toBytes("row");
  static final byte [] family = Bytes.toBytes("family");
  static final byte [] value = Bytes.toBytes("value");

  /**
   * Run some tests to ensure Result acts like a proper CellScanner.
   * @throws IOException
   */
  @Test
  public void testResultAsCellScanner() throws IOException {
    Cell[] cells = genKVs(row, family, value, 1, 10);
    Arrays.sort(cells, KeyValue.COMPARATOR);
    Result r = Result.create(cells);
    assertSame(r, cells);
    // Assert I run over same result multiple times.
    assertSame(r.cellScanner(), cells);
    assertSame(r.cellScanner(), cells);
    // Assert we are not creating new object when doing cellscanner
    assertTrue(r == r.cellScanner());
  }

  private void assertSame(final CellScanner cellScanner, final Cell[] cells) throws IOException {
    int count = 0;
    while (cellScanner.advance()) {
      assertTrue(cells[count].equals(cellScanner.current()));
      count++;
    }
    assertEquals(cells.length, count);
  }

  @Test
  public void testBasicGetColumn() throws Exception {
    KeyValue[] kvs = genKVs(row, family, value, 1, 100);

    Arrays.sort(kvs, KeyValue.COMPARATOR);

    Result r = Result.create(kvs);

    for (int i = 0; i < 100; ++i) {
      final byte[] qf = Bytes.toBytes(i);
      List<Cell> ks = r.getColumnCells(family, qf);
      assertEquals(1, ks.size());
      assertTrue(CellUtil.matchingQualifier(ks.get(0), qf));
      assertEquals(ks.get(0), r.getColumnLatestCell(family, qf));
    }
  }

  @Test
  public void testMultiVersionGetColumn() throws Exception {
    KeyValue[] kvs1 = genKVs(row, family, value, 1, 100);
    KeyValue[] kvs2 = genKVs(row, family, value, 200, 100);

    KeyValue[] kvs = new KeyValue[kvs1.length+kvs2.length];
    System.arraycopy(kvs1, 0, kvs, 0, kvs1.length);
    System.arraycopy(kvs2, 0, kvs, kvs1.length, kvs2.length);

    Arrays.sort(kvs, KeyValue.COMPARATOR);

    Result r = Result.create(kvs);
    for (int i = 0; i < 100; ++i) {
      final byte[] qf = Bytes.toBytes(i);
      List<Cell> ks = r.getColumnCells(family, qf);
      assertEquals(2, ks.size());
      assertTrue(CellUtil.matchingQualifier(ks.get(0), qf));
      assertEquals(200, ks.get(0).getTimestamp());
      assertEquals(ks.get(0), r.getColumnLatestCell(family, qf));
    }
  }

  @Test
  public void testBasicGetValue() throws Exception {
    KeyValue[] kvs = genKVs(row, family, value, 1, 100);

    Arrays.sort(kvs, KeyValue.COMPARATOR);

    Result r = Result.create(kvs);

    for (int i = 0; i < 100; ++i) {
      final byte[] qf = Bytes.toBytes(i);
      assertByteEquals(Bytes.add(value, Bytes.toBytes(i)), r.getValue(family, qf));
      assertTrue(r.containsColumn(family, qf));
    }
  }
  public static void assertByteEquals(byte[] expected,
                                      byte[] actual) {
    if (Bytes.compareTo(expected, actual) != 0) {
      throw new AssertionFailedError("expected:<" +
              Bytes.toString(expected) + "> but was:<" +
              Bytes.toString(actual) + ">");
    }
  }

  @Test
  public void testMultiVersionGetValue() throws Exception {
    KeyValue[] kvs1 = genKVs(row, family, value, 1, 100);
    KeyValue[] kvs2 = genKVs(row, family, value, 200, 100);

    KeyValue[] kvs = new KeyValue[kvs1.length+kvs2.length];
    System.arraycopy(kvs1, 0, kvs, 0, kvs1.length);
    System.arraycopy(kvs2, 0, kvs, kvs1.length, kvs2.length);

    Arrays.sort(kvs, KeyValue.COMPARATOR);

    Result r = Result.create(kvs);
    for (int i = 0; i < 100; ++i) {
      final byte[] qf = Bytes.toBytes(i);
      assertByteEquals(Bytes.add(value, Bytes.toBytes(i)), r.getValue(family, qf));
      assertTrue(r.containsColumn(family, qf));
    }
  }

  @Test
  public void testBasicLoadValue() throws Exception {
    KeyValue[] kvs = genKVs(row, family, value, 1, 100);

    Arrays.sort(kvs, KeyValue.COMPARATOR);

    Result r = Result.create(kvs);
    ByteBuffer loadValueBuffer = ByteBuffer.allocate(1024);

    for (int i = 0; i < 100; ++i) {
      final byte[] qf = Bytes.toBytes(i);
      loadValueBuffer.clear();
      r.loadValue(family, qf, loadValueBuffer);
      loadValueBuffer.flip();
      assertEquals(ByteBuffer.wrap(Bytes.add(value, Bytes.toBytes(i))), loadValueBuffer);
      assertEquals(ByteBuffer.wrap(Bytes.add(value, Bytes.toBytes(i))),
          r.getValueAsByteBuffer(family, qf));
    }
  }

  @Test
  public void testMultiVersionLoadValue() throws Exception {
    KeyValue[] kvs1 = genKVs(row, family, value, 1, 100);
    KeyValue[] kvs2 = genKVs(row, family, value, 200, 100);

    KeyValue[] kvs = new KeyValue[kvs1.length+kvs2.length];
    System.arraycopy(kvs1, 0, kvs, 0, kvs1.length);
    System.arraycopy(kvs2, 0, kvs, kvs1.length, kvs2.length);

    Arrays.sort(kvs, KeyValue.COMPARATOR);

    ByteBuffer loadValueBuffer = ByteBuffer.allocate(1024);

    Result r = Result.create(kvs);
    for (int i = 0; i < 100; ++i) {
      final byte[] qf = Bytes.toBytes(i);
      loadValueBuffer.clear();
      r.loadValue(family, qf, loadValueBuffer);
      loadValueBuffer.flip();
      assertEquals(ByteBuffer.wrap(Bytes.add(value, Bytes.toBytes(i))), loadValueBuffer);
      assertEquals(ByteBuffer.wrap(Bytes.add(value, Bytes.toBytes(i))),
          r.getValueAsByteBuffer(family, qf));
    }
  }

  /**
   * Verify that Result.compareResults(...) behaves correctly.
   */
  @Test
  public void testCompareResults() throws Exception {
    byte [] value1 = Bytes.toBytes("value1");
    byte [] qual = Bytes.toBytes("qual");

    KeyValue kv1 = new KeyValue(row, family, qual, value);
    KeyValue kv2 = new KeyValue(row, family, qual, value1);

    Result r1 = Result.create(new KeyValue[] {kv1});
    Result r2 = Result.create(new KeyValue[] {kv2});
    // no exception thrown
    Result.compareResults(r1, r1);
    try {
      Result.compareResults(r1, r2);
      fail();
    } catch (Exception x) {
      assertTrue(x.getMessage().startsWith("This result was different:"));
    }
  }

  /**
   * Verifies that one can't modify instance of EMPTY_RESULT.
   */
  @Test
  public void testEmptyResultIsReadonly() {
    Result emptyResult = Result.EMPTY_RESULT;
    Result otherResult = new Result();

    try {
      emptyResult.copyFrom(otherResult);
      fail("UnsupportedOperationException should have been thrown!");
    } catch (UnsupportedOperationException ex) {
      //Expected
    }
    try {
      emptyResult.addResults(ClientProtos.RegionLoadStats.getDefaultInstance());
      fail("UnsupportedOperationException should have been thrown!");
    } catch (UnsupportedOperationException ex) {
      //Expected
    }
    try {
      emptyResult.setExists(true);
      fail("UnsupportedOperationException should have been thrown!");
    } catch (UnsupportedOperationException ex) {
      //Expected
    }
  }

  @Test
  public void testReadBenchmark() throws Exception {

    final int n = 5;
    final int m = 100000000;

    StringBuilder valueSB = new StringBuilder();
    for (int i = 0; i < 100; i++) {
      valueSB.append((byte)(Math.random() * 10));
    }

    StringBuilder rowSB = new StringBuilder();
    for (int i = 0; i < 50; i++) {
      rowSB.append((byte)(Math.random() * 10));
    }

    KeyValue[] kvs = genKVs(Bytes.toBytes(rowSB.toString()), family,
        Bytes.toBytes(valueSB.toString()), 1, n);
    Arrays.sort(kvs, KeyValue.COMPARATOR);
    ByteBuffer loadValueBuffer = ByteBuffer.allocate(1024);
    Result r = Result.create(kvs);

    byte[][] qfs = new byte[n][Bytes.SIZEOF_INT];
    for (int i = 0; i < n; ++i) {
      System.arraycopy(qfs[i], 0, Bytes.toBytes(i), 0, Bytes.SIZEOF_INT);
    }

    // warm up
    for (int k = 0; k < 100000; k++) {
      for (int i = 0; i < n; ++i) {
        r.getValue(family, qfs[i]);
        loadValueBuffer.clear();
        r.loadValue(family, qfs[i], loadValueBuffer);
        loadValueBuffer.flip();
      }
    }

    System.gc();
    long start = System.nanoTime();
    for (int k = 0; k < m; k++) {
      for (int i = 0; i < n; ++i) {
        loadValueBuffer.clear();
        r.loadValue(family, qfs[i], loadValueBuffer);
        loadValueBuffer.flip();
      }
    }
    long stop = System.nanoTime();
    System.out.println("loadValue(): " + (stop - start));

    System.gc();
    start = System.nanoTime();
    for (int k = 0; k < m; k++) {
      for (int i = 0; i < n; i++) {
        r.getValue(family, qfs[i]);
      }
    }
    stop = System.nanoTime();
    System.out.println("getValue():  " + (stop - start));
  }
}
