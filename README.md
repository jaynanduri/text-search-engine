# Project Report

### Author
- Jayantha Nanduri
---

# PR psuedocode in Spark
- Page Rank PseudoCode
```scala
package pr

import org.apache.spark.{HashPartitioner, SparkConf, SparkContext}
import org.apache.log4j.LogManager


object PageRankMain {
  def main(args: Array[String]) {
    val logger: org.apache.log4j.Logger = LogManager.getRootLogger
    if (args.length != 3) {
      logger.error("Usage:\n pr.PageRank <output dir> <k> <iterations>")
      System.exit(1)
    }

    val conf = new SparkConf().setAppName("Page Rank").setMaster("local")
    val sc = new SparkContext(conf)
    // program parameters to run page rank algorithm
    val k = args(1).toInt // used to compute number of pages 
    val iter = args(2).toInt // number of iterations
    val numPages = k * k
    // initialising every page with page rank value to be 1.0/(k * k)
    val initialPRValue = 1.0 / numPages

    // Generating graph RDD([Int, Int])
    // A sequence of linear chains with dummy page having pseudo edges to all
    // pages. 
    // ex: with k=2, graph = {0 :[1, 2, 3, 4], 1: [2], 2:[0], 3:[4], 4:[0]} obtained after groupByKey
    val graph = sc.parallelize((0 until numPages+1).flatMap { i =>
      if (i == 0) (1 until numPages+1).map(j => (0, j)) // From dummy node to all other nodes
      else Seq((i, if (i % k != 0) i+1 else 0)) // Ensuring a linear chain, looping back after reaching the node divisible by k
    }).groupByKey().partitionBy(new HashPartitioner(k)).persist() //caching the graph to reuse it, since graph doesn't change
    
    // rank is obtained from using mapValues on graph so that both, RDDs use same partitioner.
    // This is effective for joining the two RDDs
    var ranks = graph.mapValues(f => if (f.size == numPages) 0.0 else initialPRValue)
    
    // Starting page rank iterations
    for (i <- 1 until iter) {
      // contribution of each page is computed as page_rank_of_page/num_of_out_edges 
      // and each page in the outgoing list receives that much mass from the current page
      val contrib = graph.join(ranks).flatMap {
        case (pageId, (pageLinks, rank)) => pageLinks.map(dest => (dest, rank / pageLinks.size))
      }
      // reducing by key to add the page rank contributions from all the edges coming into the page
      // also adding the prob of random walk
      ranks = contrib.reduceByKey((x, y) => x + y).mapValues(prob => (0.15/(numPages+1)) + (0.85 * prob))
    }

    logger.info("Lineage of ranks rdd - " + ranks.toDebugString)

    logger.info("Sum of all page ranks - " + ranks.map(f=>f._2).reduce(_+_))

    ranks.saveAsTextFile(args(0))
    sc.stop()
  }
}
```

# Top 19 pages after 10 iterations
```text
# K = 100, iterations = 10
# top 19 pages page rank values

(0,0.008797467286167473)
(1,1.574421654098754E-5)
(2,2.912475255218331E-5)
(3,4.0496180193894446E-5)
(4,5.01598856055111E-5)
(5,5.8372046808640476E-5)
(6,6.535041512199611E-5)
(7,7.1280059444305E-5)
(8,7.632026059217433E-5)
(9,8.04072097728422E-5)
(10,1.0356890440116253E-4)
(11,1.0356890440116253E-4)
(12,1.0356890440116253E-4)
(13,1.0356890440116253E-4)
(14,1.0356890440116253E-4)
(15,1.0356890440116253E-4)
(16,1.0356890440116253E-4)
(17,1.0356890440116253E-4)
(18,1.0356890440116253E-4)
(19,1.0356890440116253E-4)
```

# Lineage of spark RDD 

