package hbase;

import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Durability;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.coprocessor.AggregationClient;
import org.apache.hadoop.hbase.client.coprocessor.LongColumnInterpreter;
import org.apache.hadoop.hbase.coprocessor.ColumnInterpreter;
import org.apache.hadoop.hbase.protobuf.generated.HBaseProtos.EmptyMsg;
import org.apache.hadoop.hbase.protobuf.generated.HBaseProtos.LongMsg;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TestAggregateClient {
    private static final TableName TEST_TABLE = TableName.valueOf("AggregateClientTest");
    private static final byte[] TEST_FAMILY = Bytes.toBytes("family");
    private static final byte[] TEST_QUALIFIER = Bytes.toBytes("qualifier");
    private static final String CoprocessorClassName = "org.apache.hadoop.hbase.coprocessor.AggregateImplementation";

    private static byte[] ROW = Bytes.toBytes("testRow");
    private static final int ROWSIZE = 2000;
    private static final int rowSeparator1 = 500;
    private static final int rowSeparator2 = 1200;
    private static byte[][] ROWS = makeN(ROW, ROWSIZE);
    private static HBaseUtility util = new HBaseUtility();

    private static Table table;
    private static Scan scan;
    private static AggregationClient aggregationClient;

    private static byte[][] makeN(byte[] base, int n) {
        byte[][] ret = new byte[n][];
        for (int i = 0; i < n; i++) {
            ret[i] = Bytes.add(base, Bytes.toBytes(i));
        }
        return ret;
    }

    @BeforeClass
    public static void setUp() throws Exception {
        final byte[][] SPLIT_KEYS = new byte[][] {ROWS[rowSeparator1], ROWS[rowSeparator2]};
        util.deleteTableIfExists(TEST_TABLE);
        HTableDescriptor desc = new HTableDescriptor(TEST_TABLE);
        HColumnDescriptor hcd = new HColumnDescriptor(TEST_FAMILY);
        desc.addFamily(hcd);
        desc.addCoprocessor(CoprocessorClassName);
        util.getAdmin().createTable(desc, SPLIT_KEYS);
        table = util.getConnection().getTable(TEST_TABLE);
        List<Put> puts = new ArrayList<>();
        for (int i = 0; i < ROWSIZE; i++) {
            Put put = new Put(ROWS[i]);
            put.setDurability(Durability.SKIP_WAL);//not write WAL, may cause data loss
            put.addColumn(TEST_FAMILY, TEST_QUALIFIER, Bytes.toBytes((long)i));
            puts.add(put);
            if (i % 100 == 0 || i == ROWSIZE - 1) {// batch put
                table.put(puts);
                puts.clear();
            }
        }
        table.close();

        scan = new Scan();
        scan.addColumn(TEST_FAMILY, TEST_QUALIFIER);
        aggregationClient = new AggregationClient(util.getConfiguration());
    }

    @Test
    public void testMedian() throws Throwable {
    final ColumnInterpreter<Long, Long, EmptyMsg, LongMsg, LongMsg> ci =
            new LongColumnInterpreter();
        long median = aggregationClient.median(TEST_TABLE, ci, scan);
        System.out.println("the median in the table " + median);
    }

    @Test
    public void testMin() throws Throwable {
         double min = aggregationClient.min(table, new LongColumnInterpreter(), scan);
        System.out.println("the min in the table " + min);
    }

    @Test
    public void testStd() throws Throwable {
        Object std = aggregationClient.std(table, new LongColumnInterpreter(), scan);
        System.out.println("the std in the table " + std);
    }

    @Test
    public void testAvg() throws Throwable {
        Object avg = aggregationClient.avg(table, new LongColumnInterpreter(), scan);
        System.out.println("the avg in the table " + avg);
    }

    @Test
    public void testMax() throws Throwable {
        double max = aggregationClient.max(table, new LongColumnInterpreter(), scan);
        System.out.println("the max in the table " + max);
    }

    @Test
    public void testRowCount() throws Throwable {
        long rowcnt = aggregationClient.rowCount(table, new LongColumnInterpreter(), scan);
        System.out.println("the row count in the table " + rowcnt);
    }

    @Test
    public void testSum() throws Throwable {
        long sum = aggregationClient.sum(table, new LongColumnInterpreter(), scan);
        System.out.println("the sum of the table " + sum);
    }

    @AfterClass
    public  static void tearDown() throws IOException {
        util.getAdmin().disableTable(TableName.valueOf("AggregateClientTest"));
        util.getAdmin().deleteTable(TableName.valueOf("AggregateClientTest"));
        util.getAdmin().close();
        util.getConnection().close();
    }
}
