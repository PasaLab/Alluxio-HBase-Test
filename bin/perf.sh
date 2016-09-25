#!/bin/bash

${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=10000 --table=TestCheckAppend --nomapred --columns=2  append 3 >Append10000.txt 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=20000 --table=TestCheckAppend --nomapred --columns=2  append 3 >Append20000.txt 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=30000 --table=TestCheckAppend --nomapred --columns=2  append 3 >Append30000.txt 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=40000 --table=TestCheckAppend --nomapred --columns=2  append 3 >Append40000.txt 2>&1

${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=10000 --table=TestCheckAndDelete --nomapred --columns=2  checkAndDelete 3 >Delete10000.txt 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=20000 --table=TestCheckAndDelete --nomapred --columns=2  checkAndDelete 3 >Delete20000.txt 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=30000 --table=TestCheckAndDelete --nomapred --columns=2  checkAndDelete 3 >Delete30000.txt 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=40000 --table=TestCheckAndDelete --nomapred --columns=2  checkAndDelete 3 >Delete40000.txt 2>&1

${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=10000 --table=TestCheckAndMutate --nomapred --columns=2  checkAndMutate 3 >Mutate10000.txt 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=20000 --table=TestCheckAndMutate --nomapred --columns=2  checkAndMutate 3 >Mutate20000.txt 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=30000 --table=TestCheckAndMutate --nomapred --columns=2  checkAndMutate 3 >Mutate30000.txt 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=40000 --table=TestCheckAndMutate --nomapred --columns=2  checkAndMutate 3 >Mutate40000.txt 2>&1

${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=10000 --table=TestCheckAndPut --nomapred --columns=2  checkAndPut 3 >Put10000.txt 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=20000 --table=TestCheckAndPut --nomapred --columns=2  checkAndPut 3 >Put20000.txt 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=30000 --table=TestCheckAndPut --nomapred --columns=2  checkAndPut 3 >Put30000.txt 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=40000 --table=TestCheckAndPut --nomapred --columns=2  checkAndPut 3 >Put40000.txt 2>&1

${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=10000 --table=TestIncrement --nomapred --columns=2  increment 3 >Increment10000.txt 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=20000 --table=TestIncrement --nomapred --columns=2  increment 3 >Increment20000.txt 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=30000 --table=TestIncrement --nomapred --columns=2  increment 3 >Increment30000.txt 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=40000 --table=TestIncrement --nomapred --columns=2  increment 3 >Increment40000.txt 2>&1

${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=100000 --table=TestRandomWrite --nomapred --columns=2  randomWrite 3 >RandomWrite100000.txt 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=200000 --table=TestRandomWrite --nomapred --columns=2  randomWrite 3 >RandomWrite200000.txt 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=300000 --table=TestRandomWrite --nomapred --columns=2  randomWrite 3 >RandomWrite300000.txt 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=400000 --table=TestRandomWrite --nomapred --columns=2  randomWrite 3 >RandomWrite400000.txt 2>&1

${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=100000 --table=TestSequentialWrite --nomapred --columns=2  sequentialWrite 3 >SequentialWrite100000.txt 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=200000 --table=TestSequentialWrite --nomapred --columns=2  sequentialWrite 3 >SequentialWrite200000.txt 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=300000 --table=TestSequentialWrite --nomapred --columns=2  sequentialWrite 3 >SequentialWrite300000.txt 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=400000 --table=TestSequentialWrite --nomapred --columns=2  sequentialWrite 3 >SequentialWrite400000.txt 2>&1

${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=100000 --table=TestSequentialWrite --columns=2 --nomapred randomRead 3 >randomRead100000.out 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=200000 --table=TestSequentialWrite --columns=2 --nomapred randomRead 3 >randomRead200000.out 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=300000 --table=TestSequentialWrite --columns=2 --nomapred randomRead 3 >randomRead300000.out 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=400000 --table=TestSequentialWrite --columns=2 --nomapred randomRead 3 >randomRead400000.out 2>&1

${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=10000 --table=TestSequentialWrite --columns=2 --nomapred randomSeekScan 3 >randomSeekScan10000.out 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=20000 --table=TestSequentialWrite --columns=2 --nomapred randomSeekScan 3 >randomSeekScan20000.out 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=30000 --table=TestSequentialWrite --columns=2 --nomapred randomSeekScan 3 >randomSeekScan30000.out 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=40000 --table=TestSequentialWrite --columns=2 --nomapred randomSeekScan 3 >randomSeekScan40000.out 2>&1

${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=100000 --table=TestSequentialWrite --columns=2 --nomapred scan 3 >scan100000.out 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=200000 --table=TestSequentialWrite --columns=2 --nomapred scan 3 >scan200000.out 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=300000 --table=TestSequentialWrite --columns=2 --nomapred scan 3 >scan300000.out 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=400000 --table=TestSequentialWrite --columns=2 --nomapred scan 3 >scan400000.out 2>&1

${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=10000  --table=TestSequentialWrite --columns=2 --nomapred scanRange10 3 >scanRange10-10000.out 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=20000  --table=TestSequentialWrite --columns=2 --nomapred scanRange10 3 >scanRange10-20000.out 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=30000  --table=TestSequentialWrite --columns=2 --nomapred scanRange10 3 >scanRange10-30000.out 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=40000  --table=TestSequentialWrite --columns=2 --nomapred scanRange10 3 >scanRange10-40000.out 2>&1

${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=10000  --table=TestSequentialWrite --columns=2 --nomapred scanRange100 3 >scanRange100-10000.out 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=20000  --table=TestSequentialWrite --columns=2 --nomapred scanRange100 3 >scanRange100-20000.out 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=30000  --table=TestSequentialWrite --columns=2 --nomapred scanRange100 3 >scanRange100-30000.out 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=40000  --table=TestSequentialWrite --columns=2 --nomapred scanRange100 3 >scanRange100-40000.out 2>&1

${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=10000  --table=TestSequentialWrite --columns=2 --nomapred scanRange1000 3 >scanRange1000-10000.out 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=20000  --table=TestSequentialWrite --columns=2 --nomapred scanRange1000 3 >scanRange1000-20000.out 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=30000  --table=TestSequentialWrite --columns=2 --nomapred scanRange1000 3 >scanRange1000-30000.out 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=40000  --table=TestSequentialWrite --columns=2 --nomapred scanRange1000 3 >scanRange1000-40000.out 2>&1

${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=1000  --table=TestSequentialWrite --columns=2 --nomapred scanRange10000 3 >scanRange10000-1000.out 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=2000  --table=TestSequentialWrite --columns=2 --nomapred scanRange10000 3 >scanRange10000-2000.out 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=3000  --table=TestSequentialWrite --columns=2 --nomapred scanRange10000 3 >scanRange10000-3000.out 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=4000  --table=TestSequentialWrite --columns=2 --nomapred scanRange10000 3 >scanRange10000-4000.out 2>&1

${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=100000 --table=TestSequentialWrite --columns=2 --nomapred sequentialRead 3 >sequentialRead100000.out 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=200000 --table=TestSequentialWrite --columns=2 --nomapred sequentialRead 3 >sequentialRead200000.out 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=300000 --table=TestSequentialWrite --columns=2 --nomapred sequentialRead 3 >sequentialRead300000.out 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=400000 --table=TestSequentialWrite --columns=2 --nomapred sequentialRead 3 >sequentialRead400000.out 2>&1

${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=10000 --table=TestSequentialWrite --columns=2 --nomapred filterScan 3 >filterScan10000.out 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=20000 --table=TestSequentialWrite --columns=2 --nomapred filterScan 3 >filterScan20000.out 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=30000 --table=TestSequentialWrite --columns=2 --nomapred filterScan 3 >filterScan30000.out 2>&1
${HBASE_HOME}/bin/hbase org.apache.hadoop.hbase.PerformanceEvaluation --rows=40000 --table=TestSequentialWrite --columns=2 --nomapred filterScan 3 >filterScan40000.out 2>&1