- ## Iteration - 1, 2 and 3
```text
- Lineage of ranks rdd for single iteration
   
  (100) MapPartitionsRDD[9] at mapValues at PageRankMain.scala:46 []
  |   ShuffledRDD[8] at reduceByKey at PageRankMain.scala:46 []
  +-(100) MapPartitionsRDD[7] at flatMap at PageRankMain.scala:41 []
      |   MapPartitionsRDD[6] at join at PageRankMain.scala:41 []
      |   MapPartitionsRDD[5] at join at PageRankMain.scala:41 []
      |   CoGroupedRDD[4] at join at PageRankMain.scala:41 []
      |   ShuffledRDD[2] at partitionBy at PageRankMain.scala:31 []
      +-(1) ShuffledRDD[1] at groupByKey at PageRankMain.scala:28 []
         +-(1) ParallelCollectionRDD[0] at parallelize at PageRankMain.scala:28 []
      |   MapPartitionsRDD[3] at mapValues at PageRankMain.scala:35 []
      |   ShuffledRDD[2] at partitionBy at PageRankMain.scala:31 []
      +-(1) ShuffledRDD[1] at groupByKey at PageRankMain.scala:28 []
         +-(1) ParallelCollectionRDD[0] at parallelize at PageRankMain.scala:28 []

- Lineage of ranks rdd for 2 iterations

(100) MapPartitionsRDD[15] at mapValues at PageRankMain.scala:46 []
|   ShuffledRDD[14] at reduceByKey at PageRankMain.scala:46 []
+-(100) MapPartitionsRDD[13] at flatMap at PageRankMain.scala:41 []
  |   MapPartitionsRDD[12] at join at PageRankMain.scala:41 []
  |   MapPartitionsRDD[11] at join at PageRankMain.scala:41 []
  |   CoGroupedRDD[10] at join at PageRankMain.scala:41 []
  |   ShuffledRDD[2] at partitionBy at PageRankMain.scala:31 []
  +-(1) ShuffledRDD[1] at groupByKey at PageRankMain.scala:28 []
     +-(1) ParallelCollectionRDD[0] at parallelize at PageRankMain.scala:28 []
  |   MapPartitionsRDD[9] at mapValues at PageRankMain.scala:46 []
  |   ShuffledRDD[8] at reduceByKey at PageRankMain.scala:46 []
  +-(100) MapPartitionsRDD[7] at flatMap at PageRankMain.scala:41 []
      |   MapPartitionsRDD[6] at join at PageRankMain.scala:41 []
      |   MapPartitionsRDD[5] at join at PageRankMain.scala:41 []
      |   CoGroupedRDD[4] at join at PageRankMain.scala:41 []
      |   ShuffledRDD[2] at partitionBy at PageRankMain.scala:31 []
      +-(1) ShuffledRDD[1] at groupByKey at PageRankMain.scala:28 []
         +-(1) ParallelCollectionRDD[0] at parallelize at PageRankMain.scala:28 []
      |   MapPartitionsRDD[3] at mapValues at PageRankMain.scala:35 []
      |   ShuffledRDD[2] at partitionBy at PageRankMain.scala:31 []
      +-(1) ShuffledRDD[1] at groupByKey at PageRankMain.scala:28 []
         +-(1) ParallelCollectionRDD[0] at parallelize at PageRankMain.scala:28 []

- Lineage of ranks rdd for 3 iterations

(100) MapPartitionsRDD[21] at mapValues at PageRankMain.scala:46 []
|   ShuffledRDD[20] at reduceByKey at PageRankMain.scala:46 []
+-(100) MapPartitionsRDD[19] at flatMap at PageRankMain.scala:41 []
  |   MapPartitionsRDD[18] at join at PageRankMain.scala:41 []
  |   MapPartitionsRDD[17] at join at PageRankMain.scala:41 []
  |   CoGroupedRDD[16] at join at PageRankMain.scala:41 []
  |   ShuffledRDD[2] at partitionBy at PageRankMain.scala:31 []
  +-(1) ShuffledRDD[1] at groupByKey at PageRankMain.scala:28 []
     +-(1) ParallelCollectionRDD[0] at parallelize at PageRankMain.scala:28 []
  |   MapPartitionsRDD[15] at mapValues at PageRankMain.scala:46 []
  |   ShuffledRDD[14] at reduceByKey at PageRankMain.scala:46 []
  +-(100) MapPartitionsRDD[13] at flatMap at PageRankMain.scala:41 []
      |   MapPartitionsRDD[12] at join at PageRankMain.scala:41 []
      |   MapPartitionsRDD[11] at join at PageRankMain.scala:41 []
      |   CoGroupedRDD[10] at join at PageRankMain.scala:41 []
      |   ShuffledRDD[2] at partitionBy at PageRankMain.scala:31 []
      +-(1) ShuffledRDD[1] at groupByKey at PageRankMain.scala:28 []
         +-(1) ParallelCollectionRDD[0] at parallelize at PageRankMain.scala:28 []
      |   MapPartitionsRDD[9] at mapValues at PageRankMain.scala:46 []
      |   ShuffledRDD[8] at reduceByKey at PageRankMain.scala:46 []
      +-(100) MapPartitionsRDD[7] at flatMap at PageRankMain.scala:41 []
          |   MapPartitionsRDD[6] at join at PageRankMain.scala:41 []
          |   MapPartitionsRDD[5] at join at PageRankMain.scala:41 []
          |   CoGroupedRDD[4] at join at PageRankMain.scala:41 []
          |   ShuffledRDD[2] at partitionBy at PageRankMain.scala:31 []
          +-(1) ShuffledRDD[1] at groupByKey at PageRankMain.scala:28 []
             +-(1) ParallelCollectionRDD[0] at parallelize at PageRankMain.scala:28 []
          |   MapPartitionsRDD[3] at mapValues at PageRankMain.scala:35 []
          |   ShuffledRDD[2] at partitionBy at PageRankMain.scala:31 []
          +-(1) ShuffledRDD[1] at groupByKey at PageRankMain.scala:28 []
             +-(1) ParallelCollectionRDD[0] at parallelize at PageRankMain.scala:28 []
```
  ### Explanation
  Begining with a ParallelCollectionRDD, which is created by parallelizing a collection. Apply the "groupByKey" transformation to this parallelized collection. This transformation shuffles the RDD on keys, and we create a rank using "mapValues" to modify values while retaining keys, similar to a graph. The process then progresses through joins, indicated by a CoGroupedRDD and multiple MapPartitionsRDD stages. These stages represent operations such as flatMap and reduceByKey, which map inputs to multiple outputs and aggregate values by key, respectively. The lineage culminates in a transformed MapPartitionsRDD.

