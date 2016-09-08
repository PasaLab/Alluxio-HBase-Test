package hbase;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HRegionLocation;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.KeepDeletedCells;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.Waiter;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ClusterConnection;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Durability;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.filter.CompareFilter;
import org.apache.hadoop.hbase.filter.FilterList;
import org.apache.hadoop.hbase.filter.PrefixFilter;
import org.apache.hadoop.hbase.filter.SingleColumnValueFilter;
import org.apache.hadoop.hbase.protobuf.ProtobufUtil;
import org.apache.hadoop.hbase.protobuf.generated.AdminProtos;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.Pair;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestClientSettings {
    private static HBaseUtility util = new HBaseUtility();
    private static HBaseAdmin admin;
    private static byte[] FAMILY = Bytes.toBytes("testFamily");
    private static Random random = new Random();
    private static byte [] ROW = Bytes.toBytes("testRow");
    private static final byte[] ANOTHERROW = Bytes.toBytes("anotherrow");
    private static byte [] QUALIFIER = Bytes.toBytes("testQualifier");
    private static byte [] VALUE = Bytes.toBytes("testValue");
    private final static byte[] COL_QUAL = Bytes.toBytes("f1");
    private final static byte[] VAL_BYTES = Bytes.toBytes("v1");
    private final static byte[] ROW_BYTES = Bytes.toBytes("r1");

    @BeforeClass
    public static void setUp() throws Exception {
        util.getConfiguration().setBoolean("hbase.online.schema.update.enable", true);
        admin = (HBaseAdmin) util.getAdmin();
    }
    @After
    public void tearDown() throws Exception {
        for (HTableDescriptor htd: util.getAdmin().listTables()) {
            util.deleteTableIfExists(htd.getTableName());
        }
    }
    @Test
    public void testAdvancedConfigOverride() throws Exception {
        util.getConfiguration().setInt("hbase.hstore.compaction.min", 3);

        String tableName = "testAdvancedConfigOverride";
        TableName TABLE = TableName.valueOf(tableName);
        HTable hTable = util.createTable(TABLE, FAMILY, 10);
        ClusterConnection connection = (ClusterConnection)util.getConnection();

        // Create 3 store files.
        byte[] row = Bytes.toBytes(random.nextInt());
        performMultiplePutAndFlush(admin, hTable, row, FAMILY, 3, 100);

        // Verify we have multiple store files.
        HRegionLocation loc = hTable.getRegionLocation(row, true);
        byte[] regionName = loc.getRegionInfo().getRegionName();
        AdminProtos.AdminService.BlockingInterface server =
                connection.getAdmin(loc.getServerName());
        assertTrue(ProtobufUtil.getStoreFiles(
                server, regionName, FAMILY).size() > 1);

        // Issue a compaction request
        admin.compact(TABLE.getName());

        // poll wait for the compactions to happen
        for (int i = 0; i < 10 * 1000 / 40; ++i) {
            // The number of store files after compaction should be lesser.
            loc = hTable.getRegionLocation(row, true);
            if (!loc.getRegionInfo().isOffline()) {
                regionName = loc.getRegionInfo().getRegionName();
                server = connection.getAdmin(loc.getServerName());
                if (ProtobufUtil.getStoreFiles(
                        server, regionName, FAMILY).size() <= 1) {
                    break;
                }
            }
            Thread.sleep(40);
        }

        // verify the compactions took place and that we didn't just time out
        assertTrue(ProtobufUtil.getStoreFiles(
                server, regionName, FAMILY).size() <= 1);

        // change the compaction.min config option for this table to 5
        HTableDescriptor htd = new HTableDescriptor(hTable.getTableDescriptor());
        htd.setValue("hbase.hstore.compaction.min", String.valueOf(5));
        admin.modifyTable(TABLE, htd);
        Pair<Integer, Integer> st;
        while (null != (st = admin.getAlterStatus(TABLE)) && st.getFirst() > 0) {
            Thread.sleep(40);
        }

        // Create 3 more store files.
        performMultiplePutAndFlush(admin, hTable, row, FAMILY, 3, 10);

        // Issue a compaction request
        admin.compact(TABLE.getName());

        // This time, the compaction request should not happen
        Thread.sleep(10 * 1000);
        loc = hTable.getRegionLocation(row, true);
        regionName = loc.getRegionInfo().getRegionName();
        server = connection.getAdmin(loc.getServerName());
        int sfCount = ProtobufUtil.getStoreFiles(
                server, regionName, FAMILY).size();
        assertTrue(sfCount > 1);

        // change an individual CF's config option to 2 & online schema update
        HColumnDescriptor hcd = new HColumnDescriptor(htd.getFamily(FAMILY));
        hcd.setValue("hbase.hstore.compaction.min", String.valueOf(2));
        htd.modifyFamily(hcd);
        admin.modifyTable(TABLE, htd);
        while (null != (st = admin.getAlterStatus(TABLE)) && st.getFirst() > 0) {
            Thread.sleep(40);
        }

        // Issue a compaction request
        admin.compact(TABLE.getName());

        // poll wait for the compactions to happen
        for (int i = 0; i < 10 * 1000 / 40; ++i) {
            loc = hTable.getRegionLocation(row, true);
            regionName = loc.getRegionInfo().getRegionName();
            try {
                server = connection.getAdmin(loc.getServerName());
                if (ProtobufUtil.getStoreFiles(
                        server, regionName, FAMILY).size() < sfCount) {
                    break;
                }
            } catch (Exception e) {
                //Expected
            }
            Thread.sleep(40);
        }

        // verify the compaction took place and that we didn't just time out
        assertTrue(ProtobufUtil.getStoreFiles(
                server, regionName, FAMILY).size() < sfCount);

        // Finally, ensure that we can remove a custom config value after we made it
        hcd = new HColumnDescriptor(htd.getFamily(FAMILY));
        hcd.setValue("hbase.hstore.compaction.min", null);
        htd.modifyFamily(hcd);
        admin.modifyTable(TABLE, htd);
        while (null != (st = admin.getAlterStatus(TABLE)) && st.getFirst() > 0) {
            Thread.sleep(40);
        }
        assertNull(hTable.getTableDescriptor().getFamily(FAMILY).getValue(
                "hbase.hstore.compaction.min"));
    }

    @Test
    public void testKeepDeletedCells() throws Exception {
        final TableName TABLENAME = TableName.valueOf("testKeepDeletesCells");
        final byte[] FAMILY = Bytes.toBytes("family");
        final byte[] C0 = Bytes.toBytes("c0");

        final byte[] T1 = Bytes.toBytes("T1");
        final byte[] T2 = Bytes.toBytes("T2");
        final byte[] T3 = Bytes.toBytes("T3");
        HColumnDescriptor hcd = new HColumnDescriptor(FAMILY)
                .setKeepDeletedCells(KeepDeletedCells.TRUE).setMaxVersions(3);

        HTableDescriptor desc = new HTableDescriptor(TABLENAME);
        desc.addFamily(hcd);
        util.getAdmin().createTable(desc);
        Configuration c = util.getConfiguration();
        Table h = new HTable(c, TABLENAME);

        long ts = System.currentTimeMillis();
        Put p = new Put(T1, ts);
        p.addColumn(FAMILY, C0, T1);
        h.put(p);
        p = new Put(T1, ts+2);
        p.addColumn(FAMILY, C0, T2);
        h.put(p);
        p = new Put(T1, ts+4);
        p.addColumn(FAMILY, C0, T3);
        h.put(p);

        Delete d = new Delete(T1, ts+3);
        h.delete(d);

        d = new Delete(T1, ts+3);
        d.addColumns(FAMILY, C0, ts+3);
        h.delete(d);

        Get g = new Get(T1);
        // does *not* include the delete
        g.setTimeRange(0, ts+3);
        Result r = h.get(g);
        assertArrayEquals(T2, r.getValue(FAMILY, C0));

        Scan s = new Scan(T1);
        s.setTimeRange(0, ts+3);
        s.setMaxVersions();
        ResultScanner scanner = h.getScanner(s);
        Cell[] kvs = scanner.next().rawCells();
        assertArrayEquals(T2, CellUtil.cloneValue(kvs[0]));
        assertArrayEquals(T1, CellUtil.cloneValue(kvs[1]));
        scanner.close();

        s = new Scan(T1);
        s.setRaw(true);
        s.setMaxVersions();
        scanner = h.getScanner(s);
        kvs = scanner.next().rawCells();
        assertTrue(CellUtil.isDeleteFamily(kvs[0]));
        assertArrayEquals(T3, CellUtil.cloneValue(kvs[1]));
        assertTrue(CellUtil.isDelete(kvs[2]));
        assertArrayEquals(T2, CellUtil.cloneValue(kvs[3]));
        assertArrayEquals(T1, CellUtil.cloneValue(kvs[4]));
        scanner.close();
        h.close();
    }

    @Test
    public void testHTableBatchWithEmptyPut() throws Exception {
        Table table = util.createTable(Bytes.toBytes("testHTableBatchWithEmptyPut"), new byte[][] { FAMILY });
        try {
            List actions = new ArrayList();
            Object[] results = new Object[2];

            // create an empty Put
            Put put1 = new Put(ROW);
            actions.add(put1);

            Put put2 = new Put(ANOTHERROW);
            put2.add(FAMILY, QUALIFIER, VALUE);
            actions.add(put2);

            table.batch(actions, results);
            fail("Empty Put should have failed the batch call");
        } catch (IllegalArgumentException iae) {
           //Expected
        } finally {
            table.close();
        }
    }
    private void performMultiplePutAndFlush(HBaseAdmin admin, HTable table,
                                            byte[] row, byte[] family, int nFlushes, int nPuts)
            throws Exception {
        // connection needed for poll-wait
        HRegionLocation loc = table.getRegionLocation(row, true);
        AdminProtos.AdminService.BlockingInterface server =
                admin.getConnection().getAdmin(loc.getServerName());
        byte[] regName = loc.getRegionInfo().getRegionName();

        for (int i = 0; i < nFlushes; i++) {
            randomCFPuts(table, row, family, nPuts);
            List<String> sf = ProtobufUtil.getStoreFiles(server, regName, FAMILY);
            int sfCount = sf.size();

            admin.flush(table.getTableName());

            // synchronously poll wait for a new storefile to appear (flush happened)
            while (ProtobufUtil.getStoreFiles(
                    server, regName, FAMILY).size() == sfCount) {
                Thread.sleep(40);
            }
        }
    }

    private void randomCFPuts(Table table, byte[] row, byte[] family, int nPuts)
            throws Exception {
        Put put = new Put(row);
        for (int i = 0; i < nPuts; i++) {
            byte[] qualifier = Bytes.toBytes(random.nextInt());
            byte[] value = Bytes.toBytes(random.nextInt());
            put.add(family, qualifier, value);
        }
        table.put(put);
    }

    @Test
    public void testPurgeFutureDeletes() throws Exception {
        final TableName TABLENAME = TableName.valueOf("testPurgeFutureDeletes");
        final byte[] ROW = Bytes.toBytes("row");
        final byte[] FAMILY = Bytes.toBytes("family");
        final byte[] COLUMN = Bytes.toBytes("column");
        final byte[] VALUE = Bytes.toBytes("value");

        Table table = util.createTable(TABLENAME, FAMILY);

        // future timestamp
        long ts = System.currentTimeMillis() * 2;
        Put put = new Put(ROW, ts);
        put.add(FAMILY, COLUMN, VALUE);
        table.put(put);

        Get get = new Get(ROW);
        Result result = table.get(get);
        assertArrayEquals(VALUE, result.getValue(FAMILY, COLUMN));

        Delete del = new Delete(ROW);
        del.deleteColumn(FAMILY, COLUMN, ts);
        table.delete(del);

        get = new Get(ROW);
        result = table.get(get);
        assertNull(result.getValue(FAMILY, COLUMN));

        // major compaction, purged future deletes
        util.getAdmin().flush(TABLENAME);
        util.getAdmin().majorCompact(TABLENAME);

        // waiting for the major compaction to complete
        util.waitFor(6000, new Waiter.Predicate<IOException>() {
            @Override
            public boolean evaluate() throws IOException {
                return util.getAdmin().getCompactionState(TABLENAME) ==
                        AdminProtos.GetRegionInfoResponse.CompactionState.NONE;
            }
        });

        put = new Put(ROW, ts);
        put.add(FAMILY, COLUMN, VALUE);
        table.put(put);

        get = new Get(ROW);
        result = table.get(get);
        assertArrayEquals(VALUE, result.getValue(FAMILY, COLUMN));

        table.close();
    }

    @Test
    public void testWeirdCacheBehaviour() throws Exception {
        TableName TABLE = TableName.valueOf("testWeirdCacheBehaviour");
        byte [][] FAMILIES = new byte[][] { Bytes.toBytes("trans-blob"),
                Bytes.toBytes("trans-type"), Bytes.toBytes("trans-date"),
                Bytes.toBytes("trans-tags"), Bytes.toBytes("trans-group") };
        HTable ht = util.createTable(TABLE, FAMILIES);
        String value = "this is the value";
        String value2 = "this is some other value";
        String keyPrefix1 = UUID.randomUUID().toString();
        String keyPrefix2 = UUID.randomUUID().toString();
        String keyPrefix3 = UUID.randomUUID().toString();
        putRows(ht, 3, value, keyPrefix1);
        putRows(ht, 3, value, keyPrefix2);
        putRows(ht, 3, value, keyPrefix3);
        ht.flushCommits();
        putRows(ht, 3, value2, keyPrefix1);
        putRows(ht, 3, value2, keyPrefix2);
        putRows(ht, 3, value2, keyPrefix3);
        Table table = new HTable(util.getConfiguration(), TABLE);
        System.out.println("Checking values for key: " + keyPrefix1);
        assertEquals("Got back incorrect number of rows from scan", 3,
                getNumberOfRows(keyPrefix1, value2, table));
        System.out.println("Checking values for key: " + keyPrefix2);
        assertEquals("Got back incorrect number of rows from scan", 3,
                getNumberOfRows(keyPrefix2, value2, table));
        System.out.println("Checking values for key: " + keyPrefix3);
        assertEquals("Got back incorrect number of rows from scan", 3,
                getNumberOfRows(keyPrefix3, value2, table));
        deleteColumns(ht, value2, keyPrefix1);
        deleteColumns(ht, value2, keyPrefix2);
        deleteColumns(ht, value2, keyPrefix3);
        System.out.println("Starting important checks.....");
        assertEquals("Got back incorrect number of rows from scan: " + keyPrefix1,
                0, getNumberOfRows(keyPrefix1, value2, table));
        assertEquals("Got back incorrect number of rows from scan: " + keyPrefix2,
                0, getNumberOfRows(keyPrefix2, value2, table));
        assertEquals("Got back incorrect number of rows from scan: " + keyPrefix3,
                0, getNumberOfRows(keyPrefix3, value2, table));
        ht.setScannerCaching(0);
        assertEquals("Got back incorrect number of rows from scan", 0,
                getNumberOfRows(keyPrefix1, value2, table)); ht.setScannerCaching(100);
        assertEquals("Got back incorrect number of rows from scan", 0,
                getNumberOfRows(keyPrefix2, value2, table));
    }

    @Test
    public void testHTableExistsMethodSingleRegionSingleGet() throws Exception {
        // Test with a single region table.
        Table table = util.createTable(
                Bytes.toBytes("testHTableExistsMethodSingleRegionSingleGet"), new byte[][] { FAMILY });

        Put put = new Put(ROW);
        put.addColumn(FAMILY, QUALIFIER, VALUE);

        Get get = new Get(ROW);

        boolean exist = table.exists(get);
        assertEquals(exist, false);

        table.put(put);

        exist = table.exists(get);
        assertEquals(exist, true);
    }

    @Test
    public void testHTableExistsBeforeGet() throws Exception {
        Table table = util.createTable(
                Bytes.toBytes("testHTableExistsBeforeGet"), new byte[][] { FAMILY });
        try {
            Put put = new Put(ROW);
            put.add(FAMILY, QUALIFIER, VALUE);
            table.put(put);

            Get get = new Get(ROW);

            boolean exist = table.exists(get);
            assertEquals(true, exist);

            Result result = table.get(get);
            assertEquals(false, result.isEmpty());
            assertTrue(Bytes.equals(VALUE, result.getValue(FAMILY, QUALIFIER)));
        } finally {
            table.close();
        }
    }

    @Test
    public void testHTableExistsAllBeforeGet() throws Exception {
        final byte[] ROW2 = Bytes.add(ROW, Bytes.toBytes("2"));
        Table table = util.createTable(
                Bytes.toBytes("testHTableExistsAllBeforeGet"), new byte[][] { FAMILY });
        try {
            Put put = new Put(ROW);
            put.add(FAMILY, QUALIFIER, VALUE);
            table.put(put);
            put = new Put(ROW2);
            put.add(FAMILY, QUALIFIER, VALUE);
            table.put(put);

            Get get = new Get(ROW);
            Get get2 = new Get(ROW2);
            ArrayList<Get> getList = new ArrayList(2);
            getList.add(get);
            getList.add(get2);

            boolean[] exists = table.existsAll(getList);
            assertEquals(true, exists[0]);
            assertEquals(true, exists[1]);

            Result[] result = table.get(getList);
            assertEquals(false, result[0].isEmpty());
            assertTrue(Bytes.equals(VALUE, result[0].getValue(FAMILY, QUALIFIER)));
            assertEquals(false, result[1].isEmpty());
            assertTrue(Bytes.equals(VALUE, result[1].getValue(FAMILY, QUALIFIER)));
        } finally {
            table.close();
        }
    }

    @Test
    public void testHTableExistsMethodMultipleRegionsSingleGet() throws Exception {
        Table table = util.createTable(
                TableName.valueOf("testHTableExistsMethodMultipleRegionsSingleGet"), new byte[][] { FAMILY },
                1, new byte[] { 0x00 }, new byte[] { (byte) 0xff }, 255);
        Put put = new Put(ROW);
        put.add(FAMILY, QUALIFIER, VALUE);

        Get get = new Get(ROW);

        boolean exist = table.exists(get);
        assertEquals(exist, false);

        table.put(put);

        exist = table.exists(get);
        assertEquals(exist, true);
    }

    @Test
    public void testHTableExistsMethodMultipleRegionsMultipleGets() throws Exception {
        HTable table = util.createTable(
                TableName.valueOf("testHTableExistsMethodMultipleRegionsMultipleGets"),
                new byte[][] { FAMILY }, 1, new byte[] { 0x00 }, new byte[] { (byte) 0xff }, 255);
        Put put = new Put(ROW);
        put.add(FAMILY, QUALIFIER, VALUE);
        table.put (put);

        List<Get> gets = new ArrayList<Get>();
        gets.add(new Get(ANOTHERROW));
        gets.add(new Get(Bytes.add(ROW, new byte[] { 0x00 })));
        gets.add(new Get(ROW));
        gets.add(new Get(Bytes.add(ANOTHERROW, new byte[] { 0x00 })));

        Boolean[] results = table.exists(gets);
        assertEquals(results[0], false);
        assertEquals(results[1], false);
        assertEquals(results[2], true);
        assertEquals(results[3], false);

        // Test with the first region.
        put = new Put(new byte[] { 0x00 });
        put.addColumn(FAMILY, QUALIFIER, VALUE);
        table.put(put);

        gets = new ArrayList<>();
        gets.add(new Get(new byte[] { 0x00 }));
        gets.add(new Get(new byte[] { 0x00, 0x00 }));
        results = table.exists(gets);
        assertEquals(results[0], true);
        assertEquals(results[1], false);

        // Test with the last region
        put = new Put(new byte[] { (byte) 0xff, (byte) 0xff });
        put.addColumn(FAMILY, QUALIFIER, VALUE);
        table.put(put);

        gets = new ArrayList<>();
        gets.add(new Get(new byte[] { (byte) 0xff }));
        gets.add(new Get(new byte[] { (byte) 0xff, (byte) 0xff }));
        gets.add(new Get(new byte[] { (byte) 0xff, (byte) 0xff, (byte) 0xff }));
        results = table.exists(gets);
        assertEquals(results[0], false);
        assertEquals(results[1], true);
        assertEquals(results[2], false);
    }

    @Test
    public void testGetEmptyRow() throws Exception {
        //Create a table and put in 1 row
        Admin admin = util.getAdmin();
        HTableDescriptor desc = new HTableDescriptor(TableName.valueOf(Bytes.toBytes("test")));
        desc.addFamily(new HColumnDescriptor(FAMILY));
        admin.createTable(desc);
        Table table = new HTable(util.getConfiguration(), desc.getTableName());

        Put put = new Put(ROW_BYTES);
        put.add(FAMILY, COL_QUAL, VAL_BYTES);
        table.put(put);

        //Try getting the row with an empty row key
        Result res = null;
        try {
            res = table.get(new Get(new byte[0]));
            fail();
        } catch (IllegalArgumentException e) {
            // Expected.
        }
        assertTrue(res == null);
        res = table.get(new Get(Bytes.toBytes("r1-not-exist")));
        assertTrue(res.isEmpty() == true);
        res = table.get(new Get(ROW_BYTES));
        assertTrue(Arrays.equals(res.getValue(FAMILY, COL_QUAL), VAL_BYTES));
        table.close();
    }

    private void putRows(Table ht, int numRows, String value, String key)
            throws IOException {
        for (int i = 0; i < numRows; i++) {
            String row = key + "_" + UUID.randomUUID().toString();
            System.out.println(String.format("Saving row: %s, with value %s", row,
                    value));
            Put put = new Put(Bytes.toBytes(row));
            put.setDurability(Durability.SKIP_WAL);
            put.addColumn(Bytes.toBytes("trans-blob"), null, Bytes
                    .toBytes("value for blob"));
            put.addColumn(Bytes.toBytes("trans-type"), null, Bytes.toBytes("statement"));
            put.addColumn(Bytes.toBytes("trans-date"), null, Bytes
                    .toBytes("20090921010101999"));
            put.addColumn(Bytes.toBytes("trans-tags"), Bytes.toBytes("qual2"), Bytes
                    .toBytes(value));
            put.addColumn(Bytes.toBytes("trans-group"), null, Bytes
                    .toBytes("adhocTransactionGroupId"));
            ht.put(put);
        }
    }

    private int getNumberOfRows(String keyPrefix, String value, Table ht)
            throws Exception {
        ResultScanner resultScanner = buildScanner(keyPrefix, value, ht);
        Iterator<Result> scanner = resultScanner.iterator();
        int numberOfResults = 0;
        while (scanner.hasNext()) {
            Result result = scanner.next();
            System.out.println("Got back key: " + Bytes.toString(result.getRow()));
            for (Cell kv : result.rawCells()) {
                System.out.println("kv=" + kv.toString() + ", "
                        + Bytes.toString(CellUtil.cloneValue(kv)));
            }
            numberOfResults++;
        }
        return numberOfResults;
    }

    private ResultScanner buildScanner(String keyPrefix, String value, Table ht)
            throws IOException {
        // OurFilterList allFilters = new OurFilterList();
        FilterList allFilters = new FilterList(/* FilterList.Operator.MUST_PASS_ALL */);
        allFilters.addFilter(new PrefixFilter(Bytes.toBytes(keyPrefix)));
        SingleColumnValueFilter filter = new SingleColumnValueFilter(Bytes
                .toBytes("trans-tags"), Bytes.toBytes("qual2"), CompareFilter.CompareOp.EQUAL, Bytes
                .toBytes(value));
        filter.setFilterIfMissing(true);
        allFilters.addFilter(filter);

        // allFilters.addFilter(new
        // RowExcludingSingleColumnValueFilter(Bytes.toBytes("trans-tags"),
        // Bytes.toBytes("qual2"), CompareOp.EQUAL, Bytes.toBytes(value)));
        Scan scan = new Scan();
        scan.addFamily(Bytes.toBytes("trans-blob"));
        scan.addFamily(Bytes.toBytes("trans-type"));
        scan.addFamily(Bytes.toBytes("trans-date"));
        scan.addFamily(Bytes.toBytes("trans-tags"));
        scan.addFamily(Bytes.toBytes("trans-group"));
        scan.setFilter(allFilters);

        return ht.getScanner(scan);
    }

    private void deleteColumns(Table ht, String value, String keyPrefix)
            throws IOException {
        ResultScanner scanner = buildScanner(keyPrefix, value, ht);
        Iterator<Result> it = scanner.iterator();
        int count = 0;
        while (it.hasNext()) {
            Result result = it.next();
            Delete delete = new Delete(result.getRow());
            delete.addColumn(Bytes.toBytes("trans-tags"), Bytes.toBytes("qual2"));
            ht.delete(delete);
            count++;
        }
        assertEquals("Did not perform correct number of deletes", 3, count);
    }
}
