/*
 * Warcbase: an open-source platform for managing web archives
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.warcbase.demo;

import java.util.Arrays;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.TableMapReduceUtil;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Counters;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.Logger;
import org.warcbase.io.ArcRecordWritable;
import org.warcbase.mapreduce.lib.HBaseRowToArcRecordWritableMapper;
import org.warcbase.mapreduce.lib.TableChainMapper;

public class WacMapReduceHBaseWrapperDemo extends Configured implements Tool {
  private static final Logger LOG = Logger.getLogger(WacMapReduceHBaseDemo.class);

  public WacMapReduceHBaseWrapperDemo() {}

  public static final String INPUT_OPTION = "input";
  public static final String OUTPUT_OPTION = "output";

  /**
   * Runs this tool.
   */
  @SuppressWarnings("static-access")
  public int run(String[] args) throws Exception {
    Options options = new Options();

    options.addOption(OptionBuilder.withArgName("path")
        .hasArg().withDescription("input path").create(INPUT_OPTION));
    options.addOption(OptionBuilder.withArgName("path")
        .hasArg().withDescription("output path").create(OUTPUT_OPTION));

    CommandLine cmdline;
    CommandLineParser parser = new GnuParser();
    try {
      cmdline = parser.parse(options, args);
    } catch (ParseException exp) {
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp(this.getClass().getName(), options);
      ToolRunner.printGenericCommandUsage(System.out);
      System.err.println("Error parsing command line: " + exp.getMessage());
      return -1;
    }

    if (!cmdline.hasOption(INPUT_OPTION) || !cmdline.hasOption(OUTPUT_OPTION)) {
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp(this.getClass().getName(), options);
      ToolRunner.printGenericCommandUsage(System.out);
      return -1;
    }

    String input = cmdline.getOptionValue(INPUT_OPTION);
    Path output = new Path(cmdline.getOptionValue(OUTPUT_OPTION));

    LOG.info("Tool name: " + WacMapReduceHBaseWrapperDemo.class.getSimpleName());
    LOG.info(" - input: " + input);
    LOG.info(" - output: " + output);

    Configuration config = HBaseConfiguration.create(getConf());
    // This should be fetched from external config files,
    // but not working due to weirdness in current config.
    config.set("hbase.zookeeper.quorum", "bespinrm.umiacs.umd.edu");

    Job job = Job.getInstance(config, WacMapReduceHBaseWrapperDemo.class.getSimpleName() + ":" + input);
    job.setJarByClass(WacMapReduceHBaseWrapperDemo.class);

    Scan scan = new Scan();
    scan.addFamily("c".getBytes());
    // Very conservative settings because a single row might not fit in memory
    // if we have many captured version of a URL.
    scan.setCaching(1); // Controls the number of rows to pre-fetch
    scan.setBatch(10); // Controls the number of columns to fetch on a per row basis
    scan.setCacheBlocks(false); // Don't set to true for MR jobs
    scan.setMaxVersions(); // We want all versions

    TableMapReduceUtil.initTableMapperJob(input, // input HBase table name
        scan, // Scan instance to control CF and attribute selection
        TableChainMapper.class, // mapper
        Text.class, // mapper output key
        Text.class, // mapper output value
        job);

    TableChainMapper.addMapper(job, HBaseRowToArcRecordWritableMapper.class,
        ImmutableBytesWritable.class, Result.class, LongWritable.class,
        ArcRecordWritable.class, job.getConfiguration());
    TableChainMapper.addMapper(job, WacMapReduceArcDemo.MyMapper.class, LongWritable.class,
        ArcRecordWritable.class, Text.class, Text.class, job.getConfiguration());

    job.setNumReduceTasks(0);
    FileOutputFormat.setOutputPath(job, output);
    job.setOutputFormatClass(TextOutputFormat.class);

    FileSystem fs = FileSystem.get(getConf());
    if (FileSystem.get(getConf()).exists(output)) {
      fs.delete(output, true);
    }

    long startTime = System.currentTimeMillis();
    job.waitForCompletion(true);
    LOG.info("Job Finished in " + (System.currentTimeMillis() - startTime) / 1000.0 + " seconds");

    Counters counters = job.getCounters();
    LOG.info("Read " + counters.findCounter(HBaseRowToArcRecordWritableMapper.Rows.TOTAL).getValue() + " rows.");
    LOG.info("Read " + counters.findCounter(WacMapReduceArcDemo.Records.TOTAL).getValue() + " records.");

    return 0;
  }

  /**
   * Dispatches command-line arguments to the tool via the <code>ToolRunner</code>.
   */
  public static void main(String[] args) throws Exception {
    LOG.info("Running " + WacMapReduceHBaseWrapperDemo.class.getCanonicalName() + " with args "
        + Arrays.toString(args));
    ToolRunner.run(new WacMapReduceHBaseWrapperDemo(), args);
  }
}