# Behaviour of RDD when a print statement is added

- a 
  - Adding a println(s"Iteration ${i}") would print the lineage of rank RDD to observe the execution flow of the PageRank algorithm . However, the lineage of the RDDs don't change because lineage pertains to the transformations and actions performed on RDDs.

      ```text
      Iteration- 1, Lineage- (100) MapPartitionsRDD[9] at mapValues at PageRankMain.scala:46 []
        |   ShuffledRDD[8] at reduceByKey at PageRankMain.scala:46 []
        +-(100) MapPartitionsRDD[7] at flatMap at PageRankMain.scala:41 []
            |   MapPartitionsRDD[6] at join at PageRankMain.scala:41 []
            |   MapPartitionsRDD[5] at join at PageRankMain.scala:41 []
            |   CoGroupedRDD[4] at join at PageRankMain.scala:41 []
            |   ShuffledRDD[2] at partitionBy at PageRankMain.scala:31 []
            +-(1) ShuffledRDD[1] at groupByKey at PageRankMain.scala:28 []
               +-(1) ParallelCollectionRDD[0] at parallelize at PageRankMain.scala:28 []
            |   MapPartitionsRDD[3] at mapValues at PageRankMain.scala:35 []
            |   ShuffledRDD[2] at partitionBy at PageRankMain.scala:31 []
            +-(1) ShuffledRDD[1] at groupByKey at PageRankMain.scala:28 []
               +-(1) ParallelCollectionRDD[0] at parallelize at PageRankMain.scala:28 []
    
      Iteration- 2, Lineage- (100) MapPartitionsRDD[15] at mapValues at PageRankMain.scala:46 []
        |   ShuffledRDD[14] at reduceByKey at PageRankMain.scala:46 []
        +-(100) MapPartitionsRDD[13] at flatMap at PageRankMain.scala:41 []
            |   MapPartitionsRDD[12] at join at PageRankMain.scala:41 []
            |   MapPartitionsRDD[11] at join at PageRankMain.scala:41 []
            |   CoGroupedRDD[10] at join at PageRankMain.scala:41 []
            |   ShuffledRDD[2] at partitionBy at PageRankMain.scala:31 []
            +-(1) ShuffledRDD[1] at groupByKey at PageRankMain.scala:28 []
               +-(1) ParallelCollectionRDD[0] at parallelize at PageRankMain.scala:28 []
            |   MapPartitionsRDD[9] at mapValues at PageRankMain.scala:46 []
            |   ShuffledRDD[8] at reduceByKey at PageRankMain.scala:46 []
            +-(100) MapPartitionsRDD[7] at flatMap at PageRankMain.scala:41 []
                |   MapPartitionsRDD[6] at join at PageRankMain.scala:41 []
                |   MapPartitionsRDD[5] at join at PageRankMain.scala:41 []
                |   CoGroupedRDD[4] at join at PageRankMain.scala:41 []
                |   ShuffledRDD[2] at partitionBy at PageRankMain.scala:31 []
                +-(1) ShuffledRDD[1] at groupByKey at PageRankMain.scala:28 []
                   +-(1) ParallelCollectionRDD[0] at parallelize at PageRankMain.scala:28 []
                |   MapPartitionsRDD[3] at mapValues at PageRankMain.scala:35 []
                |   ShuffledRDD[2] at partitionBy at PageRankMain.scala:31 []
                +-(1) ShuffledRDD[1] at groupByKey at PageRankMain.scala:28 []
                   +-(1) ParallelCollectionRDD[0] at parallelize at PageRankMain.scala:28 []
    
      Iteration- 3, Lineage- (100) MapPartitionsRDD[21] at mapValues at PageRankMain.scala:46 []
        |   ShuffledRDD[20] at reduceByKey at PageRankMain.scala:46 []
        +-(100) MapPartitionsRDD[19] at flatMap at PageRankMain.scala:41 []
            |   MapPartitionsRDD[18] at join at PageRankMain.scala:41 []
            |   MapPartitionsRDD[17] at join at PageRankMain.scala:41 []
            |   CoGroupedRDD[16] at join at PageRankMain.scala:41 []
            |   ShuffledRDD[2] at partitionBy at PageRankMain.scala:31 []
            +-(1) ShuffledRDD[1] at groupByKey at PageRankMain.scala:28 []
               +-(1) ParallelCollectionRDD[0] at parallelize at PageRankMain.scala:28 []
            |   MapPartitionsRDD[15] at mapValues at PageRankMain.scala:46 []
            |   ShuffledRDD[14] at reduceByKey at PageRankMain.scala:46 []
            +-(100) MapPartitionsRDD[13] at flatMap at PageRankMain.scala:41 []
                |   MapPartitionsRDD[12] at join at PageRankMain.scala:41 []
                |   MapPartitionsRDD[11] at join at PageRankMain.scala:41 []
                |   CoGroupedRDD[10] at join at PageRankMain.scala:41 []
                |   ShuffledRDD[2] at partitionBy at PageRankMain.scala:31 []
                +-(1) ShuffledRDD[1] at groupByKey at PageRankMain.scala:28 []
                   +-(1) ParallelCollectionRDD[0] at parallelize at PageRankMain.scala:28 []
                |   MapPartitionsRDD[9] at mapValues at PageRankMain.scala:46 []
                |   ShuffledRDD[8] at reduceByKey at PageRankMain.scala:46 []
                +-(100) MapPartitionsRDD[7] at flatMap at PageRankMain.scala:41 []
                    |   MapPartitionsRDD[6] at join at PageRankMain.scala:41 []
                    |   MapPartitionsRDD[5] at join at PageRankMain.scala:41 []
                    |   CoGroupedRDD[4] at join at PageRankMain.scala:41 []
                    |   ShuffledRDD[2] at partitionBy at PageRankMain.scala:31 []
                    +-(1) ShuffledRDD[1] at groupByKey at PageRankMain.scala:28 []
                       +-(1) ParallelCollectionRDD[0] at parallelize at PageRankMain.scala:28 []
                    |   MapPartitionsRDD[3] at mapValues at PageRankMain.scala:35 []
                    |   ShuffledRDD[2] at partitionBy at PageRankMain.scala:31 []
                    +-(1) ShuffledRDD[1] at groupByKey at PageRankMain.scala:28 []
                       +-(1) ParallelCollectionRDD[0] at parallelize at PageRankMain.scala:28 []
      ```
 
