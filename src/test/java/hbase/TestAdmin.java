package hbase;

import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.HRegionLocation;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.InvalidFamilyOperationException;
import org.apache.hadoop.hbase.ProcedureInfo;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.TableNotDisabledException;
import org.apache.hadoop.hbase.TableNotEnabledException;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Append;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.RetriesExhaustedException;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.zookeeper.ZKTableStateClientSideReader;
import org.apache.hadoop.hbase.zookeeper.ZooKeeperWatcher;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestAdmin {
    private static HBaseUtility util = new HBaseUtility();
    private static Admin admin;
    @BeforeClass
    public static void setUp() throws Exception {
        util.getConfiguration().setBoolean("hbase.online.schema.update.enable", true);
        admin = util.getAdmin();
    }

    @Test
    public void testDisableAndEnableTable() throws IOException {
        final byte [] row = Bytes.toBytes("row");
        final byte [] qualifier = Bytes.toBytes("qualifier");
        final byte [] value = Bytes.toBytes("value");
        final TableName table = TableName.valueOf("testDisableAndEnableTable");
        Table t = util.createTable(table, HConstants.CATALOG_FAMILY);

        Put put = new Put(row);
        put.addColumn(HConstants.CATALOG_FAMILY, qualifier, value);
        t.put(put);
        Get get = new Get(row);
        get.addColumn(HConstants.CATALOG_FAMILY, qualifier);
        t.get(get);

        this.admin.disableTable(t.getName());
        assertTrue("Table must be disabled", !admin.isTableEnabled(table));

        // Test that table is disabled
        get = new Get(row);
        get.addColumn(HConstants.CATALOG_FAMILY, qualifier);
        boolean ok = false;
        try {
            t.get(get);
        } catch (TableNotEnabledException e) {
            ok = true;
        }
        assertTrue(ok);
        ok = false;
        // verify that scan encounters correct exception
        Scan scan = new Scan();
        try {
            ResultScanner scanner = t.getScanner(scan);
            Result res;
            do {
                res = scanner.next();
            } while (res != null);
        } catch (TableNotEnabledException e) {
            ok = true;
        }
        assertTrue(ok);

        this.admin.enableTable(table);
        assertTrue("Table must be enabled.", admin.isTableEnabled(table));
        try {
            t.get(get);
        } catch (RetriesExhaustedException e) {
            ok = false;
        }
        assertTrue(ok);
        admin.disableTable(table);
        admin.deleteTable(table);
    }

    @Test
    public void testDisableAndEnableTables() throws IOException {
        final byte [] row = Bytes.toBytes("row");
        final byte [] qualifier = Bytes.toBytes("qualifier");
        final byte [] value = Bytes.toBytes("value");
        final TableName table1 = TableName.valueOf("testDisableAndEnableTable1");
        final TableName table2 = TableName.valueOf("testDisableAndEnableTable2");
        Table t1 = util.createTable(table1, HConstants.CATALOG_FAMILY);
        Table t2 = util.createTable(table2, HConstants.CATALOG_FAMILY);

        Put put = new Put(row);
        put.addColumn(HConstants.CATALOG_FAMILY, qualifier, value);
        t1.put(put);
        t2.put(put);
        Get get = new Get(row);
        get.addColumn(HConstants.CATALOG_FAMILY, qualifier);
        t1.get(get);
        t2.get(get);

        this.admin.disableTables("testDisableAndEnableTable.*");

        // Test that tables are disabled
        get = new Get(row);
        get.addColumn(HConstants.CATALOG_FAMILY, qualifier);
        boolean ok = false;
        try {
            t1.get(get);
            t2.get(get);
        } catch (org.apache.hadoop.hbase.DoNotRetryIOException e) {
            ok = true;
        }

        assertTrue(ok);
        this.admin.enableTables("testDisableAndEnableTable.*");

        // Test that tables are enabled
        try {
            t1.get(get);
        } catch (IOException e) {
            ok = false;
        }
        try {
            t2.get(get);
        } catch (IOException e) {
            ok = false;
        }
        assertTrue(ok);
        admin.disableTable(table1);
        admin.disableTable(table2);
        admin.deleteTable(table1);
        admin.deleteTable(table2);
    }

    @Test
    public void testCreateTable() throws IOException {
        HTableDescriptor[] tables = admin.listTables();
        int numTables = tables.length;
        TableName tn = TableName.valueOf("testCreateTable");
        util.createTable(tn, HConstants.CATALOG_FAMILY).close();
        tables = this.admin.listTables();
        assertEquals(numTables + 1, tables.length);
        assertTrue("Table must be enabled.", admin.isTableEnabled(tn));
        admin.disableTable(tn);
        admin.deleteTable(tn);
    }

    @Test
    public void testTruncateTable() throws IOException {
        testTruncateTable(TableName.valueOf("testTruncateTable"), false);
    }

    private void testTruncateTable(final TableName tableName, boolean preserveSplits)
            throws IOException {
        byte[][] splitKeys = new byte[2][];
        splitKeys[0] = Bytes.toBytes(4);
        splitKeys[1] = Bytes.toBytes(8);

        // Create & Fill the table
        util.deleteTableIfExists(tableName);
        Table table = util.createTable(tableName, HConstants.CATALOG_FAMILY, splitKeys);
        try {
            util.loadNumericRows(table, HConstants.CATALOG_FAMILY, 0, 1000);
            assertEquals(1000, util.countRows(table));
        } finally {
            table.close();
        }
        assertEquals(3, this.admin.getTableRegions(tableName).size());

        // Truncate & Verify
        this.admin.disableTable(tableName);
        this.admin.truncateTable(tableName, preserveSplits);
        table = util.getConnection().getTable(tableName);
        try {
            assertEquals(0, util.countRows(table));
        } finally {
            table.close();
        }
        if (preserveSplits) {
            assertEquals(3, this.admin.getTableRegions(tableName).size());
        } else {
            assertEquals(1, this.admin.getTableRegions(tableName).size());
        }
    }

    @Test
    public void testGetTableDescriptor() throws IOException {
        HColumnDescriptor fam1 = new HColumnDescriptor("fam1");
        HColumnDescriptor fam2 = new HColumnDescriptor("fam2");
        HColumnDescriptor fam3 = new HColumnDescriptor("fam3");
        TableName tn = TableName.valueOf("myTestTable");
        HTableDescriptor htd = new HTableDescriptor(tn);
        htd.addFamily(fam1);
        htd.addFamily(fam2);
        htd.addFamily(fam3);
        util.deleteTableIfExists(tn);
        this.admin.createTable(htd);
        Table table = admin.getConnection().getTable(TableName.valueOf("myTestTable"));
        HTableDescriptor confirmedHtd = table.getTableDescriptor();
        assertEquals(htd.compareTo(confirmedHtd), 0);
        table.close();
    }

    @Test
    public void testCompactionTimestamps() throws Exception {
        HColumnDescriptor fam1 = new HColumnDescriptor("fam1");
        TableName tableName = TableName.valueOf("testCompactionTimestampsTable");
        util.deleteTableIfExists(tableName);
        HTableDescriptor htd = new HTableDescriptor(tableName);
        htd.addFamily(fam1);
        this.admin.createTable(htd);
        Table table = util.getConnection().getTable(htd.getTableName());
        long ts = this.admin.getLastMajorCompactionTimestamp(tableName);
        assertEquals(0, ts);
        Put p = new Put(Bytes.toBytes("row1"));
        p.add(Bytes.toBytes("fam1"), Bytes.toBytes("fam1"), Bytes.toBytes("fam1"));
        table.put(p);
        ts = this.admin.getLastMajorCompactionTimestamp(tableName);
        // no files written -> no data
        assertEquals(0, ts);

        this.admin.flush(tableName);
        ts = this.admin.getLastMajorCompactionTimestamp(tableName);
        // still 0, we flushed a file, but no major compaction happened
        assertEquals(0, ts);

        byte[] regionName = ((HTable)table).getRegionLocator().
                getAllRegionLocations().get(0).getRegionInfo().getRegionName();
        long ts1 = this.admin.getLastMajorCompactionTimestampForRegion(regionName);
        assertEquals(ts, ts1);
        p = new Put(Bytes.toBytes("row2"));
        p.add(Bytes.toBytes("fam1"), Bytes.toBytes("fam1"), Bytes.toBytes("fam1"));
        table.put(p);
        this.admin.flush(tableName);
        ts = this.admin.getLastMajorCompactionTimestamp(tableName);
        // make sure the region API returns the same value, as the old file is still around
        assertEquals(ts1, ts);

        admin.majorCompact(tableName);
        table.put(p);
        // forces a wait for the compaction
        this.admin.flush(tableName);

        // region api still the same
        ts1 = this.admin.getLastMajorCompactionTimestampForRegion(regionName);
        System.out.println("last modify time: " + ts1);
        table.put(p);
        this.admin.flush(tableName);
        ts = this.admin.getLastMajorCompactionTimestamp(tableName);
        System.out.println("last modify time: " + ts);
        table.close();
    }

    @Test
    public void testOnlineChangeTableSchema() throws IOException, InterruptedException {
        final TableName tableName =
                TableName.valueOf("changeTableSchemaOnline");
        HTableDescriptor [] tables = admin.listTables();
        int numTables = tables.length;
        util.createTable(tableName, HConstants.CATALOG_FAMILY).close();
        tables = this.admin.listTables();
        assertEquals(numTables + 1, tables.length);

        // FIRST, do htabledescriptor changes.
        HTableDescriptor htd = this.admin.getTableDescriptor(tableName);
        // Make a copy and assert copy is good.
        HTableDescriptor copy = new HTableDescriptor(htd);
        assertTrue(htd.equals(copy));
        // Now amend the copy. Introduce differences.
        long newFlushSize = htd.getMemStoreFlushSize() / 2;
        if (newFlushSize <=0) {
            newFlushSize = HTableDescriptor.DEFAULT_MEMSTORE_FLUSH_SIZE / 2;
        }
        copy.setMemStoreFlushSize(newFlushSize);
        final String key = "anyoldkey";
        assertTrue(htd.getValue(key) == null);
        copy.setValue(key, key);
        boolean expectedException = false;
        try {
            admin.modifyTable(tableName, copy);
        } catch (TableNotDisabledException re) {
            expectedException = true;
        }
        assertFalse(expectedException);
        HTableDescriptor modifiedHtd = this.admin.getTableDescriptor(tableName);
        assertFalse(htd.equals(modifiedHtd));
        assertTrue(copy.equals(modifiedHtd));
        assertEquals(newFlushSize, modifiedHtd.getMemStoreFlushSize());
        assertEquals(key, modifiedHtd.getValue(key));

        // Now work on column family changes.
        int countOfFamilies = modifiedHtd.getFamilies().size();
        assertTrue(countOfFamilies > 0);
        HColumnDescriptor hcd = modifiedHtd.getFamilies().iterator().next();
        int maxversions = hcd.getMaxVersions();
        final int newMaxVersions = maxversions + 1;
        hcd.setMaxVersions(newMaxVersions);
        final byte [] hcdName = hcd.getName();
        expectedException = false;
        try {
            this.admin.modifyColumn(tableName, hcd);
        } catch (TableNotDisabledException re) {
            expectedException = true;
        }
        assertFalse(expectedException);
        modifiedHtd = this.admin.getTableDescriptor(tableName);
        HColumnDescriptor modifiedHcd = modifiedHtd.getFamily(hcdName);
        assertEquals(newMaxVersions, modifiedHcd.getMaxVersions());

        // Try adding a column
        assertFalse(this.admin.isTableDisabled(tableName));
        final String xtracolName = "xtracol";
        HColumnDescriptor xtracol = new HColumnDescriptor(xtracolName);
        xtracol.setValue(xtracolName, xtracolName);
        expectedException = false;
        try {
            this.admin.addColumn(tableName, xtracol);
        } catch (TableNotDisabledException re) {
            expectedException = true;
        }
        // Add column should work even if the table is enabled
        assertFalse(expectedException);
        modifiedHtd = this.admin.getTableDescriptor(tableName);
        hcd = modifiedHtd.getFamily(xtracol.getName());
        assertTrue(hcd != null);
        assertTrue(hcd.getValue(xtracolName).equals(xtracolName));

        // Delete the just-added column.
        this.admin.deleteColumn(tableName, xtracol.getName());
        modifiedHtd = this.admin.getTableDescriptor(tableName);
        hcd = modifiedHtd.getFamily(xtracol.getName());
        assertTrue(hcd == null);

        // Delete the table
        this.admin.disableTable(tableName);
        this.admin.deleteTable(tableName);
        this.admin.listTables();
        assertFalse(this.admin.tableExists(tableName));
    }

    @Test
    public void testShouldFailOnlineSchemaUpdateIfOnlineSchemaIsNotEnabled()
            throws Exception {
        final TableName tableName = TableName.valueOf("changeTableSchemaOnlineFailure");
        util.getConfiguration().setBoolean(
                "hbase.online.schema.update.enable", false);
        HTableDescriptor[] tables = admin.listTables();
        util.createTable(tableName, HConstants.CATALOG_FAMILY).close();

        // FIRST, do htabledescriptor changes.
        HTableDescriptor htd = this.admin.getTableDescriptor(tableName);
        // Make a copy and assert copy is good.
        HTableDescriptor copy = new HTableDescriptor(htd);
        assertTrue(htd.equals(copy));
        // Now amend the copy. Introduce differences.
        long newFlushSize = htd.getMemStoreFlushSize() / 2;
        if (newFlushSize <=0) {
            newFlushSize = HTableDescriptor.DEFAULT_MEMSTORE_FLUSH_SIZE / 2;
        }
        copy.setMemStoreFlushSize(newFlushSize);
        final String key = "anyoldkey";
        assertTrue(htd.getValue(key) == null);
        copy.setValue(key, key);
        try {
            admin.modifyTable(tableName, copy);
        } catch (TableNotDisabledException re) {
            System.out.println("table " + tableName + " not disabled, which is expected");
        }

        util.getConfiguration().setBoolean(
                "hbase.online.schema.update.enable", true);
    }

    @Test
    public void testCreateTableNumberOfRegions() throws IOException, InterruptedException {
        TableName tableName = TableName.valueOf("testCreateTableNumberOfRegions");
        HTableDescriptor desc = new HTableDescriptor(tableName);
        desc.addFamily(new HColumnDescriptor(HConstants.CATALOG_FAMILY));

        TableName TABLE_4 = TableName.valueOf(tableName.getNameAsString() + "_4");
        desc = new HTableDescriptor(TABLE_4);
        desc.addFamily(new HColumnDescriptor(HConstants.CATALOG_FAMILY));
        try {
            admin.createTable(desc, "a".getBytes(), "z".getBytes(), 2);
            fail("Should not be able to create a table with only 2 regions using this API.");
        } catch (IllegalArgumentException eae) {
            // Expected
        }
    }

    @Test
    public void testTableAvailableWithRandomSplitKeys() throws Exception {
        TableName tableName = TableName.valueOf("testTableAvailableWithRandomSplitKeys");
        util.deleteTableIfExists(tableName);
        HTableDescriptor desc = new HTableDescriptor(tableName);
        desc.addFamily(new HColumnDescriptor("col"));
        byte[][] splitKeys = new byte [][] {
                new byte [] { 1, 1, 1 },
                new byte [] { 2, 2, 2 }
        };
        admin.createTable(desc);
        boolean tableAvailable = admin.isTableAvailable(tableName, splitKeys);
        assertFalse("Table should be created with 1 row in META", tableAvailable);
    }

    @Test
    public void testCreateTableWithOnlyEmptyStartRow() throws IOException {
        byte[] tableName = Bytes.toBytes("testCreateTableWithOnlyEmptyStartRow");
        byte[][] splitKeys = new byte[1][];
        splitKeys[0] = HConstants.EMPTY_BYTE_ARRAY;
        HTableDescriptor desc = new HTableDescriptor(TableName.valueOf(tableName));
        desc.addFamily(new HColumnDescriptor("col"));
        try {
            admin.createTable(desc, splitKeys);
            fail("Test case should fail as empty split key is passed.");
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testCreateTableWithEmptyRowInTheSplitKeys() throws IOException{
        byte[] tableName = Bytes.toBytes("testCreateTableWithEmptyRowInTheSplitKeys");
        byte[][] splitKeys = new byte[3][];
        splitKeys[0] = "region1".getBytes();
        splitKeys[1] = HConstants.EMPTY_BYTE_ARRAY;
        splitKeys[2] = "region2".getBytes();
        HTableDescriptor desc = new HTableDescriptor(TableName.valueOf(tableName));
        desc.addFamily(new HColumnDescriptor("col"));
        try {
            admin.createTable(desc, splitKeys);
            fail("Test case should fail as empty split key is passed.");
        } catch (IllegalArgumentException e) {
            //Expected
        }
    }

    @Test
    public void testTableExist() throws IOException {
        final TableName table = TableName.valueOf("testTableExist");
        util.deleteTableIfExists(table);
        boolean exist;
        exist = this.admin.tableExists(table);
        assertEquals(false, exist);
        util.createTable(table, HConstants.CATALOG_FAMILY);
        exist = this.admin.tableExists(table);
        assertEquals(true, exist);
    }

    @Test
    public void testForceSplit() throws Exception {
        byte[][] familyNames = new byte[][] { Bytes.toBytes("cf") };
        int[] rowCounts = new int[] { 6000 };
        int numVersions = HColumnDescriptor.DEFAULT_VERSIONS;
        int blockSize = 1024000;
        splitTest(null, familyNames, rowCounts, numVersions, blockSize);
        byte[] splitKey = Bytes.toBytes(3500);
        splitTest(splitKey, familyNames, rowCounts, numVersions, blockSize);
    }

    void splitTest(byte[] splitPoint, byte[][] familyNames, int[] rowCounts,
                   int numVersions, int blockSize) throws Exception {
        TableName tableName = TableName.valueOf("testForceSplit");
        StringBuilder sb = new StringBuilder();
        // Add tail to String so can see better in logs where a test is running.
        for (int i = 0; i < rowCounts.length; i++) {
            sb.append("_").append(Integer.toString(rowCounts[i]));
        }
        final HTable table = util.createTable(tableName, familyNames,
                numVersions, blockSize);
        int rowCount = 0;
        byte[] q = new byte[0];

        // insert rows into column families. The number of rows that have values
        // in a specific column family is decided by rowCounts[familyIndex]
        for (int index = 0; index < familyNames.length; index++) {
            ArrayList<Put> puts = new ArrayList<>(rowCounts[index]);
            for (int i = 0; i < rowCounts[index]; i++) {
                byte[] k = Bytes.toBytes(i);
                Put put = new Put(k);
                put.addColumn(familyNames[index], q, k);
                puts.add(put);
            }
            table.put(puts);

            if ( rowCount < rowCounts[index] ) {
                rowCount = rowCounts[index];
            }
        }

        // get the initial layout (should just be one region)
        Map<HRegionInfo, ServerName> m = table.getRegionLocations();
        assertTrue(m.size() == 1);

        // Verify row count
        Scan scan = new Scan();
        ResultScanner scanner = table.getScanner(scan);
        int rows = 0;
        for(Result result : scanner) {
            rows++;
        }
        scanner.close();
        assertEquals(rowCount, rows);

        // Have an outstanding scan going on to make sure we can scan over splits.
        scan = new Scan();
        scanner = table.getScanner(scan);
        // Scan first row so we are into first region before split happens.
        scanner.next();

        // Split the table
        this.admin.split(tableName, splitPoint);

        final AtomicInteger count = new AtomicInteger(0);
        Thread t = new Thread("CheckForSplit") {
            @Override
            public void run() {
                for (int i = 0; i < 45; i++) {
                    try {
                        sleep(1000);
                    } catch (InterruptedException e) {
                        continue;
                    }
                    // check again    table = new HTable(conf, tableName);
                    Map<HRegionInfo, ServerName> regions = null;
                    try {
                        regions = table.getRegionLocations();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    if (regions == null) continue;
                    count.set(regions.size());
                    if (count.get() >= 2) {
                        break;
                    }
                }
            }
        };
        t.setPriority(Thread.NORM_PRIORITY - 2);
        t.start();
        t.join();

        // Verify row count
        rows = 1; // We counted one row above.
        for (Result result : scanner) {
            rows++;
            if (rows > rowCount) {
                scanner.close();
                assertTrue("Scanned more than expected (" + rowCount + ")", false);
            }
        }
        scanner.close();
        assertEquals(rowCount, rows);

        Map<HRegionInfo, ServerName> regions = null;
        try {
            regions = table.getRegionLocations();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("region size: " + regions.size());
        Set<HRegionInfo> hRegionInfos = regions.keySet();
        HRegionInfo[] r = hRegionInfos.toArray(new HRegionInfo[hRegionInfos.size()]);
        if (splitPoint != null) {
            // make sure the split point matches our explicit configuration
            assertEquals(Bytes.toString(splitPoint),
                    Bytes.toString(r[0].getEndKey()));
            assertEquals(Bytes.toString(splitPoint),
                    Bytes.toString(r[1].getStartKey()));
        }
        util.deleteTableIfExists(tableName);
        table.close();
    }

    @Test
    public void testEnableTableRetainAssignment() throws IOException {
        final TableName tableName = TableName.valueOf("testEnableTableAssignment");
        byte[][] splitKeys = { new byte[] { 1, 1, 1 }, new byte[] { 2, 2, 2 },
                new byte[] { 3, 3, 3 }, new byte[] { 4, 4, 4 }, new byte[] { 5, 5, 5 },
                new byte[] { 6, 6, 6 }, new byte[] { 7, 7, 7 }, new byte[] { 8, 8, 8 },
                new byte[] { 9, 9, 9 } };
        int expectedRegions = splitKeys.length + 1;
        HTableDescriptor desc = new HTableDescriptor(tableName);
        util.deleteTableIfExists(tableName);
        desc.addFamily(new HColumnDescriptor(HConstants.CATALOG_FAMILY));
        admin.createTable(desc, splitKeys);
        HTable ht = new HTable(util.getConfiguration(), tableName);
        Map<HRegionInfo, ServerName> regions = ht.getRegionLocations();
        assertEquals("Tried to create " + expectedRegions + " regions "
                + "but only found " + regions.size(), expectedRegions, regions.size());
        // Disable table.
        admin.disableTable(tableName);
        // Enable table, use retain assignment to assign regions.
        admin.enableTable(tableName);
        Map<HRegionInfo, ServerName> regions2 = ht.getRegionLocations();

        // Check the assignment.
        assertEquals(regions.size(), regions2.size());
        for (Map.Entry<HRegionInfo, ServerName> entry : regions.entrySet()) {
            assertEquals(regions2.get(entry.getKey()), entry.getValue());
        }
    }

    @Test
    public void testForceSplitMultiFamily() throws Exception {
        int numVersions = HColumnDescriptor.DEFAULT_VERSIONS;

        // use small HFile block size so that we can have lots of blocks in HFile
        // Otherwise, if there is only one block,
        // HFileBlockIndex.midKey()'s value == startKey
        int blockSize = 1024;
        byte[][] familyNames = new byte[][] { Bytes.toBytes("cf1"),
                Bytes.toBytes("cf2") };

        // one of the column families isn't splittable
        int[] rowCounts = new int[] { 6000, 1 };
        splitTest(null, familyNames, rowCounts, numVersions, blockSize);

        rowCounts = new int[] { 1, 6000 };
        splitTest(null, familyNames, rowCounts, numVersions, blockSize);

        // one column family has much smaller data than the other
        // the split key should be based on the largest column family
        rowCounts = new int[] { 6000, 300 };
        splitTest(null, familyNames, rowCounts, numVersions, blockSize);

        rowCounts = new int[] { 300, 6000 };
        splitTest(null, familyNames, rowCounts, numVersions, blockSize);
    }

    @Test
    public void testDisableCatalogTable() throws Exception {
        try {
            this.admin.disableTable(TableName.META_TABLE_NAME);
            fail("Expected to throw ConstraintException");
        } catch (Exception e) {
            //Expected
        }

        HTableDescriptor htd = new HTableDescriptor(TableName.valueOf("testDisableCatalogTable".getBytes()));
        HColumnDescriptor hcd = new HColumnDescriptor("cf1".getBytes());
        htd.addFamily(hcd);
        util.deleteTableIfExists(TableName.valueOf("testDisableCatalogTable".getBytes()));
        util.getAdmin().createTable(htd);
    }

    @Test
    public void testIsEnabledOrDisabledOnUnknownTable() throws Exception {
        try {
            admin.isTableEnabled(TableName.valueOf("unkownTable"));
            fail("Test should fail if isTableEnabled called on unknown table.");
        } catch (IOException e) {
        }

        try {
            admin.isTableDisabled(TableName.valueOf("unkownTable"));
            fail("Test should fail if isTableDisabled called on unknown table.");
        } catch (IOException e) {
        }
    }

    @Test
    public void testBalancer() throws Exception {
        boolean initialState = admin.isBalancerEnabled();

        // Start the balancer, wait for it.
        boolean prevState = admin.setBalancerRunning(!initialState, true);

        // The previous state should be the original state we observed
        assertEquals(initialState, prevState);

        // Current state should be opposite of the original
        assertEquals(!initialState, admin.isBalancerEnabled());

        // Reset it back to what it was
        prevState = admin.setBalancerRunning(initialState, true);

        // The previous state should be the opposite of the initial state
        assertEquals(!initialState, prevState);
        // Current state should be the original state again
        assertEquals(initialState, admin.isBalancerEnabled());
    }

    @Test
    public void testRegionNormalizer() throws Exception {
        boolean initialState = admin.isNormalizerEnabled();

        // flip state
        boolean prevState = admin.setNormalizerRunning(!initialState);

        // The previous state should be the original state we observed
        assertEquals(initialState, prevState);

        // Current state should be opposite of the original
        assertEquals(!initialState, admin.isNormalizerEnabled());

        // Reset it back to what it was
        prevState = admin.setNormalizerRunning(initialState);

        // The previous state should be the opposite of the initial state
        assertEquals(!initialState, prevState);
        // Current state should be the original state again
        assertEquals(initialState, admin.isNormalizerEnabled());
    }

    @Test
    public void testAbortProcedureFail() throws Exception {
        Random randomGenerator = new Random();
        long procId = randomGenerator.nextLong();

        boolean abortResult = admin.abortProcedure(procId, true);
        assertFalse(abortResult);
    }

    @Test
    public void testListProcedures() throws Exception {
        ProcedureInfo[] procList = admin.listProcedures();
        assertTrue(procList.length >= 0);
    }

    @Test
    public void testEnableDisableAddColumnDeleteColumn() throws Exception {
        ZooKeeperWatcher zkw = HBaseUtility.getZooKeeperWatcher(util);
        TableName tableName = TableName.valueOf("testEnableDisableAddColumnDeleteColumn");
        util.createTable(tableName, HConstants.CATALOG_FAMILY).close();
        while (!ZKTableStateClientSideReader.isEnabledTable(zkw,
                TableName.valueOf("testEnableDisableAddColumnDeleteColumn"))) {
            Thread.sleep(10);
        }
        this.admin.disableTable(tableName);
        try {
            new HTable(util.getConfiguration(), tableName);
        } catch (org.apache.hadoop.hbase.DoNotRetryIOException e) {
            //expected
        }

        this.admin.addColumn(tableName, new HColumnDescriptor("col2"));
        this.admin.enableTable(tableName);
        try {
            this.admin.deleteColumn(tableName, Bytes.toBytes("col2"));
        } catch (TableNotDisabledException e) {
        }
        this.admin.disableTable(tableName);
        this.admin.deleteTable(tableName);
    }

    @Test
    public void testAppend() throws IOException {
        TableName tableName = TableName.valueOf("testAppend");
        HTable table = util.createTable(tableName, HConstants.CATALOG_FAMILY);
        for (int i = 0; i <10000; i++) {
            Append append = new Append("row1".getBytes());
            append.add(HConstants.CATALOG_FAMILY, "cq".getBytes(), "value".getBytes());
            table.append(append);
        }
        table.close();
    }

    @Test
    public void testDeleteLastColumnFamily() throws Exception {
        TableName tableName = TableName.valueOf("testDeleteLastColumnFamily");
        util.createTable(tableName, HConstants.CATALOG_FAMILY).close();
        while (!this.admin.isTableEnabled(TableName.valueOf("testDeleteLastColumnFamily"))) {
            Thread.sleep(10);
        }

        // test for enabled table
        try {
            this.admin.deleteColumn(tableName, HConstants.CATALOG_FAMILY);
            fail("Should have failed to delete the only column family of a table");
        } catch (InvalidFamilyOperationException ex) {
            // expected
        }

        // test for disabled table
        this.admin.disableTable(tableName);

        try {
            this.admin.deleteColumn(tableName, HConstants.CATALOG_FAMILY);
            fail("Should have failed to delete the only column family of a table");
        } catch (InvalidFamilyOperationException ex) {
            // expected
        }
        this.admin.deleteTable(tableName);
    }

    @Test
    public void testMergeRegions() throws Exception {
        TableName tableName = TableName.valueOf("testMergeWithFullRegionName");
        HColumnDescriptor cd = new HColumnDescriptor("d");
        HTableDescriptor td = new HTableDescriptor(tableName);
        td.addFamily(cd);
        byte[][] splitRows = new byte[2][];
        splitRows[0] = new byte[]{(byte)'3'};
        splitRows[1] = new byte[]{(byte)'6'};
        try {
            util.createTable(td, splitRows);
            util.waitTableAvailable(tableName);

            List<HRegionInfo> tableRegions;
            HRegionInfo regionA;
            HRegionInfo regionB;

            // merge with full name
            tableRegions = admin.getTableRegions(tableName);
            assertEquals(3, admin.getTableRegions(tableName).size());
            regionA = tableRegions.get(0);
            regionB = tableRegions.get(1);
            admin.mergeRegions(regionA.getRegionName(), regionB.getRegionName(), false);
            Thread.sleep(1000);
            assertEquals(2, admin.getTableRegions(tableName).size());

            // merge with encoded name
            tableRegions = admin.getTableRegions(tableName);
            regionA = tableRegions.get(0);
            regionB = tableRegions.get(1);
            admin.mergeRegions(regionA.getEncodedNameAsBytes(), regionB.getEncodedNameAsBytes(), false);
            Thread.sleep(1000);
            assertEquals(1, admin.getTableRegions(tableName).size());
        } finally {
            this.admin.disableTable(tableName);
            this.admin.deleteTable(tableName);
        }
    }
}
