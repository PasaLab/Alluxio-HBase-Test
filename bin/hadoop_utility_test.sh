#!/usr/bin/env bash
#该命令中-Dimporttsv.separator表示每行单词分隔符(tsv为\t, csv为,), test-table表示表名, hdfs://sr164:9000/tsv-files为输入文件的路径(可以是文件夹)
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.mapreduce.ImportTsv -Dimporttsv.columns=HBASE_ROW_KEY,a:a1,a:a2,b:b1 -Dimporttsv.separator=, test-table hdfs://sr164:9000/tsv-files

#该命令中test-table为Export的表名, hdfs://sr164:9000/testexport表示Export的文件名
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.mapreduce.Export test-table hdfs://sr164:9000/testexport

#该命令中test-table为Import的表名, hdfs://sr164:9000/testexport表示Import的文件名
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.mapreduce.Import test-table2 hdfs://sr164:9000/testexport

#该命令中alluxio://sr164:19998/hbase/WALs可以不用修改, 表示播放该目录下所有WAL, test-table为需要播放的表名
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.mapreduce.WALPlayer alluxio://sr164:19998/hbase/WALs test-table

#该命令中test-table表示需要进行统计行数的表名
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.mapreduce.RowCounter test-table

#该命令中test-table表示需要进行cell统计的表名
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.mapreduce.CellCounter test-table