- b
  - 24/03/10 19:37:53 INFO DAGScheduler: Job 0 finished: lookup at PageRankMain.scala:49, took 4.387650 s
  - 24/03/10 19:37:55 INFO DAGScheduler: Job 1 finished: lookup at PageRankMain.scala:49, took 1.737219 s
  - 24/03/10 19:37:56 INFO DAGScheduler: Job 2 finished: lookup at PageRankMain.scala:49, took 1.559132 s
  - 24/03/10 19:37:58 INFO DAGScheduler: Job 3 finished: lookup at PageRankMain.scala:49, took 1.484536 s
  - 24/03/10 19:37:59 INFO DAGScheduler: Job 4 finished: lookup at PageRankMain.scala:49, took 1.460713 s
  - 24/03/10 19:38:01 INFO DAGScheduler: Job 5 finished: lookup at PageRankMain.scala:49, took 1.408902 s
  - 24/03/10 19:38:02 INFO DAGScheduler: Job 6 finished: lookup at PageRankMain.scala:49, took 1.385134 s
  - 24/03/10 19:38:03 INFO DAGScheduler: Job 7 finished: lookup at PageRankMain.scala:49, took 1.350588 s
  - 24/03/10 19:38:05 INFO DAGScheduler: Job 8 finished: lookup at PageRankMain.scala:49, took 1.356592 s
  - 24/03/10 19:38:06 INFO DAGScheduler: Job 9 finished: lookup at PageRankMain.scala:49, took 1.316157 s

  
