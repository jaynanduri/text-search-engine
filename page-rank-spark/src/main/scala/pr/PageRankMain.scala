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
    }).groupByKey().partitionBy(new HashPartitioner(k)) //caching the graph to reuse it, since graph doesn't change

    // rank is obtained from using mapValues on graph so that both, RDDs use same partitioner.
    // This is effective for joining the two RDDs
    var ranks = graph.mapValues(f => if (f.size == numPages) 0.0 else initialPRValue).cache()

    // Starting page rank iterations
    for (i <- 0 until iter) {
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

//    ranks.saveAsTextFile(args(0))
    sc.stop()
  }
}
