# Alluxio-HBase-Test
This is a repo for HBase on Alluxio Integration Test

#### 1. Install the following softwares in your cluster：
+ Hadoop2.7.1
+ Alluxio1.2.0(build with Hadoop2.7.1)
+ HBase1.2.2(build with Hadoop2.7.1)(you can install a zookeeper ensemble independently for HBase)

(Configuration items can refer to the `conf` directory in this project)

#### 2、 Copy this project to a node in the cluster(make sure this node can act as a client of HBase and Hadoop)

##### The tests are divided into three parts:
##### 1.The first part is unit test(mostly from HBase Client UnitTest):
  
  How to run it：
  
     go to the project base directory and run a maven command： `mvn test`

##### 2.The second part is Hadoop Utility test, the test commands is in `bin/hadoop_utility_test.sh`:
  
  requisites:
  
      1. export ${HBASE_HOME}or replace ${HBASE_HOME}with the actual HBase root directory:
  
      2. prepare a tsv file or csv file as input and replace the input path with the actual tsv file path
  
##### 3.The third part is HBase Integration Test, the test commands is in `bin/integration_test.sh`:

  requisites:
  
      1. go to the root directory of HBase source code and run *mvn-compile* to compile the HBase test code
  
      2. export ${HBASE_HOME}or replace ${HBASE_HOME}with the actual HBase root directory:

  running test IntegrationTestIngestWithACL needs the following configuration item in `hbase-site.xml`：
  ```xml
  <property>
   <name>hbase.coprocessor.region.classes</name>
   <value>org.apache.hadoop.hbase.security.access.AccessController</value>
  </property>
  <property>
   <name>hbase.coprocessor.master.classes</name>
   <value>org.apache.hadoop.hbase.security.access.AccessController</value>
  </property>
  <property>
   <name>hbase.security.access.early_out</name>
   <value>false</value>
  </property>
  ```
  running test IntegrationTestIngestWithVisibilityLabels needs the following configuration item in `hbase-site.xml`：
  ```xml
  <property>
   <name>hbase.coprocessor.region.classes</name>
   <value>org.apache.hadoop.hbase.security.visibility.VisibilityController</value>
  </property>
  <property>
   <name>hbase.coprocessor.master.classes</name>
   <value>org.apache.hadoop.hbase.security.visibility.VisibilityController</value>
  </property>
  ```
  The above 2 configuration items can be set in the same item, just separate the value with a comma， see [conf/hbase_conf/hbase-site.xml](./conf/hbase_conf/hbase-site.xml)
  
  if any integration test throws an expection and you can see 'hbase checksum failed, using HDFS checksum' in Alluxio logs(but nothing abnormal in HBase logs), you can add a configuration in `hbase-site.xml`:
  ```xml
  <property>
   <name>hbase.regionserver.checksum.verify</name>
   <value>false</value>
  </property>
  ```
There is a integration test failed and needs to be digged:
```bash
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.trace.IntegrationTestSendTraceRequests(OutOfOrderScannerNextException appears, this test can pass in HBase-on-HDFS, still working on it):
```
Other integration tests which fail but don't need futher work are listed below:
```
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.mapreduce.IntegrationTestBulkLoad(test code bug, see [https://issues.apache.org/jira/browse/HBASE-16558](https://issues.apache.org/jira/browse/HBASE-16558))
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.IntegrationTestRegionReplicaReplication(same error in HBase-on-HDFS)
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.IntegrationTestMetaReplicas(same error in HBase-on-HDFS: zookeeper connection refused)
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.test.IntegrationTestBigLinkedListWithVisibility Loop 1 10 10 /tmp/aas 2 -u huangzhi(same error in HBase-on-HDFS: Verify failed)
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.IntegrationTestsDriver -r .*\\.IntegrationTestRpcClient(same warning in HBase-on-HDFS)
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.IntegrationTestsDriver -r .*\\.IntegrationTestMTTR(destroy test, need about 2 days in a 4-node HBase cluster(48G mem, 3TB HDD))
```