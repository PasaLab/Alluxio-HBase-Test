package hbase;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Durability;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TestMultipleTimestamps {
  private final static HBaseUtility util = new HBaseUtility();

  @Test
  public void testReseeksWithOneColumnMiltipleTimestamp() throws IOException {
    TableName TABLE =
        TableName.valueOf("testReseeksWithOne" +
            "ColumnMiltipleTimestamps");
    byte [] FAMILY = Bytes.toBytes("event_log");
    byte [][] FAMILIES = new byte[][] { FAMILY };

    // create table; set versions to max...
    Table ht = util.createTable(TABLE, FAMILIES, Integer.MAX_VALUE);

    Integer[] putRows = new Integer[] {1, 3, 5, 7};
    Integer[] putColumns = new Integer[] { 1, 3, 5};
    Long[] putTimestamps = new Long[] {1L, 2L, 3L, 4L, 5L};

    Integer[] scanRows = new Integer[] {3, 5};
    Integer[] scanColumns = new Integer[] {3};
    Long[] scanTimestamps = new Long[] {3L, 4L};
    int scanMaxVersions = 2;

    put(ht, FAMILY, putRows, putColumns, putTimestamps);

    util.getAdmin().flush(TABLE);

    ResultScanner scanner = scan(ht, FAMILY, scanRows, scanColumns,
        scanTimestamps, scanMaxVersions);

    Cell[] kvs;

    kvs = scanner.next().rawCells();
    assertEquals(2, kvs.length);
    checkOneCell(kvs[0], FAMILY, 3, 3, 4);
    checkOneCell(kvs[1], FAMILY, 3, 3, 3);
    kvs = scanner.next().rawCells();
    assertEquals(2, kvs.length);
    checkOneCell(kvs[0], FAMILY, 5, 3, 4);
    checkOneCell(kvs[1], FAMILY, 5, 3, 3);

    ht.close();
  }

  @Test
  public void testReseeksWithMultipleColumnOneTimestamp() throws IOException {
    TableName TABLE =
        TableName.valueOf("testReseeksWithMultiple" +
            "ColumnOneTimestamps");
    byte [] FAMILY = Bytes.toBytes("event_log");
    byte [][] FAMILIES = new byte[][] { FAMILY };

    // create table; set versions to max...
    Table ht = util.createTable(TABLE, FAMILIES, Integer.MAX_VALUE);

    Integer[] putRows = new Integer[] {1, 3, 5, 7};
    Integer[] putColumns = new Integer[] { 1, 3, 5};
    Long[] putTimestamps = new Long[] {1L, 2L, 3L, 4L, 5L};

    Integer[] scanRows = new Integer[] {3, 5};
    Integer[] scanColumns = new Integer[] {3,4};
    Long[] scanTimestamps = new Long[] {3L};
    int scanMaxVersions = 2;

    put(ht, FAMILY, putRows, putColumns, putTimestamps);

    util.getAdmin().flush(TABLE);

    ResultScanner scanner = scan(ht, FAMILY, scanRows, scanColumns,
        scanTimestamps, scanMaxVersions);

    Cell[] kvs;

    kvs = scanner.next().rawCells();
    assertEquals(1, kvs.length);
    checkOneCell(kvs[0], FAMILY, 3, 3, 3);
    kvs = scanner.next().rawCells();
    assertEquals(1, kvs.length);
    checkOneCell(kvs[0], FAMILY, 5, 3, 3);

    ht.close();
  }

  @Test
  public void testReseeksWithMultipleColumnMultipleTimestamp() throws
  IOException {
    TableName TABLE =
        TableName.valueOf("testReseeksWithMultipleColumnMiltipleTimestamps");
    byte [] FAMILY = Bytes.toBytes("event_log");
    byte [][] FAMILIES = new byte[][] { FAMILY };

    // create table; set versions to max...
    Table ht = util.createTable(TABLE, FAMILIES, Integer.MAX_VALUE);

    Integer[] putRows = new Integer[] {1, 3, 5, 7};
    Integer[] putColumns = new Integer[] { 1, 3, 5};
    Long[] putTimestamps = new Long[] {1L, 2L, 3L, 4L, 5L};

    Integer[] scanRows = new Integer[] {5, 7};
    Integer[] scanColumns = new Integer[] {3, 4, 5};
    Long[] scanTimestamps = new Long[] {2l, 3L};
    int scanMaxVersions = 2;

    put(ht, FAMILY, putRows, putColumns, putTimestamps);

    util.getAdmin().flush(TABLE);
    Scan scan = new Scan();
    scan.setMaxVersions(10);
    ResultScanner scanner = ht.getScanner(scan);
    while (true) {
      Result r = scanner.next();
      if (r == null) break;
    }
    scanner = scan(ht, FAMILY, scanRows, scanColumns, scanTimestamps, scanMaxVersions);

    Cell[] kvs;

    // This looks like wrong answer.  Should be 2.  Even then we are returning wrong result,
    // timestamps that are 3 whereas should be 2 since min is inclusive.
    kvs = scanner.next().rawCells();
    assertEquals(4, kvs.length);
    checkOneCell(kvs[0], FAMILY, 5, 3, 3);
    checkOneCell(kvs[1], FAMILY, 5, 3, 2);
    checkOneCell(kvs[2], FAMILY, 5, 5, 3);
    checkOneCell(kvs[3], FAMILY, 5, 5, 2);
    kvs = scanner.next().rawCells();
    assertEquals(4, kvs.length);
    checkOneCell(kvs[0], FAMILY, 7, 3, 3);
    checkOneCell(kvs[1], FAMILY, 7, 3, 2);
    checkOneCell(kvs[2], FAMILY, 7, 5, 3);
    checkOneCell(kvs[3], FAMILY, 7, 5, 2);

    ht.close();
  }

  @Test
  public void testReseeksWithMultipleFiles() throws IOException {
    TableName TABLE =
        TableName.valueOf("testReseeksWithMultipleFiles");
    byte [] FAMILY = Bytes.toBytes("event_log");
    byte [][] FAMILIES = new byte[][] { FAMILY };

    // create table; set versions to max...
    Table ht = util.createTable(TABLE, FAMILIES, Integer.MAX_VALUE);

    Integer[] putRows1 = new Integer[] {1, 2, 3};
    Integer[] putColumns1 = new Integer[] { 2, 5, 6};
    Long[] putTimestamps1 = new Long[] {1L, 2L, 5L};

    Integer[] putRows2 = new Integer[] {6, 7};
    Integer[] putColumns2 = new Integer[] {3, 6};
    Long[] putTimestamps2 = new Long[] {4L, 5L};

    Integer[] putRows3 = new Integer[] {2, 3, 5};
    Integer[] putColumns3 = new Integer[] {1, 2, 3};
    Long[] putTimestamps3 = new Long[] {4L,8L};


    Integer[] scanRows = new Integer[] {3, 5, 7};
    Integer[] scanColumns = new Integer[] {3, 4, 5};
    Long[] scanTimestamps = new Long[] {2l, 4L};
    int scanMaxVersions = 5;

    put(ht, FAMILY, putRows1, putColumns1, putTimestamps1);
    put(ht, FAMILY, putRows2, putColumns2, putTimestamps2);
    put(ht, FAMILY, putRows3, putColumns3, putTimestamps3);

    ResultScanner scanner = scan(ht, FAMILY, scanRows, scanColumns,
        scanTimestamps, scanMaxVersions);

    Cell[] kvs;

    kvs = scanner.next().rawCells();
    assertEquals(2, kvs.length);
    checkOneCell(kvs[0], FAMILY, 3, 3, 4);
    checkOneCell(kvs[1], FAMILY, 3, 5, 2);

    kvs = scanner.next().rawCells();
    assertEquals(1, kvs.length);
    checkOneCell(kvs[0], FAMILY, 5, 3, 4);

    kvs = scanner.next().rawCells();
    assertEquals(1, kvs.length);
    checkOneCell(kvs[0], FAMILY, 6, 3, 4);

    kvs = scanner.next().rawCells();
    assertEquals(1, kvs.length);
    checkOneCell(kvs[0], FAMILY, 7, 3, 4);

    ht.close();
  }

  @Test
  public void testWithVersionDeletes() throws Exception {
    // first test from memstore (without flushing).
    testWithVersionDeletes(false);

    // run same test against HFiles (by forcing a flush).
    testWithVersionDeletes(true);
  }

  public void testWithVersionDeletes(boolean flushTables) throws IOException {
    TableName TABLE =
        TableName.valueOf("testWithVersionDeletes_" + (flushTables ?
            "flush" : "noflush"));
    byte [] FAMILY = Bytes.toBytes("event_log");
    byte [][] FAMILIES = new byte[][] { FAMILY };

    // create table; set versions to max...
    Table ht = util.createTable(TABLE, FAMILIES, Integer.MAX_VALUE);

    // For row:0, col:0: insert versions 1 through 5.
    putNVersions(ht, FAMILY, 0, 0, 1, 5);

    if (flushTables) {
      util.getAdmin().flush(TABLE);
    }

    // delete version 4.
    deleteOneVersion(ht, FAMILY, 0, 0, 4);

    // request a bunch of versions including the deleted version. We should
    // only get back entries for the versions that exist.
    Cell kvs[] = getNVersions(ht, FAMILY, 0, 0,
        Arrays.asList(2L, 3L, 4L, 5L));
    assertEquals(3, kvs.length);
    checkOneCell(kvs[0], FAMILY, 0, 0, 5);
    checkOneCell(kvs[1], FAMILY, 0, 0, 3);
    checkOneCell(kvs[2], FAMILY, 0, 0, 2);

    ht.close();
  }

  @Test
  public void testWithMultipleVersionDeletes() throws IOException {
    TableName TABLE =
        TableName.valueOf("testWithMultipleVersionDeletes");
    byte [] FAMILY = Bytes.toBytes("event_log");
    byte [][] FAMILIES = new byte[][] { FAMILY };

    // create table; set versions to max...
    Table ht = util.createTable(TABLE, FAMILIES, Integer.MAX_VALUE);

    // For row:0, col:0: insert versions 1 through 5.
    putNVersions(ht, FAMILY, 0, 0, 1, 5);

    util.getAdmin().flush(TABLE);

    // delete all versions before 4.
    deleteAllVersionsBefore(ht, FAMILY, 0, 0, 4);

    // request a bunch of versions including the deleted version. We should
    // only get back entries for the versions that exist.
    Cell kvs[] = getNVersions(ht, FAMILY, 0, 0, Arrays.asList(2L, 3L));
    assertEquals(0, kvs.length);

    ht.close();
  }

  @Test
  public void testWithColumnDeletes() throws IOException {
    TableName TABLE =
        TableName.valueOf("testWithColumnDeletes");
    byte [] FAMILY = Bytes.toBytes("event_log");
    byte [][] FAMILIES = new byte[][] { FAMILY };

    // create table; set versions to max...
    Table ht = util.createTable(TABLE, FAMILIES, Integer.MAX_VALUE);

    // For row:0, col:0: insert versions 1 through 5.
    putNVersions(ht, FAMILY, 0, 0, 1, 5);

    util.getAdmin().flush(TABLE);

    // delete all versions before 4.
    deleteColumn(ht, FAMILY, 0, 0);

    // request a bunch of versions including the deleted version. We should
    // only get back entries for the versions that exist.
    Cell kvs[] = getNVersions(ht, FAMILY, 0, 0, Arrays.asList(2L, 3L));
    assertEquals(0, kvs.length);

    ht.close();
  }

  @Test
  public void testWithFamilyDeletes() throws IOException {
    TableName TABLE =
        TableName.valueOf("testWithFamilyDeletes");
    byte [] FAMILY = Bytes.toBytes("event_log");
    byte [][] FAMILIES = new byte[][] { FAMILY };

    // create table; set versions to max...
    Table ht = util.createTable(TABLE, FAMILIES, Integer.MAX_VALUE);

    // For row:0, col:0: insert versions 1 through 5.
    putNVersions(ht, FAMILY, 0, 0, 1, 5);

    util.getAdmin().flush(TABLE);

    // delete all versions before 4.
    deleteFamily(ht, FAMILY, 0);

    // request a bunch of versions including the deleted version. We should
    // only get back entries for the versions that exist.
    Cell kvs[] = getNVersions(ht, FAMILY, 0, 0, Arrays.asList(2L, 3L));
    assertEquals(0, kvs.length);

    ht.close();
  }

  /**
   * Assert that the passed in KeyValue has expected contents for the
   * specified row, column & timestamp.
   */
  private void checkOneCell(Cell kv, byte[] cf,
                            int rowIdx, int colIdx, long ts) {

    String ctx = "rowIdx=" + rowIdx + "; colIdx=" + colIdx + "; ts=" + ts;

    assertEquals("Row mismatch which checking: " + ctx,
        "row:"+ rowIdx, Bytes.toString(CellUtil.cloneRow(kv)));

    assertEquals("ColumnFamily mismatch while checking: " + ctx,
        Bytes.toString(cf), Bytes.toString(CellUtil.cloneFamily(kv)));

    assertEquals("Column qualifier mismatch while checking: " + ctx,
        "column:" + colIdx,
        Bytes.toString(CellUtil.cloneQualifier(kv)));

    assertEquals("Timestamp mismatch while checking: " + ctx,
        ts, kv.getTimestamp());

    assertEquals("Value mismatch while checking: " + ctx,
        "value-version-" + ts, Bytes.toString(CellUtil.cloneValue(kv)));
  }

  /**
   * Uses the TimestampFilter on a Get to request a specified list of
   * versions for the row/column specified by rowIdx & colIdx.
   */
  private  Cell[] getNVersions(Table ht, byte[] cf, int rowIdx,
                               int colIdx, List<Long> versions)
  throws IOException {
    byte row[] = Bytes.toBytes("row:" + rowIdx);
    byte column[] = Bytes.toBytes("column:" + colIdx);
    Get get = new Get(row);
    get.addColumn(cf, column);
    get.setMaxVersions();
    get.setTimeRange(Collections.min(versions), Collections.max(versions)+1);
    Result result = ht.get(get);

    return result.rawCells();
  }

  private ResultScanner scan(Table ht, byte[] cf,
                             Integer[] rowIndexes, Integer[] columnIndexes,
                             Long[] versions, int maxVersions)
  throws IOException {
    Arrays.asList(rowIndexes);
    byte startRow[] = Bytes.toBytes("row:" +
        Collections.min( Arrays.asList(rowIndexes)));
    byte endRow[] = Bytes.toBytes("row:" +
        Collections.max( Arrays.asList(rowIndexes))+1);
    Scan scan = new Scan(startRow, endRow);
    for (Integer colIdx: columnIndexes) {
      byte column[] = Bytes.toBytes("column:" + colIdx);
      scan.addColumn(cf, column);
    }
    scan.setMaxVersions(maxVersions);
    scan.setTimeRange(Collections.min(Arrays.asList(versions)),
        Collections.max(Arrays.asList(versions))+1);
    ResultScanner scanner = ht.getScanner(scan);
    return scanner;
  }

  private void put(Table ht, byte[] cf, Integer[] rowIndexes,
                   Integer[] columnIndexes, Long[] versions)
  throws IOException {
    for (int rowIdx: rowIndexes) {
      byte row[] = Bytes.toBytes("row:" + rowIdx);
      Put put = new Put(row);
      put.setDurability(Durability.SKIP_WAL);
      for(int colIdx: columnIndexes) {
        byte column[] = Bytes.toBytes("column:" + colIdx);
        for (long version: versions) {
          put.addColumn(cf, column, version, Bytes.toBytes("value-version-" +
              version));
        }
      }
      ht.put(put);
    }
  }

  /**
   * Insert in specific row/column versions with timestamps
   * versionStart..versionEnd.
   */
  private void putNVersions(Table ht, byte[] cf, int rowIdx, int colIdx,
                            long versionStart, long versionEnd)
  throws IOException {
    byte row[] = Bytes.toBytes("row:" + rowIdx);
    byte column[] = Bytes.toBytes("column:" + colIdx);
    Put put = new Put(row);
    put.setDurability(Durability.SKIP_WAL);

    for (long idx = versionStart; idx <= versionEnd; idx++) {
      put.addColumn(cf, column, idx, Bytes.toBytes("value-version-" + idx));
    }

    ht.put(put);
  }

  /**
   * For row/column specified by rowIdx/colIdx, delete the cell
   * corresponding to the specified version.
   */
  private void deleteOneVersion(Table ht, byte[] cf, int rowIdx,
                                int colIdx, long version)
  throws IOException {
    byte row[] = Bytes.toBytes("row:" + rowIdx);
    byte column[] = Bytes.toBytes("column:" + colIdx);
    Delete del = new Delete(row);
    del.addColumn(cf, column, version);
    ht.delete(del);
  }

  /**
   * For row/column specified by rowIdx/colIdx, delete all cells
   * preceeding the specified version.
   */
  private void deleteAllVersionsBefore(Table ht, byte[] cf, int rowIdx,
                                       int colIdx, long version)
  throws IOException {
    byte row[] = Bytes.toBytes("row:" + rowIdx);
    byte column[] = Bytes.toBytes("column:" + colIdx);
    Delete del = new Delete(row);
    del.addColumns(cf, column, version);
    ht.delete(del);
  }

  private void deleteColumn(Table ht, byte[] cf, int rowIdx, int colIdx) throws IOException {
    byte row[] = Bytes.toBytes("row:" + rowIdx);
    byte column[] = Bytes.toBytes("column:" + colIdx);
    Delete del = new Delete(row);
    del.addColumns(cf, column);
    ht.delete(del);
  }

  private void deleteFamily(Table ht, byte[] cf, int rowIdx) throws IOException {
    byte row[] = Bytes.toBytes("row:" + rowIdx);
    Delete del = new Delete(row);
    del.addFamily(cf);
    ht.delete(del);
  }
}


