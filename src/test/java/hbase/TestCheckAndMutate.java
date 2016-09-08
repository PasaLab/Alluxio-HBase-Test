package hbase;

import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.RowMutations;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.filter.CompareFilter;
import org.apache.hadoop.hbase.regionserver.NoSuchColumnFamilyException;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestCheckAndMutate {
    private static HBaseUtility util = new HBaseUtility();
    private static Admin admin;

    @BeforeClass
    public static void setUp() throws Exception {
        admin = util.getAdmin();
    }

    @Test
    public void testCheckAndMutate() throws Exception {
        final TableName tableName = TableName.valueOf("TestPutWithDelete");
        final byte[] rowKey = Bytes.toBytes("12345");
        final byte[] family = Bytes.toBytes("cf");
        Table table = util.createTable(tableName, family);
        try {
            // put one row
            Put put = new Put(rowKey);
            put.addColumn(family, Bytes.toBytes("A"), Bytes.toBytes("a"));
            put.addColumn(family, Bytes.toBytes("B"), Bytes.toBytes("b"));
            put.addColumn(family, Bytes.toBytes("C"), Bytes.toBytes("c"));
            table.put(put);
            // get row back and assert the values
            Get get = new Get(rowKey);
            Result result = table.get(get);
            assertTrue("Column A value should be a",
                    Bytes.toString(result.getValue(family, Bytes.toBytes("A"))).equals("a"));
            assertTrue("Column B value should be b",
                    Bytes.toString(result.getValue(family, Bytes.toBytes("B"))).equals("b"));
            assertTrue("Column C value should be c",
                    Bytes.toString(result.getValue(family, Bytes.toBytes("C"))).equals("c"));

            // put the same row again with C column deleted
            RowMutations rm = new RowMutations(rowKey);
            put = new Put(rowKey);
            put.addColumn(family, Bytes.toBytes("A"), Bytes.toBytes("a"));
            put.addColumn(family, Bytes.toBytes("B"), Bytes.toBytes("b"));
            rm.add(put);
            Delete del = new Delete(rowKey);
            del.addColumn(family, Bytes.toBytes("C"));
            rm.add(del);
            boolean res = table.checkAndMutate(rowKey, family, Bytes.toBytes("A"), CompareFilter.CompareOp.EQUAL,
                    Bytes.toBytes("a"), rm);
            assertTrue(res);

            // get row back and assert the values
            get = new Get(rowKey);
            result = table.get(get);
            assertTrue("Column A value should be a",
                    Bytes.toString(result.getValue(family, Bytes.toBytes("A"))).equals("a"));
            assertTrue("Column B value should be b",
                    Bytes.toString(result.getValue(family, Bytes.toBytes("B"))).equals("b"));
            assertTrue("Column C should not exist",
                    result.getValue(family, Bytes.toBytes("C")) == null);

            //Test that we get a region level exception
            try {
                Put p = new Put(rowKey);
                p.addColumn(new byte[]{'b', 'o', 'g', 'u', 's'}, new byte[]{'A'},  new byte[0]);
                rm = new RowMutations(rowKey);
                rm.add(p);
                table.checkAndMutate(rowKey, family, Bytes.toBytes("A"), CompareFilter.CompareOp.EQUAL,
                        Bytes.toBytes("a"), rm);
                fail("Expected NoSuchColumnFamilyException");
            } catch(NoSuchColumnFamilyException e) {
                //Expected
            }
        } finally {
            table.close();
        }
    }
}
