# Alluxio-HBase-Test
This is a repo for HBase on Alluxio Integration Test

首先在集群中安装以下软件：
Hadoop2.7.1
Alluxio1.2.0(build with Hadoop2.7.1)
HBase1.2.2(build with Hadoop2.7.1)
可以独立安装一个zookeeper ensemble供HBase使用.

然后将本工程考到其中任何一个节点上,可以作为HBase和Hadoop的Client即可。
该测试分为3个部分:
  第一部分为单元测试,使用junit编写(参考HBase Client UnitTest):
运行方式为：
  到项目根目录下运行：mvn test

  第二部分为Hadoop Utility测试,将以csv文件上传到hdfs或alluxio上,
  然后把bin目录下的hbase-hadoop-test.sh的第一行最后一个参数设置为文件的地址
  到bin目录下运行hbase-hadoop-test.sh即可
  
  第三部分为HBase的Integration Test:
  到bin目录下运行hbase-it.sh即可
  
  关于Ingest的测试出现异常:(20min,可能需要将checksum.verify设置为false)
  
  测试IntegrationTestIngestWithACL需要在hbase-site.xml中配置：
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
目前没有通过的HBase Integration如下:
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.mapreduce.IntegrationTestBulkLoad(这个测试代码有bug,https://issues.apache.org/jira/browse/HBASE-16558)



