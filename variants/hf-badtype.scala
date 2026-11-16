package hetframes.variants

import org.apache.spark.sql.SparkSession
import hetframes.bench.Record._
import hetframes.bench.Schemas._
import hetframes.bench.{Loader, Sink}
import hetframes.ops.Ops._
import hetframes.types.{Document, Relation}
import shapeless.{::, HNil}

/**
 * HetFrames, an attribute of an unexpected domain.
 *
 * Expected: rejected at compile time.
 */
object HfBadType {

  type KeepNested =
    ("code" :: HNil) :: ("nutriscore_grade" :: HNil) ::
    ("is_beverage" :: HNil) :: ("nutriments" :: "carb_share" :: HNil) :: HNil

  type KeepFlat =
    ("code" :: HNil) :: ("nutriscore_grade" :: HNil) ::
    ("is_beverage" :: HNil) :: ("dense" :: HNil) :: HNil

  def main(args: Array[String]): Unit = {
    val path = args.headOption.getOrElse("data/products-10000.jsonl")
    val spark = SparkSession.builder().master("local[*]").appName("HfBadType").getOrCreate()
    spark.sparkContext.setLogLevel("WARN")

    val d0 = Loader.json[Document, SProduct](spark, path)

    // The attribute created at step 1 is given a textual domain.
    val d1 = add[Document, "nutriments" :: "carb_share" :: HNil](d0)(_ => "high")

    val d2 = add[Document, "is_beverage" :: HNil](d1)(s => s.at["pnns_groups_1"] == "Beverages")
    val d3 = project[Document, KeepNested](d2)

    // At step 4, it is used as a number. The domain recorded at
    // step 1 has travelled through two operators to meet this one.
    val d4 = add[Document, "dense" :: HNil](d3)(s => s.at["nutriments"].at["carb_share"] > 0.5)

    val d5 = project[Relation, KeepFlat](d4)
    val d6 = add[Relation, "flag" :: HNil](d5)(s => s.at["dense"] && !s.at["is_beverage"])
    Sink.typed(d6, Sink.scratch(spark, "HfBadType"))

    spark.stop()
  }
}
