package hbase;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.MetaTableAccessor;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.MetaScanner;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.Threads;
import org.apache.hadoop.util.StringUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.NavigableMap;
import java.util.Random;

public class TestMetaScanner {
  private final static HBaseUtility util = new HBaseUtility();
  private Connection connection;

  public void setUp() throws Exception {
    this.connection = util.getConnection();
  }

  @Test
  public void testMetaScanner() throws Exception {
    System.out.println(("Starting testMetaScanner"));

    setUp();
    final TableName TABLENAME = TableName.valueOf("testMetaScanner");
    final byte[] FAMILY = Bytes.toBytes("family");
    final byte[][] SPLIT_KEYS =
        new byte[][] { Bytes.toBytes("region_a"), Bytes.toBytes("region_b") };

    util.createTable(TABLENAME, FAMILY, SPLIT_KEYS);
    HTable table = (HTable) connection.getTable(TABLENAME);
    // Make sure all the regions are deployed
    util.countRows(table);

    MetaScanner.MetaScannerVisitor visitor = new MetaScanner.MetaScannerVisitor() {
      @Override
      public boolean processRow(Result result) throws IOException {
        System.out.println(result);
        return true;
      }

      @Override
      public void close() throws IOException {
      }
    };

    // Scanning the entire table should give us three rows
    MetaScanner.metaScan(connection, visitor, TABLENAME);

    MetaScanner.metaScan(connection, visitor, TABLENAME, HConstants.EMPTY_BYTE_ARRAY, 1000);

    // Scanning the table starting in the middle should give us two rows:
    // region_a and region_b
    MetaScanner.metaScan(connection, visitor, TABLENAME, Bytes.toBytes("region_ac"), 1000);

    // Scanning with a limit of 1 should only give us one row
    MetaScanner.metaScan(connection, visitor, TABLENAME, Bytes.toBytes("region_ac"), 1);
    table.close();
  }

  @Test
  public void testConcurrentMetaScannerAndCatalogJanitor() throws Throwable {
    /* TEST PLAN: start with only one region in a table. Have a splitter
     * thread  and metascanner threads that continously scan the meta table for regions.
     * CatalogJanitor from master will run frequently to clean things up
     */
    util.getConfiguration().setLong("hbase.catalogjanitor.interval", 500);
    setUp();

    final long runtime = 30 * 1000; //30 sec
    System.out.println(("Starting testConcurrentMetaScannerAndCatalogJanitor"));
    final TableName TABLENAME =
        TableName.valueOf("testConcurrentMetaScannerAndCatalogJanitor");
    final byte[] FAMILY = Bytes.toBytes("family");
    util.createTable(TABLENAME, FAMILY);

    class RegionMetaSplitter extends StoppableImplementation implements Runnable {
      Random random = new Random();
      Throwable ex = null;
      @Override
      public void run() {
        while (!isStopped()) {
          try {
            List<HRegionInfo> regions = MetaScanner.listAllRegions(util.getConfiguration(),
                connection, false);

            //select a random region
            HRegionInfo parent = regions.get(random.nextInt(regions.size()));
            if (parent == null || !TABLENAME.equals(parent.getTable())) {
              continue;
            }

            long startKey = 0, endKey = Long.MAX_VALUE;
            byte[] start = parent.getStartKey();
            byte[] end = parent.getEndKey();
            if (!Bytes.equals(HConstants.EMPTY_START_ROW, parent.getStartKey())) {
              startKey = Bytes.toLong(parent.getStartKey());
            }
            if (!Bytes.equals(HConstants.EMPTY_END_ROW, parent.getEndKey())) {
              endKey = Bytes.toLong(parent.getEndKey());
            }
            if (startKey == endKey) {
              continue;
            }

            long midKey = BigDecimal.valueOf(startKey).add(BigDecimal.valueOf(endKey))
                .divideToIntegralValue(BigDecimal.valueOf(2)).longValue();

            HRegionInfo splita = new HRegionInfo(TABLENAME,
              start,
              Bytes.toBytes(midKey));
            HRegionInfo splitb = new HRegionInfo(TABLENAME,
              Bytes.toBytes(midKey),
              end);

            MetaTableAccessor.splitRegion(connection,
              parent, splita, splitb, ServerName.valueOf("fooserver", 1, 0), 1);

            Threads.sleep(random.nextInt(200));
          } catch (Throwable e) {
            ex = e;
            Assert.fail(StringUtils.stringifyException(e));
          }
        }
      }
      void rethrowExceptionIfAny() throws Throwable {
        if (ex != null) { throw ex; }
      }
    }

    class MetaScannerVerifier extends StoppableImplementation implements Runnable {
      Random random = new Random();
      Throwable ex = null;
      @Override
      public void run() {
         while(!isStopped()) {
           try {
            NavigableMap<HRegionInfo, ServerName> regions =
                MetaScanner.allTableRegions(connection, TABLENAME);

             System.out.println(("-------"));
            byte[] lastEndKey = HConstants.EMPTY_START_ROW;
            for (HRegionInfo hri: regions.navigableKeySet()) {
              long startKey = 0, endKey = Long.MAX_VALUE;
              if (!Bytes.equals(HConstants.EMPTY_START_ROW, hri.getStartKey())) {
                startKey = Bytes.toLong(hri.getStartKey());
              }
              if (!Bytes.equals(HConstants.EMPTY_END_ROW, hri.getEndKey())) {
                endKey = Bytes.toLong(hri.getEndKey());
              }
              System.out.println(("start:" + startKey + " end:" + endKey + " hri:" + hri));
              Assert.assertTrue("lastEndKey=" + Bytes.toString(lastEndKey) + ", startKey=" +
                Bytes.toString(hri.getStartKey()), Bytes.equals(lastEndKey, hri.getStartKey()));
              lastEndKey = hri.getEndKey();
            }
            Assert.assertTrue(Bytes.equals(lastEndKey, HConstants.EMPTY_END_ROW));
             System.out.println(("-------"));
            Threads.sleep(10 + random.nextInt(50));
          } catch (Throwable e) {
            ex = e;
            Assert.fail(StringUtils.stringifyException(e));
          }
         }
      }
      void rethrowExceptionIfAny() throws Throwable {
        if (ex != null) { throw ex; }
      }
    }

    RegionMetaSplitter regionMetaSplitter = new RegionMetaSplitter();
    MetaScannerVerifier metaScannerVerifier = new MetaScannerVerifier();

    Thread regionMetaSplitterThread = new Thread(regionMetaSplitter);
    Thread metaScannerVerifierThread = new Thread(metaScannerVerifier);

    regionMetaSplitterThread.start();
    metaScannerVerifierThread.start();

    Threads.sleep(runtime);

    regionMetaSplitter.stop("test finished");
    metaScannerVerifier.stop("test finished");

    regionMetaSplitterThread.join();
    metaScannerVerifierThread.join();

    regionMetaSplitter.rethrowExceptionIfAny();
    metaScannerVerifier.rethrowExceptionIfAny();
  }
}
