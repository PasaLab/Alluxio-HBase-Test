#!/usr/bin/env bash
# 执行该脚本中的所有命令, 需要先到HBase源码工程的根目录下执行mvn test-compile！！！将测试代码编译为Java class文件

# 该测试命令执行时间约为20min,设置compact type为Stripe Compact后不停进行表数据的读写
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.IntegrationTestIngestStripeCompactions

# 该测试命令可以设置测试时间
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.IntegrationTestDDLMasterFailover -Dhbase.IntegrationTestDDLMasterFailover.runtime=240000 -Dhbase.IntegrationTestDDLMasterFailover.numThreads=4 -Dhbase.IntegrationTestDDLMasterFailover.numRegions=10

# 该测试命令可以设置时间
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.IntegrationTestIngest -Dhbase.IntegrationTestIngest.runtime=600000

# loadmapper.num_to_write可以设置
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.test.IntegrationTestLoadAndVerify -Dloadmapper.num_to_write=10000 loadAndVerify

# 该测试约为20min
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.test.IntegrationTestTimeBoundedRequestsWithRegionReplicas

# 该测试约为20min
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.test.IntegrationTestTimeBoundedMultiGetRequestsWithRegionReplicas

# 该测试需要节点上有mapreduce,约为1min
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.mapreduce.IntegrationTestImportTsv

# 该测试需要大约1min
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.IntegrationTestAcidGuarantees

# 该测试可以设置时间
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.IntegrationTestIngestWithTags -Dhbase.IntegrationTestIngest.runtime=600000

# 该测试约为1min
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.IntegrationTestsDriver -r .*\\.IntegrationTestManyRegions

# 该测试需要节点上有mapreduce, 约为3min
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.IntegrationTestsDriver -r .*\\.IntegrationTestReplication

# 该测试约为6min
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.IntegrationTestsDriver -r .*\\.IntegrationTestRpcClient

# 该测试约为2min
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.IntegrationTestsDriver -r .*\\.IntegrationTestLazyCfLoading

# 该测试可以设置时间
${HBASE_HOME}/bin/hbase -Dhbase.IntegrationTestIngest.runtime=600000 org.apache.hadoop.hbase.IntegrationTestsDriver -r .*\\.IntegrationTestIngestWithACL

# 该测试可以设置时间
${HBASE_HOME}/bin/hbase -Dhbase.IntegrationTestIngest.runtime=600000 org.apache.hadoop.hbase.IntegrationTestsDriver -r .*\\.IntegrationTestIngestWithVisibilityLabels
