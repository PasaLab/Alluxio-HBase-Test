# Alluxio-HBase-Test
This is a repo for HBase on Alluxio Integration Test

#### 1、在集群中安装以下软件：
+ Hadoop2.7.1
+ Alluxio1.2.0(build with Hadoop2.7.1)
+ HBase1.2.2(build with Hadoop2.7.1)(可以独立安装一个zookeeper ensemble供HBase使用)

(以上软件的配置项可以参考conf目录下的配置文件)

#### 2、然后将本工程拷到其中任何一个节点上(该节点可以作为HBase和Hadoop的Client即可)

##### 该测试分为3个部分:
###### 1.第一部分为单元测试,使用junit编写(参考HBase Client UnitTest):
  
  运行方式为：
  
     到项目根目录下运行：mvn test

###### 2.第二部分为Hadoop Utility测试, 测试命令在bin/hadoop_utility_test.sh中,
  
  **需要export ${HBASE_HOME}或者将${HBASE_HOME}替换为实际的HBase根目录**
  
  **需要准备tsv或csv文件作为输入并把脚本中所有文件路径替换为实际的路径**
  
###### 3.第三部分为HBase的Integration Test, 测试命令在bin/integration_test.sh中,
  
  **需要先在Base源码根目录下运行*mvn-compile*将HBase测试代码编译**
  
  **需要export ${HBASE_HOME}或者将${HBASE_HOME}替换为实际的HBase根目录**

  测试IntegrationTestIngestWithACL需要在hbase-site.xml中配置：
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
  测试IntegrationTestIngestWithVisibilityLabels需要在hbase-site.xml中配置：
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
  可以同时配置，以逗号分隔，具体见[conf/hbase_conf/hbase-site.xml](./conf/hbase_conf/hbase-site.xml)
  
  如果有Integration Test的测试出现异常(HBase未报错，Alluxio的日志中有hbase checksum failed),可以在hbase-site.xml中添加如下配置:
  ```xml
  <property>
   <name>hbase.regionserver.checksum.verify</name>
   <value>false</value>
  </property>
  ```
目前没有通过的HBase Integration Test，并且需要研究的如下:
```bash
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.trace.IntegrationTestSendTraceRequests(出现OutOfOrderScannerNextException,在HBase-on-HDFS可以，正在研究)
```
其余没通过的HBase Integration Test， 无需测试的原因写在后面了：
```
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.mapreduce.IntegrationTestBulkLoad(这个测试代码有bug, https://issues.apache.org/jira/browse/HBASE-16558)
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.IntegrationTestRegionReplicaReplication(出现和HBase-on-HDFS一样的错误，测试代码问题)
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.IntegrationTestMetaReplicas(和HBase-on-HDFS一样报zookeeper connection refused)
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.test.IntegrationTestBigLinkedListWithVisibility Loop 1 10 10 /tmp/aas 2 -u huangzhi(和HBase-on-HDFS一样的报错， Verify失败)
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.IntegrationTestsDriver -r .*\\.IntegrationTestMTTR(因为是测试的是集群从宕机到恢复的时间，一直在读写表，跑)
```