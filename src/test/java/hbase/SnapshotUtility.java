package hbase;

import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.BufferedMutator;
import org.apache.hadoop.hbase.client.Durability;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.RegionReplicaUtil;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.protobuf.generated.HBaseProtos;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.MD5Hash;
import org.junit.Assert;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SnapshotUtility {
    private static byte[] KEYS = Bytes.toBytes("0123456789");

    public static void assertNoSnapshots(Admin admin) throws IOException {
        assertEquals("Have some previous snapshots", 0, admin.listSnapshots()
                .size());
    }

    public static List<HBaseProtos.SnapshotDescription> assertOneSnapshotThatMatches(
            Admin admin, byte[] snapshot, TableName tableName) throws IOException {
        return assertOneSnapshotThatMatches(admin, Bytes.toString(snapshot),
                tableName);
    }

    public static List<HBaseProtos.SnapshotDescription> assertOneSnapshotThatMatches(
            Admin admin, String snapshotName, TableName tableName)
            throws IOException {
        // list the snapshot
        List<HBaseProtos.SnapshotDescription> snapshots = admin.listSnapshots();

        assertEquals("Should only have 1 snapshot", 1, snapshots.size());
        assertEquals(snapshotName, snapshots.get(0).getName());
        assertEquals(tableName, TableName.valueOf(snapshots.get(0).getTable()));

        return snapshots;
    }

    public static void createTable(final HBaseUtility util, final TableName tableName,
                                   int regionReplication, final byte[]... families) throws IOException, InterruptedException {
        HTableDescriptor htd = new HTableDescriptor(tableName);
        htd.setRegionReplication(regionReplication);
        for (byte[] family : families) {
            HColumnDescriptor hcd = new HColumnDescriptor(family);
            htd.addFamily(hcd);
        }
        byte[][] splitKeys = getSplitKeys();
        util.createTable(htd, splitKeys);
        assertEquals((splitKeys.length + 1) * regionReplication,
                util.getAdmin().getTableRegions(tableName).size());
    }

    private static Put createPut(final byte[][] families, final byte[] key, final byte[] value) {
        byte[] q = Bytes.toBytes("q");
        Put put = new Put(key);
        put.setDurability(Durability.SKIP_WAL);
        for (byte[] family: families) {
            put.add(family, q, value);
        }
        return put;
    }

    public static void deleteAllSnapshots(final Admin admin)
            throws IOException {
        // Delete all the snapshots
        for (HBaseProtos.SnapshotDescription snapshot: admin.listSnapshots()) {
            admin.deleteSnapshot(snapshot.getName());
        }
        SnapshotUtility.assertNoSnapshots(admin);
    }

    public static byte[][] getSplitKeys() {
        byte[][] splitKeys = new byte[KEYS.length-2][];
        for (int i = 0; i < splitKeys.length; ++i) {
            splitKeys[i] = new byte[] { KEYS[i+1] };
        }
        return splitKeys;
    }

    public static void loadData(final HBaseUtility util, final TableName tableName, int rows,
                                byte[]... families) throws IOException, InterruptedException {
        try (BufferedMutator mutator = util.getConnection().getBufferedMutator(tableName)) {
            loadData(util, mutator, rows, families);
        }
    }

    public static void loadData(final HBaseUtility util, final BufferedMutator mutator, int rows,
                                byte[]... families) throws IOException, InterruptedException {
        // Ensure one row per region
        assertTrue(rows >= KEYS.length);
        for (byte k0 : KEYS) {
            byte[] k = new byte[]{k0};
            byte[] value = Bytes.add(Bytes.toBytes(System.currentTimeMillis()), k);
            byte[] key = Bytes.add(k, Bytes.toBytes(MD5Hash.getMD5AsHex(value)));
            final byte[][] families1 = families;
            final byte[] key1 = key;
            final byte[] value1 = value;
            mutator.mutate(createPut(families1, key1, value1));
            rows--;
        }
    }

    public static void verifyRowCount(final HBaseUtility util, final TableName tableName,
                                      long expectedRows) throws IOException {
        Table table = new HTable(util.getConfiguration(), tableName);
        try {
            assertEquals(expectedRows, util.countRows(table));
        } finally {
            table.close();
        }
    }

    public static void verifyReplicasCameOnline(TableName tableName, Admin admin,
                                                int regionReplication) throws IOException {
        List<HRegionInfo> regions = admin.getTableRegions(tableName);
        HashSet<HRegionInfo> set = new HashSet<>();
        for (HRegionInfo hri : regions) {
            set.add(RegionReplicaUtil.getRegionInfoForDefaultReplica(hri));
            for (int i = 0; i < regionReplication; i++) {
                HRegionInfo replica = RegionReplicaUtil.getRegionInfoForReplica(hri, i);
                if (!regions.contains(replica)) {
                    Assert.fail(replica + " is not contained in the list of online regions");
                }
            }
        }
        assert(set.size() == getSplitKeys().length + 1);
    }
}
