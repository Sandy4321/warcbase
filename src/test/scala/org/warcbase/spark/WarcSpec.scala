package org.warcbase.spark

import com.google.common.io.Resources
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}
import org.scalatest.{BeforeAndAfter, FunSuite}
import org.warcbase.spark.matchbox.RecordLoader
import org.warcbase.spark.matchbox.RecordTransformers.WARecord

class WarcSpec extends FunSuite with BeforeAndAfter {

  private val warcPath = Resources.getResource("warc/example.warc.gz").getPath
  private val master = "local[2]"
  private val appName = "example-spark"
  private var sc: SparkContext = _
  private var records: RDD[WARecord] = _

  before {
    val conf = new SparkConf()
      .setMaster(master)
      .setAppName(appName)
    sc = new SparkContext(conf)
    records = RecordLoader.loadWarc(warcPath, sc)
  }

  test("count records") {
    assert(RecordLoader.loadWarc(warcPath, sc).count == 299L)
  }

  after {
    if (sc != null) {
      sc.stop()
    }
  }
}

