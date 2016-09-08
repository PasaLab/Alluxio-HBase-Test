package hbase;

import com.google.protobuf.RpcController;
import com.google.protobuf.ServiceException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.CallQueueTooBigException;
import org.apache.hadoop.hbase.CoordinatedStateManager;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.MultiActionResultTooLarge;
import org.apache.hadoop.hbase.NotServingRegionException;
import org.apache.hadoop.hbase.RegionTooBusyException;
import org.apache.hadoop.hbase.RetryImmediatelyException;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Append;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Increment;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.RowMutations;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.exceptions.RegionOpeningException;
import org.apache.hadoop.hbase.protobuf.generated.ClientProtos;
import org.apache.hadoop.hbase.protobuf.generated.ClientProtos.GetResponse;
import org.apache.hadoop.hbase.quotas.ThrottlingException;
import org.apache.hadoop.hbase.regionserver.HRegionServer;
import org.apache.hadoop.hbase.regionserver.RSRpcServices;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TestMetaCache {
  private final static HBaseUtility util = new HBaseUtility();
  private static final TableName TABLE_NAME = TableName.valueOf("test_table");
  private static final byte[] FAMILY = Bytes.toBytes("fam1");
  private static final byte[] QUALIFIER = Bytes.toBytes("qual");

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    Configuration conf = util.getConfiguration();
    conf.set("hbase.client.retries.number", "1");
    util.waitUntilAllRegionsAssigned(TABLE_NAME.META_TABLE_NAME);
  }

  @AfterClass
  public static void tearDownAfterClass() {
    util.getConfiguration().setInt("hbase.client.retries.number", 36);
  }

  @Before
  public void setup() throws Exception {

    HTableDescriptor table = new HTableDescriptor(TABLE_NAME);
    HColumnDescriptor fam = new HColumnDescriptor(FAMILY);
    fam.setMaxVersions(2);
    table.addFamily(fam);
    try (Admin admin = util.getAdmin()) {
      util.deleteTableIfExists(TABLE_NAME);
      admin.createTable(table, HBaseUtility.KEYS_FOR_HBA_CREATE_TABLE);
    }
    util.waitUntilAllRegionsAssigned(TABLE_NAME);
  }

  @Test
  public void testPreserveMetaCacheOnException() throws Exception {
    Table table = util.getConnection().getTable(TABLE_NAME);
     byte[] row = Bytes.toBytes("row");
    Put put = new Put(row);
    put.addColumn(FAMILY, QUALIFIER, Bytes.toBytes(10));
    Get get = new Get(row);
    Append append = new Append(row);
    append.add(FAMILY, QUALIFIER, Bytes.toBytes(11));
    Increment increment = new Increment(row);
    increment.addColumn(FAMILY, QUALIFIER, 10);
    Delete delete = new Delete(row);
    delete.addColumn(FAMILY, QUALIFIER);
    RowMutations mutations = new RowMutations(row);
    mutations.add(put);
    mutations.add(delete);

    for (int i = 0; i < 50; i++) {
        table.put(put);
        table.get(get);
        table.append(append);
        table.increment(increment);
        table.delete(delete);
        table.mutateRow(mutations);
    }
  }

  public static List<Throwable> metaCachePreservingExceptions() {
    return new ArrayList<Throwable>() {{
      add(new RegionOpeningException(" "));
      add(new RegionTooBusyException());
      add(new ThrottlingException(" "));
      add(new MultiActionResultTooLarge(" "));
      add(new RetryImmediatelyException(" "));
      add(new CallQueueTooBigException());
    }};
  }

  protected static class RegionServerWithFakeRpcServices extends HRegionServer {

    public RegionServerWithFakeRpcServices(Configuration conf, CoordinatedStateManager cp)
      throws IOException, InterruptedException {
      super(conf, cp);
    }

    @Override
    protected RSRpcServices createRpcServices() throws IOException {
      return new FakeRSRpcServices(this);
    }
  }

  protected static class FakeRSRpcServices extends RSRpcServices {

    private int numReqs = -1;
    private int expCount = -1;
    private List<Throwable> metaCachePreservingExceptions = metaCachePreservingExceptions();

    public FakeRSRpcServices(HRegionServer rs) throws IOException {
      super(rs);
    }

    @Override
    public GetResponse get(final RpcController controller,
                           final ClientProtos.GetRequest request) throws ServiceException {
      throwSomeExceptions();
      return super.get(controller, request);
    }

    @Override
    public ClientProtos.MutateResponse mutate(final RpcController controller,
                                              final ClientProtos.MutateRequest request) throws ServiceException {
      throwSomeExceptions();
      return super.mutate(controller, request);
    }

    @Override
    public ClientProtos.ScanResponse scan(final RpcController controller,
                                          final ClientProtos.ScanRequest request) throws ServiceException {
      throwSomeExceptions();
      return super.scan(controller, request);
    }

    /**
     * Throw some exceptions. Mostly throw exceptions which do not clear meta cache.
     * Periodically throw NotSevingRegionException which clears the meta cache.
     * @throws ServiceException
     */
    private void throwSomeExceptions() throws ServiceException {
      numReqs++;
      // Succeed every 5 request, throw cache clearing exceptions twice every 5 requests and throw
      // meta cache preserving exceptions otherwise.
      if (numReqs % 5 ==0) {
        return;
      } else if (numReqs % 5 == 1 || numReqs % 5 == 2) {
        throw new ServiceException(new NotServingRegionException());
      }
      // Round robin between different special exceptions.
      // This is not ideal since exception types are not tied to the operation performed here,
      // But, we don't really care here if we throw MultiActionTooLargeException while doing
      // single Gets.
      expCount++;
      Throwable t = metaCachePreservingExceptions.get(
        expCount % metaCachePreservingExceptions.size());
      throw new ServiceException(t);
    }
  }
}