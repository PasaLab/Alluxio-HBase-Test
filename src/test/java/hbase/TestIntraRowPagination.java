package hbase;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.regionserver.HRegion;
import org.apache.hadoop.hbase.regionserver.RegionScanner;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test scan/get offset and limit settings within one row through HRegion API.
 */
public class TestIntraRowPagination {

  private static HBaseUtility util = new HBaseUtility();

  /**
   * Test from client side for scan with maxResultPerCF set
   *
   * @throws Exception
   */
  @Test
  public void testScanLimitAndOffset() throws Exception {
    byte [][] ROWS = HTestConst.makeNAscii(HTestConst.DEFAULT_ROW_BYTES, 2);
    byte [][] FAMILIES = HTestConst.makeNAscii(HTestConst.DEFAULT_CF_BYTES, 3);
    byte [][] QUALIFIERS = HTestConst.makeNAscii(HTestConst.DEFAULT_QUALIFIER_BYTES, 10);

    HTableDescriptor htd = new HTableDescriptor(TableName.valueOf(HTestConst.DEFAULT_TABLE_BYTES));
    HRegionInfo info = new HRegionInfo(HTestConst.DEFAULT_TABLE, null, null, false);
    for (byte[] family : FAMILIES) {
      HColumnDescriptor hcd = new HColumnDescriptor(family);
      htd.addFamily(hcd);
    }
    FileSystem filesystem = FileSystem.get(util.getConfiguration());
    Path rootdir = filesystem.makeQualified(
            new Path(util.getConfiguration().get(HConstants.HBASE_DIR)));
    filesystem.mkdirs(rootdir);
    HRegion region =
        HRegion.createHRegion(info, rootdir, util.getConfiguration(), htd);
    try {
      Put put;
      Scan scan;
      Result result;
      boolean toLog = true;

      List<Cell> kvListExp = new ArrayList<>();

      int storeOffset = 1;
      int storeLimit = 3;
      for (int r = 0; r < ROWS.length; r++) {
        put = new Put(ROWS[r]);
        for (int c = 0; c < FAMILIES.length; c++) {
          for (int q = 0; q < QUALIFIERS.length; q++) {
            KeyValue kv = new KeyValue(ROWS[r], FAMILIES[c], QUALIFIERS[q], 1,
                HTestConst.DEFAULT_VALUE_BYTES);
            put.add(kv);
            if (storeOffset <= q && q < storeOffset + storeLimit) {
              kvListExp.add(kv);
            }
          }
        }
        region.put(put);
      }

      scan = new Scan();
      scan.setRowOffsetPerColumnFamily(storeOffset);
      scan.setMaxResultsPerColumnFamily(storeLimit);
      RegionScanner scanner = region.getScanner(scan);
      List<Cell> kvListScan = new ArrayList<>();
      List<Cell> results = new ArrayList<>();
      while (scanner.next(results) || !results.isEmpty()) {
        kvListScan.addAll(results);
        results.clear();
      }
      result = Result.create(kvListScan);
      if (kvListExp.size() == 0)
        return;

      int i = 0;
      for (Cell kv : result.rawCells()) {
        if (i >= kvListExp.size()) {
          break;  // we will check the size later
        }

        Cell kvExp = kvListExp.get(i++);
        if (toLog) {
          System.out.println("get kv is: " + kv.toString());
          System.out.println("exp kv is: " + kvExp.toString());
        }
        assertTrue("Not equal", kvExp.equals(kv));
      }

      assertEquals(kvListExp.size(), result.size());
    } finally {
      region.close();
    }
  }

}
