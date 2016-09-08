package hbase;

import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.NamespaceDescriptor;
import org.apache.hadoop.hbase.NamespaceNotFoundException;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.master.snapshot.SnapshotManager;
import org.apache.hadoop.hbase.snapshot.SnapshotDoesNotExistException;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

public class TestCloneSnapshotFromClient {
    private static HBaseUtility util = new HBaseUtility();
    private final byte[] FAMILY = Bytes.toBytes("cf");

    private byte[] emptySnapshot;
    private byte[] snapshotName0;
    private byte[] snapshotName1;
    private byte[] snapshotName2;
    private int snapshot0Rows;
    private int snapshot1Rows;
    private TableName tableName;
    private Admin admin;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        util.getConfiguration().setBoolean(SnapshotManager.HBASE_SNAPSHOT_ENABLED, true);
        util.getConfiguration().setBoolean("hbase.online.schema.update.enable", true);
        util.getConfiguration().setInt("hbase.hstore.compactionThreshold", 10);
        util.getConfiguration().setInt("hbase.regionserver.msginterval", 100);
        util.getConfiguration().setInt("hbase.client.pause", 250);
        util.getConfiguration().setInt(HConstants.HBASE_CLIENT_RETRIES_NUMBER, 6);
        util.getConfiguration().setBoolean(
                "hbase.master.enabletable.roundrobin", true);
    }

    @AfterClass
    public static void tearDownAfterClass() {
        util.getConfiguration().setBoolean(SnapshotManager.HBASE_SNAPSHOT_ENABLED, true);
        util.getConfiguration().setBoolean("hbase.online.schema.update.enable", true);
        util.getConfiguration().setInt("hbase.hstore.compactionThreshold", 3);
        util.getConfiguration().setInt("hbase.regionserver.msginterval", 3000);
        util.getConfiguration().setInt("hbase.client.pause", 250);
        util.getConfiguration().setInt(HConstants.HBASE_CLIENT_RETRIES_NUMBER, 35);
        util.getConfiguration().setBoolean(
                "hbase.master.enabletable.roundrobin", true);
    }

    @Before
    public void setUp() throws Exception {
        admin = util.getAdmin();

        long tid = System.currentTimeMillis();
        tableName = TableName.valueOf("testtb-" + tid);
        emptySnapshot = Bytes.toBytes("emptySnaptb-" + tid);
        snapshotName0 = Bytes.toBytes("snaptb0-" + tid);
        snapshotName1 = Bytes.toBytes("snaptb1-" + tid);
        snapshotName2 = Bytes.toBytes("snaptb2-" + tid);

        // create Table and disable it
        SnapshotUtility.createTable(util, tableName, getNumReplicas(), FAMILY);
        admin.disableTable(tableName);

        // take an empty snapshot
        admin.snapshot(emptySnapshot, tableName);

        // enable table and insert data
        admin.enableTable(tableName);
        SnapshotUtility.loadData(util, tableName, 500, FAMILY);
        try (Table table = util.getConnection().getTable(tableName)){
            snapshot0Rows = util.countRows(table);
        }
        admin.disableTable(tableName);

        // take a snapshot
        admin.snapshot(snapshotName0, tableName);

        // enable table and insert more data
        admin.enableTable(tableName);
        SnapshotUtility.loadData(util, tableName, 500, FAMILY);
        try (Table table = util.getConnection().getTable(tableName)){
            snapshot1Rows = util.countRows(table);
        }
        admin.disableTable(tableName);

        // take a snapshot of the updated table
        admin.snapshot(snapshotName1, tableName);

        // re-enable table
        admin.enableTable(tableName);
    }

    @After
    public void tearDown() throws Exception {
            util.deleteTableIfExists(tableName);
        SnapshotUtility.deleteAllSnapshots(admin);
    }

    protected int getNumReplicas() {
        return 1;
    }

    @Test(expected=SnapshotDoesNotExistException.class)
    public void testCloneNonExistentSnapshot() throws IOException, InterruptedException {
        String snapshotName = "random-snapshot-" + System.currentTimeMillis();
        TableName tableName = TableName.valueOf("random-table-" + System.currentTimeMillis());
        admin.cloneSnapshot(snapshotName, tableName);
    }

    @Test(expected = NamespaceNotFoundException.class)
    public void testCloneOnMissingNamespace() throws IOException, InterruptedException {
        TableName clonedTableName = TableName.valueOf("unknownNS:clonetb");
        admin.cloneSnapshot(snapshotName1, clonedTableName);
    }

    @Test
    public void testCloneSnapshot() throws IOException, InterruptedException {
        TableName clonedTableName = TableName.valueOf("clonedtb-" + System.currentTimeMillis());
        testCloneSnapshot(clonedTableName, snapshotName0, snapshot0Rows);
        testCloneSnapshot(clonedTableName, snapshotName1, snapshot1Rows);
        testCloneSnapshot(clonedTableName, emptySnapshot, 0);
    }

    private void testCloneSnapshot(final TableName tableName, final byte[] snapshotName,
                                   int snapshotRows) throws IOException, InterruptedException {
        // create a new table from snapshot
        admin.cloneSnapshot(snapshotName, tableName);
        SnapshotUtility.verifyRowCount(util, tableName, snapshotRows);

        verifyReplicasCameOnline(tableName);
        util.deleteTableIfExists(tableName);
    }

    protected void verifyReplicasCameOnline(TableName tableName) throws IOException {
        SnapshotUtility.verifyReplicasCameOnline(tableName, admin, getNumReplicas());
    }

    @Test
    public void testCloneSnapshotCrossNamespace() throws IOException, InterruptedException {
        String nsName = "testCloneSnapshotCrossNamespace";
        util.deleteNamespaceIfExsits(nsName);
        admin.createNamespace(NamespaceDescriptor.create(nsName).build());
        TableName clonedTableName =
                TableName.valueOf(nsName, "clonedtb-" + System.currentTimeMillis());
        testCloneSnapshot(clonedTableName, snapshotName0, snapshot0Rows);
        testCloneSnapshot(clonedTableName, snapshotName1, snapshot1Rows);
        testCloneSnapshot(clonedTableName, emptySnapshot, 0);
    }

    /**
     * Verify that tables created from the snapshot are still alive after source table deletion.
     */
    @Test
    public void testCloneLinksAfterDelete() throws IOException, InterruptedException {
        // Clone a table from the first snapshot
        TableName clonedTableName = TableName.valueOf("clonedtb1-" + System.currentTimeMillis());
        admin.cloneSnapshot(snapshotName0, clonedTableName);
        SnapshotUtility.verifyRowCount(util, clonedTableName, snapshot0Rows);

        // Take a snapshot of this cloned table.
        admin.disableTable(clonedTableName);
        admin.snapshot(snapshotName2, clonedTableName);

        // Clone the snapshot of the cloned table
        TableName clonedTableName2 = TableName.valueOf("clonedtb2-" + System.currentTimeMillis());
        admin.cloneSnapshot(snapshotName2, clonedTableName2);
        SnapshotUtility.verifyRowCount(util, clonedTableName2, snapshot0Rows);
        admin.disableTable(clonedTableName2);

        // Remove the original table
        util.deleteTableIfExists(tableName);

        // Verify the first cloned table
        admin.enableTable(clonedTableName);
        SnapshotUtility.verifyRowCount(util, clonedTableName, snapshot0Rows);

        // Verify the second cloned table
        admin.enableTable(clonedTableName2);
        SnapshotUtility.verifyRowCount(util, clonedTableName2, snapshot0Rows);
        admin.disableTable(clonedTableName2);

        // Delete the first cloned table
        util.deleteTableIfExists(clonedTableName);

        // Verify the second cloned table
        admin.enableTable(clonedTableName2);
        SnapshotUtility.verifyRowCount(util, clonedTableName2, snapshot0Rows);

        // Clone a new table from cloned
        TableName clonedTableName3 = TableName.valueOf("clonedtb3-" + System.currentTimeMillis());
        admin.cloneSnapshot(snapshotName2, clonedTableName3);
        SnapshotUtility.verifyRowCount(util, clonedTableName3, snapshot0Rows);

        // Delete the cloned tables
        util.deleteTableIfExists(clonedTableName2);
        util.deleteTableIfExists(clonedTableName3);
        admin.deleteSnapshot(snapshotName2);
    }
}
