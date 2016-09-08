package hbase;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.hbase.Abortable;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.NamespaceDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.TableNotFoundException;
import org.apache.hadoop.hbase.Waiter;
import org.apache.hadoop.hbase.Waiter.Predicate;
import org.apache.hadoop.hbase.ZooKeeperConnectionException;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Durability;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.fs.HFileSystem;
import org.apache.hadoop.hbase.regionserver.BloomType;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.zookeeper.ZooKeeperWatcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class HBaseUtility {
    public static final byte[][] KEYS_FOR_HBA_CREATE_TABLE = {
            Bytes.toBytes("bbb"),
            Bytes.toBytes("ccc"), Bytes.toBytes("ddd"), Bytes.toBytes("eee"),
            Bytes.toBytes("fff"), Bytes.toBytes("ggg"), Bytes.toBytes("hhh"),
            Bytes.toBytes("iii"), Bytes.toBytes("jjj"), Bytes.toBytes("kkk"),
            Bytes.toBytes("lll"), Bytes.toBytes("mmm"), Bytes.toBytes("nnn"),
            Bytes.toBytes("ooo"), Bytes.toBytes("ppp"), Bytes.toBytes("qqq"),
            Bytes.toBytes("rrr"), Bytes.toBytes("sss"), Bytes.toBytes("ttt"),
            Bytes.toBytes("uuu"), Bytes.toBytes("vvv"), Bytes.toBytes("www"),
            Bytes.toBytes("xxx"), Bytes.toBytes("yyy"), Bytes.toBytes("zzz")
    };
    public static final byte[][] KEYS = {
            HConstants.EMPTY_BYTE_ARRAY, Bytes.toBytes("bbb"),
            Bytes.toBytes("ccc"), Bytes.toBytes("ddd"), Bytes.toBytes("eee"),
            Bytes.toBytes("fff"), Bytes.toBytes("ggg"), Bytes.toBytes("hhh"),
            Bytes.toBytes("iii"), Bytes.toBytes("jjj"), Bytes.toBytes("kkk"),
            Bytes.toBytes("lll"), Bytes.toBytes("mmm"), Bytes.toBytes("nnn"),
            Bytes.toBytes("ooo"), Bytes.toBytes("ppp"), Bytes.toBytes("qqq"),
            Bytes.toBytes("rrr"), Bytes.toBytes("sss"), Bytes.toBytes("ttt"),
            Bytes.toBytes("uuu"), Bytes.toBytes("vvv"), Bytes.toBytes("www"),
            Bytes.toBytes("xxx"), Bytes.toBytes("yyy")
    };
    public static final byte[][] ROWS = new byte[(int) Math.pow('z' - 'a' + 1, 3)][3]; // ~52KB
    public final static byte [] fam1 = Bytes.toBytes("colfamily11");

    static {
        int i = 0;
        for (byte b1 = 'a'; b1 <= 'z'; b1++) {
            for (byte b2 = 'a'; b2 <= 'z'; b2++) {
                for (byte b3 = 'a'; b3 <= 'z'; b3++) {
                    ROWS[i][0] = b1;
                    ROWS[i][1] = b2;
                    ROWS[i][2] = b3;
                    i++;
                }
            }
        }
    }

    private volatile Connection conn = null;
    private Configuration conf = HBaseConfiguration.create();
    private Admin admin = null;

    public static void createTable(Admin admin, String tableName, String... cfs) throws IOException {
        if (admin.tableExists(TableName.valueOf(tableName))) {
            admin.disableTable(TableName.valueOf(tableName));
            admin.deleteTable(TableName.valueOf(tableName));
            System.out.println("table "+TableName.valueOf(tableName) + " exists and has been deleted!");
        }
        HTableDescriptor table = new HTableDescriptor(TableName.valueOf(tableName));
        for(String cf:cfs) {
            table.addFamily(new HColumnDescriptor(cf));
        }
        System.out.println("Creating table " + tableName + "...");
        admin.createTable(table);
        System.out.println("Done.");
    }

    public static ZooKeeperWatcher getZooKeeperWatcher(
            HBaseUtility util) throws ZooKeeperConnectionException,
            IOException {
        ZooKeeperWatcher zkw = new ZooKeeperWatcher(util.getConfiguration(),
                "unittest", new Abortable() {
            boolean aborted = false;

            @Override
            public void abort(String why, Throwable e) {
                aborted = true;
                throw new RuntimeException("Fatal ZK error, why=" + why, e);
            }

            @Override
            public boolean isAborted() {
                return aborted;
            }
        });
        return zkw;
    }

    public Configuration getConfiguration() {
        return this.conf;
    }

    public Connection getConnection() throws IOException {
        if (this.conn == null) {
            this.conn = ConnectionFactory.createConnection(this.conf);
        }
        return this.conn;
    }

    public Admin getAdmin()
            throws IOException {
        if (admin == null){
            this.admin = getConnection().getAdmin();
        }
        return admin;
    }

    public HTable createTable(TableName tableName, byte[] family)
            throws IOException{
        return createTable(tableName, new byte[][]{family});
    }

    public HTable createTable(TableName tableName, byte[][] families)
            throws IOException {
        return createTable(tableName, families, (byte[][])null);
    }

    public HTable createTable(TableName tableName, byte[][] families, byte[][] splitKeys) throws IOException {
        deleteTableIfExists(tableName);
        HTableDescriptor htd = new HTableDescriptor(tableName);
        for (byte[] family : families) {
            HColumnDescriptor hcd = new HColumnDescriptor(family);

            hcd.setBloomFilterType(BloomType.NONE);//disable blooms
            htd.addFamily(hcd);
        }
        getAdmin().createTable(htd, splitKeys);
        waitUntilAllRegionsAssigned(htd.getTableName());
        return (HTable) getConnection().getTable(htd.getTableName());
    }

    public HTable createTable(TableName tableName, byte[] family, byte[][] splitRows)
            throws IOException {
        deleteTableIfExists(tableName);
        HTableDescriptor desc = new HTableDescriptor(tableName);
        HColumnDescriptor hcd = new HColumnDescriptor(family);
        desc.addFamily(hcd);
        getAdmin().createTable(desc, splitRows);
        waitUntilAllRegionsAssigned(tableName);
        return (HTable) getConnection().getTable(tableName);
    }

    public HTable createTable(TableName tableName, byte[][] families,
                              int numVersions, int blockSize) throws IOException {
        deleteTableIfExists(tableName);
        HTableDescriptor desc = new HTableDescriptor(tableName);
        for (byte[] family : families) {
            HColumnDescriptor hcd = new HColumnDescriptor(family)
                    .setMaxVersions(numVersions)
                    .setBlocksize(blockSize);
            desc.addFamily(hcd);
        }
        getAdmin().createTable(desc);
        waitUntilAllRegionsAssigned(tableName);
        return new HTable(new Configuration(getConfiguration()), tableName);
    }

    public HTable createTable(HTableDescriptor htd, byte[][] splitRows)
            throws IOException {
        deleteTableIfExists(htd.getTableName());
        getAdmin().createTable(htd, splitRows);
        waitUntilAllRegionsAssigned(htd.getTableName());
        return new HTable(getConfiguration(), htd.getTableName());
    }

    public HTable createTable(TableName tableName, byte[] family, int numVersions)
            throws IOException {
        return createTable(tableName, new byte[][]{family}, numVersions);
    }

    public HTable createTable(TableName tableName, byte[][] families,
                              int numVersions)
            throws IOException {
        return createTable(tableName, families, numVersions, (byte[][]) null);
    }

    public HTable createTable(TableName tableName, byte[][] families, int numVersions,
                              byte[][] splitKeys) throws IOException {
        deleteTableIfExists(tableName);
        HTableDescriptor desc = new HTableDescriptor(tableName);
        for (byte[] family : families) {
            HColumnDescriptor hcd = new HColumnDescriptor(family).setMaxVersions(numVersions);
            desc.addFamily(hcd);
        }
        getAdmin().createTable(desc, splitKeys);
        // HBaseAdmin only waits for regions to appear in hbase:meta we should wait until they are assigned
        waitUntilAllRegionsAssigned(tableName);
        return new HTable(new Configuration(getConfiguration()), tableName);
    }

    public HTable createTable(byte[] tableName, byte[][] families)
            throws IOException {
        return createTable(tableName, families, new Configuration(getConfiguration()));
    }

    public HTable createTable(byte[] tableName, byte[][] families,
                              final Configuration c)
            throws IOException {
        deleteTableIfExists(TableName.valueOf(tableName));
        HTableDescriptor desc = new HTableDescriptor(TableName.valueOf(tableName));
        for(byte[] family : families) {
            HColumnDescriptor hcd = new HColumnDescriptor(family);
            // Disable blooms (they are on by default as of 0.95) but we disable them here because
            // tests have hard coded counts of what to expect in block cache, etc., and blooms being
            // on is interfering.
            hcd.setBloomFilterType(BloomType.NONE);
            desc.addFamily(hcd);
        }
        getAdmin().createTable(desc);
        return new HTable(c, desc.getTableName());
    }

    public HTable createTable(TableName tableName, byte[][] families,
                              int numVersions, byte[] startKey, byte[] endKey, int numRegions)
            throws IOException{
        deleteTableIfExists(tableName);
        HTableDescriptor desc = new HTableDescriptor(tableName);
        for (byte[] family : families) {
            HColumnDescriptor hcd = new HColumnDescriptor(family)
                    .setMaxVersions(numVersions);
            desc.addFamily(hcd);
        }
        getAdmin().createTable(desc, startKey, endKey, numRegions);
        // HBaseAdmin only waits for regions to appear in hbase:meta we should wait until they are assigned
        waitUntilAllRegionsAssigned(tableName);
        return new HTable(getConfiguration(), tableName);
    }

    public HTable createTable(byte[] tableName, byte[] family)
            throws IOException{
        return createTable(TableName.valueOf(tableName), new byte[][]{family});
    }


    public Table createTable(TableName tableName, String family)
            throws IOException{
        return createTable(tableName, new String[]{family});
    }

    public Table createTable(TableName tableName, String[] families)
            throws IOException {
        List<byte[]> fams = new ArrayList<>(families.length);
        for (String family : families) {
            fams.add(Bytes.toBytes(family));
        }
        return createTable(tableName, fams.toArray(new byte[0][]));
    }

    public HTable createTable(HTableDescriptor htd, byte[][] families, Configuration c)
            throws IOException {
        return createTable(htd, families, null, c);
    }

    public HTable createTable(HTableDescriptor htd, byte[][] families, byte[][] splitKeys,
                              Configuration c) throws IOException {
        deleteTableIfExists(htd.getTableName());
        for (byte[] family : families) {
            HColumnDescriptor hcd = new HColumnDescriptor(family);
            hcd.setBloomFilterType(BloomType.NONE);
            htd.addFamily(hcd);
        }
        getAdmin().createTable(htd, splitKeys);
        waitUntilAllRegionsAssigned(htd.getTableName());
        return (HTable) getConnection().getTable(htd.getTableName());
    }

    public HTable createTable(TableName tableName, byte[][] families,
                              final Configuration c)
            throws IOException {
        return createTable(tableName, families, null, c);
    }

    public HTable createTable(TableName tableName, byte[][] families, byte[][] splitKeys,
                              final Configuration c) throws IOException {
        return createTable(new HTableDescriptor(tableName), families, splitKeys, c);
    }

    public Table createMultiRegionTable(TableName tableName, byte[] family) throws IOException {
        return createTable(tableName, family, KEYS_FOR_HBA_CREATE_TABLE);
    }

    public void loadNumericRows(final Table t, final byte[] f, int startRow, int endRow)
            throws IOException {
        for (int i = startRow; i < endRow; i++) {
            byte[] data = Bytes.toBytes(String.valueOf(i));
            Put put = new Put(data);
            put.addColumn(f, null, data);
            t.put(put);
        }
    }

    public int countRows(final Table table) throws IOException {
        Scan scan = new Scan();
        ResultScanner results = table.getScanner(scan);
        int count = 0;
        for (Result res : results) {
            count++;
        }
        results.close();
        return count;
    }

    void deleteTableIfExists(TableName tableName) throws IOException {
        if (getAdmin().tableExists(tableName)) {
            if (getAdmin().isTableEnabled(tableName))
                getAdmin().disableTable(tableName);
            getAdmin().deleteTable(tableName);
            System.out.println("table "+tableName.getNameAsString() + " exists and has been deleted!");
        }
    }

    public void deleteNamespaceIfExsits(String nsName) throws IOException {
        for (NamespaceDescriptor nsd : getAdmin().listNamespaceDescriptors()) {
            if (nsd.getName().equals(nsName)) {
                getAdmin().deleteNamespace(nsName);
                return;
            }
        }
    }

    public void waitTableAvailable(TableName table)
            throws InterruptedException, IOException {
        waitTableAvailable(table.getName(), 30000);
    }

    public void waitTableAvailable(byte[] table, long timeoutMillis)
            throws InterruptedException, IOException {
        waitTableAvailable(getAdmin(), table, timeoutMillis);
    }

    public void waitTableAvailable(Admin admin, byte[] table, long timeoutMillis)
            throws InterruptedException, IOException {
        long startWait = System.currentTimeMillis();
        while (!admin.isTableAvailable(TableName.valueOf(table))) {
            assertTrue("Timed out waiting for table to become available " +
                            Bytes.toStringBinary(table),
                    System.currentTimeMillis() - startWait < timeoutMillis);
            Thread.sleep(200);
        }
    }

    public void waitUntilAllRegionsAssigned(final TableName tableName) throws IOException {
        waitUntilAllRegionsAssigned(tableName,this.conf.getLong("hbase.client.sync.wait.timeout.msec", 60000));
    }

    public void waitUntilAllRegionsAssigned(final TableName tableName, final long timeout)
            throws IOException {
        final Table meta = new HTable(getConfiguration(), TableName.META_TABLE_NAME);
        try {
            waitFor(timeout, 200, true, new Predicate<IOException>() {
                @Override
                public boolean evaluate() throws IOException {
                    boolean allRegionsAssigned = true;
                    Scan scan = new Scan();
                    scan.addFamily(HConstants.CATALOG_FAMILY);
                    ResultScanner s = meta.getScanner(scan);
                    try {
                        Result r;
                        while ((r = s.next()) != null) {
                            byte[] b = r.getValue(HConstants.CATALOG_FAMILY, HConstants.REGIONINFO_QUALIFIER);
                            HRegionInfo info = HRegionInfo.parseFromOrNull(b);
                            if (info != null && info.getTable().equals(tableName)) {
                                b = r.getValue(HConstants.CATALOG_FAMILY, HConstants.SERVER_QUALIFIER);
                                allRegionsAssigned &= (b != null);
                            }
                        }
                    } finally {
                        s.close();
                    }
                    return allRegionsAssigned;
                }
            });
        } finally {
            meta.close();
        }
    }

    public <E extends Exception> long waitFor(long timeout, Predicate<E> predicate)
            throws E {
        return Waiter.waitFor(this.conf, timeout, predicate);
    }

    public <E extends Exception> long waitFor(long timeout, long interval,
                                              boolean failIfTimeout, Predicate<E> predicate) throws E {
        return Waiter.waitFor(this.conf, timeout, interval, failIfTimeout, predicate);
    }

    public void waitTableEnabled(TableName table)
            throws InterruptedException, IOException {
        waitTableEnabled(table, 30000);
    }

    public void waitTableEnabled(TableName table, long timeoutMillis)
            throws IOException {
        waitFor(timeoutMillis, predicateTableEnabled(table));
    }

    public Predicate<IOException> predicateTableEnabled(final TableName tableName) {
        return new Waiter.ExplainingPredicate<IOException>() {
            @Override
            public String explainFailure() throws IOException {
                return explainTableState(tableName);
            }
            @Override
            public boolean evaluate() throws IOException {
                return getAdmin().tableExists(tableName) && getAdmin().isTableEnabled(tableName);
            }
        };
    }

    public String explainTableState(TableName tableName) throws IOException {
        try {
            if (getAdmin().isTableEnabled(tableName))
                return "table enabled in zk";
            else if (getAdmin().isTableDisabled(tableName))
                return "table disabled in zk";
            else
                return "table in uknown state";
        } catch (TableNotFoundException e) {
            return "table not exists";
        }
    }

    public int loadTable(final Table t, final byte[] f, boolean writeToWAL) throws IOException {
        return loadTable(t, new byte[][] {f}, null, writeToWAL);
    }

    public int loadTable(final Table t, final byte[][] f, byte[] value, boolean writeToWAL) throws IOException {
        List<Put> puts = new ArrayList<>();
        for (byte[] row : HBaseUtility.ROWS) {
            Put put = new Put(row);
            put.setDurability(writeToWAL ? Durability.USE_DEFAULT : Durability.SKIP_WAL);
            for (int i = 0; i < f.length; i++) {
                put.add(f[i], null, value != null ? value : row);
            }
            puts.add(put);
        }
        t.put(puts);
        return puts.size();
    }

    public int loadTable(final Table t, final byte[] f) throws IOException {
        return loadTable(t, new byte[][] {f});
    }

    public int loadTable(final Table t, final byte[][] f) throws IOException {
        return loadTable(t, f, null);
    }

    public int loadTable(final Table t, final byte[][] f, byte[] value) throws IOException {
        return loadTable(t, f, value, true);
    }

    public FileSystem getTestFileSystem() throws IOException {
        return HFileSystem.get(conf);
    }
}