From the above, we can observe that the execution times for the ten jobs remain same, therefore we can conclude that Spark re-used Ranks from the
    previous iteration.
  

- c

After adding .cache() to ranks definition, following are the log entries indicating that Spark is caching the RDD:

  - 24/03/10 19:52:12 INFO MemoryStore: Block broadcast_8 stored as values in memory (estimated size 7.0 KiB, free 2.2 GiB)
  - 24/03/10 19:52:12 INFO MemoryStore: Block rdd_25_0 stored as values in memory (estimated size 6.7 KiB, free 2.2 GiB)
  - 24/03/10 19:52:12 INFO MemoryStore: Block rdd_26_1 stored as values in memory (estimated size 6.7 KiB, free 2.2 GiB)
  - 24/03/10 19:52:12 INFO MemoryStore: Block rdd_27_2 stored as values in memory (estimated size 6.7 KiB, free 2.2 GiB)
  - 24/03/10 19:52:12 INFO MemoryStore: Block rdd_28_3 stored as values in memory (estimated size 6.7 KiB, free 2.2 GiB)
  - 24/03/10 19:52:12 INFO MemoryStore: Block rdd_29_4 stored as values in memory (estimated size 6.7 KiB, free 2.2 GiB)
  - 24/03/10 19:52:14 INFO MemoryStore: Block broadcast_14 stored as values in memory (estimated size 7.0 KiB, free 2.2 GiB)
  - 24/03/10 19:52:14 INFO MemoryStore: Block rdd_33_0 stored as values in memory (estimated size 6.7 KiB, free 2.2 GiB)
  - 24/03/10 19:53:41 INFO BlockManager: Found block rdd_64_97 locally 
  - 24/03/10 19:53:41 INFO BlockManager: Found block rdd_64_99 locally


