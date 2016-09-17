#!/usr/bin/env bash
# before running any of the commands in this file, go to HBase source root directory and run 'mvn test-compile' to compile the test source code of HBase

# test ingest data when runing in the stripe compact type, costs 20min:
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.IntegrationTestIngestStripeCompactions

# can set test running time
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.IntegrationTestDDLMasterFailover -Dhbase.IntegrationTestDDLMasterFailover.runtime=240000 -Dhbase.IntegrationTestDDLMasterFailover.numThreads=4 -Dhbase.IntegrationTestDDLMasterFailover.numRegions=10

# can set test running time
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.IntegrationTestIngest -Dhbase.IntegrationTestIngest.runtime=600000

# can set loadmapper.num_to_write
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.test.IntegrationTestLoadAndVerify -Dloadmapper.num_to_write=10000 loadAndVerify

# costs about 20min
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.test.IntegrationTestTimeBoundedRequestsWithRegionReplicas

# costs about 20min
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.test.IntegrationTestTimeBoundedMultiGetRequestsWithRegionReplicas

# costs about 1min, need mapreduce:
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.mapreduce.IntegrationTestImportTsv

# costs about 1min
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.IntegrationTestAcidGuarantees

# can set test running time
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.IntegrationTestIngestWithTags -Dhbase.IntegrationTestIngest.runtime=600000

# can set test running time
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.IntegrationTestsDriver -r .*\\.IntegrationTestManyRegions

# costs about 3min, need mapreduce:
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.IntegrationTestsDriver -r .*\\.IntegrationTestReplication

# costs about 2min
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.IntegrationTestsDriver -r .*\\.IntegrationTestLazyCfLoading

# can set test running time
${HBASE_HOME}/bin/hbase -Dhbase.IntegrationTestIngest.runtime=600000 org.apache.hadoop.hbase.IntegrationTestsDriver -r .*\\.IntegrationTestIngestWithACL

# can set test running time
${HBASE_HOME}/bin/hbase -Dhbase.IntegrationTestIngest.runtime=600000 org.apache.hadoop.hbase.IntegrationTestsDriver -r .*\\.IntegrationTestIngestWithVisibilityLabels

# the arguments are <num mappers> <num nodes per map> <tmp output dir>, need mapreduce:
hbase-1.2.2/bin/hbase org.apache.hadoop.hbase.test.IntegrationTestBigLinkedList Generator 10 10 /tmp/ITBLL2

# the arguments are <output dir> <num reducers>, need mapreduce:
hbase-1.2.2/bin/hbase org.apache.hadoop.hbase.test.IntegrationTestBigLinkedList verify /tmp/ITBLL3 2

