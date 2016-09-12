#!/usr/bin/env bash
# -Dimporttsv.columns=HBASE_ROW_KEY,a:a1,a:a2,b:b1 is for a csv file which line is like './wikipedia/commons/e/e4/A.JPG,a,59527,1',
# './wikipedia/commons/e/e4/A.JPG' is HBASE_ROW_KEY,
# 'a' for a:a1,
# '59527' for a:a2,
# '1' for b:b1
# -Dimporttsv.separator means the separator in a file line,
# 'test-table' means table name,
# 'hdfs://sr164:9000/tsv-files' means the input path,
# !! before running this command, you should use hbase shell create the table:
# !! create 'test', 'a', 'b'
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.mapreduce.ImportTsv -Dimporttsv.columns=HBASE_ROW_KEY,a:a1,a:a2,b:b1 -Dimporttsv.separator=, test-table hdfs://sr164:9000/tsv-files

# 'test-table' means table name,
# 'hdfs://sr164:9000/tsv-files' means the Export path,
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.mapreduce.Export test-table hdfs://sr164:9000/testexport

# 'test-table' means table name,
# 'hdfs://sr164:9000/tsv-files' means the Import path,
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.mapreduce.Import test-table2 hdfs://sr164:9000/testexport

# 'alluxio://sr164:19998/hbase/WALs' is ok for all the WAL play,
# 'test-table' means table name,
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.mapreduce.WALPlayer alluxio://sr164:19998/hbase/WALs test-table

# 'test-table' means table name,
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.mapreduce.RowCounter test-table

# 'test-table' means table name,
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.mapreduce.CellCounter test-table