"Block ... stored as values in memory": These lines confirm that Spark has stored the partitions of the ranks RDD in memory. It means that these partitions are ready for fast retrieval in subsequent iterations, eliminating the need for recomputation.

"Found block ... locally": Indicate that Spark has successfully located and accessed the cached partitions on the local node when needed for computations in later iterations. This directly benefits from the caching strategy, as it demonstrates the reuse of previously computed results.

# AWS cloud run results

Page rank algorithm using spark

1. [Output](https://s3.console.aws.amazon.com/s3/buckets/page-rank-spark?region=us-east-1&bucketType=general&prefix=output/&showversions=false)
2. [Log file](https://s3.console.aws.amazon.com/s3/object/page-rank-spark?region=us-east-1&bucketType=general&prefix=log/j-2OGM9GNIJETDA/steps/s-00262592TX6UCR41XLHC/stderr.gz)
3. Time elapsed for page rank - 18 min 2 secs

# Page rank implementation in MR

- Page rank pseudocode using MapReduce
```textmate
Configuration:
- numVertices: Total number of vertices/pages in the graph.
- dampingFactor: Damping factor used in the PageRank formula.
- iterations: Number of iterations to run the PageRank algorithm.
- linesPerMap: Number of lines each map task processes.
- inputFilePath: Path to the input file containing the graph data.
- outputFilePath: Base path for output files.

Input Assumptions:
- Every number divisible by k are dangling pages and point to 0 (dummy page).
- Other numbers in range (1 to k^2) point to num + 1, creating a linear chain
- Each page is initialise to have page rank = 1/k^2, dummy page pr value = 0.

Mapper (PageRankMap):
Input: Key-value pairs from the input file, where the key is ignored, and the value is a line representing a graph edge or a page with its rank.
Process:
- Parse the input line into a node ID, its PageRank, and an adjacency list.
- If the node is the dummy node (0), distribute its PageRank evenly across all pages.
- For a regular node, calculate the PageRank contribution for each adjacent node and output it.
- Output the adjacency list for each node to preserve the graph structure.

Reducer (PageRankReducer):
Input: Key-value pairs from the mapper, where the key is a node ID, and the values include its adjacency list and PageRank contributions from other nodes.
Process:
- Separate the adjacency list and PageRank contributions.
- Sum up all PageRank contributions.
- Apply the PageRank formula using the damping factor and total number of vertices to calculate the new PageRank.
- Output the node ID, its new PageRank, and its adjacency list.

Main:
- For each iteration, configure and run a new MapReduce job.
- For the first iteration, use the original input file. For subsequent iterations, use the output of the previous iteration as input.
- Output the result of each iteration to a separate directory.

Note: Dangling pages (pages without out-links) point to a dummy page (page 0), which accumulates the PageRank mass from these dangling pages. 
In each iteration, the dummy page redistributes this mass evenly to all pages in the graph, ensuring no PageRank mass is lost due to dangling pages.
```


# AWS cloud run results

1. [output](https://s3.console.aws.amazon.com/s3/buckets/page-rank-map-reduce?prefix=output/&region=us-east-1&bucketType=general)
2. [logfile](https://s3.console.aws.amazon.com/s3/object/page-rank-map-reduce?region=us-east-1&bucketType=general&prefix=log/j-1NNWHP2QDZBG5/steps/s-0793399GQTT2TGNUEZO/syslog.gz)
3. Time elapsed - 24 min 29 secs