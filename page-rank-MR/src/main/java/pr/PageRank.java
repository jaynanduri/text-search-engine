package pr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.NLineInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

public class PageRank extends Configured implements Tool {
	private static final Logger logger = LogManager.getLogger(PageRank.class);

	public static class PageRankMap extends Mapper<Object, Text, Text, Text> {
		private long numVertices;

		@Override
		protected void setup(Context context) {
			Configuration conf = context.getConfiguration();
			numVertices = Long.parseLong(conf.get("number.vertices"));
		}

		@Override
		public void map(final Object key, final Text value, final Context context)
						throws IOException, InterruptedException {
			String[] graph = value.toString().split(" ");
			String node = graph[0];
			double pageRank = Double.parseDouble(graph[1]);
			List<String> adjList = new ArrayList<>();
			if (graph.length > 2){
				adjList = List.of(graph[2].split(","));
			}
			context.write(new Text(node), new Text("vertex " + String.join(",", adjList)));
			if (node.equals("0")){
				long k = numVertices * numVertices;
				for (int i=1; i<=k; i++){
					context.write(new Text(String.valueOf(i)), new Text("pageRank " + pageRank/k));
				}
			} else {
				double pageRankContribution = pageRank / adjList.size();
				for (String nextNode: adjList){
					context.write(new Text(nextNode), new Text("pageRank " + pageRankContribution));
				}
			}
		}
	}

	public static class PageRankReducer extends Reducer<Text, Text, Text, Text> {
		private long numVertices;
		private float dampingFactor;

		@Override
		protected void setup(Context context) {
			Configuration conf = context.getConfiguration();
			numVertices = Long.parseLong(conf.get("number.vertices"));
			dampingFactor = Float.parseFloat(conf.get("damping.factor"));
		}

		@Override
		public void reduce(final Text key, final Iterable<Text> values, final Context context)
						throws IOException, InterruptedException {
			double pageRankSum = 0;
			double newPageRank;
			List<String> adjList = new ArrayList<>();
			for(Text val : values){
				String[] type = val.toString().split(" ");
				if (Objects.equals(type[0], "vertex")){
					if (type.length > 1) {
						adjList = List.of(type[1]);
					}
				} else {
					pageRankSum += Double.parseDouble(type[1]);
				}
			}
			long k = numVertices * numVertices;
			newPageRank = ((1-dampingFactor)/(k + 1)) + (pageRankSum * dampingFactor);
			context.write(new Text(key), new Text(newPageRank + " " +
																						String.join(",", adjList)));
		}
	}

	@Override
	public int run(final String[] args) throws Exception {
		// program parameters
		Configuration conf = new Configuration();
		String dampingFactor = args[5];
		int linesPerMap = Integer.parseInt(args[4]);
		int iterations = Integer.parseInt(args[3]);
		String numVertices = args[2];
		String outFilePath = args[1]; // base input file path
		String inFilePath = args[0];  // base output file path

		conf.setInt("mapreduce.input.lineinputformat.linespermap", linesPerMap);
		conf.set("number.vertices", numVertices);
		conf.set("damping.factor", dampingFactor);
		conf.set("mapreduce.output.textoutputformat.separator", " ");

		for (int i = 0; i < iterations; i++) {
			Job job = Job.getInstance(conf, "Page Rank Iteration " + (i + 1));
			job.setJarByClass(PageRank.class);

			job.setMapperClass(PageRankMap.class);
			job.setReducerClass(PageRankReducer.class);

			job.setOutputKeyClass(Text.class);
			job.setOutputValueClass(Text.class);

			job.setInputFormatClass(NLineInputFormat.class);
			job.setOutputFormatClass(TextOutputFormat.class);

			// Set input path for this iteration
			if (i == 0) {
				// First iteration reads from original input
				FileInputFormat.addInputPath(job, new Path(inFilePath));
			} else {
				// Subsequent iterations read from previous iteration's output directory
				// Hadoop automatically processes all part-* files in this directory
				FileInputFormat.addInputPath(job, new Path(outFilePath + "/iteration-" + (i - 1)));
			}

			// Set output path for this iteration
			Path outputDir = new Path(outFilePath + "/iteration-" + i);
			FileOutputFormat.setOutputPath(job, outputDir);

			// Remember to delete output directory if it exists (useful for rerunning the program)
//			FileSystem hdfs = FileSystem.get(conf);
//			if (hdfs.exists(outputDir)) {
//				hdfs.delete(outputDir, true);
//			}

			if (!job.waitForCompletion(true)) {
				System.exit(1); // If job fails, terminate
			}
		}

		return 1;
	}

	public static void main(final String[] args) {
		if (args.length != 6) {
			throw new Error("Two arguments required:\n<input-dir> <output-dir> <num-vertices> "
											+ "<num-iterations> <lines-per-map> <damping-factor>");
		}

		try {
			ToolRunner.run(new PageRank(), args);
		} catch (final Exception e) {
			logger.error("", e);
		}
	}

}