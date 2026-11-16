package hetframes

import org.apache.spark.sql.api.java.UDF1
import org.apache.spark.sql.functions.{col, expr, lit}
import org.apache.spark.sql.types._
import org.apache.spark.sql.{Row, SparkSession}
import hetframes.runtime._
import shapeless.labelled.FieldType
import shapeless.{::, HNil}

import scala.jdk.CollectionConverters._

/**
 * The building blocks of the binding to SparkSQL, one by one, so that a
 * failure points at one of them rather than at a whole operator.
 *
 *   sbt "core/Test/runMain hetframes.RuntimeSpec"
 *
 * On Windows, this is the first program needing the Hadoop binaries.
 */
object RuntimeSpec {

  type SIdentity = FieldType["names", List[String]] :: FieldType["age", Int] :: HNil
  type SDoc      = FieldType["id", Int] :: FieldType["identity", SIdentity] :: HNil

  def main(args: Array[String]): Unit = {

    // 1. FromRow: flat, nested with a collection, and a null collection.
    val flat = implicitly[FromRow[FieldType["id", Int] :: FieldType["name", String] :: HNil]]
    val f = flat(Row(1, "chocolate"), 0)
    assert(f.head == 1 && f.tail.head == "chocolate", s"flat: $f")

    val decode = implicitly[FromRow[SDoc]]
    val n = decode(Row(42, Row(Seq("ada", "alan"), 25)), 0)
    assert(n.head == 42, s"got ${n.head}")
    assert(n.tail.head.head == List("ada", "alan"), s"got ${n.tail.head.head}")
    assert(n.tail.head.tail.head == 25, s"got ${n.tail.head.tail.head}")
    assert(decode(Row(7, Row(null, 30)), 0).tail.head.head == Nil, "null collection")
    println("1. FromRow ok")

    val spark = SparkSession.builder().master("local[*]").appName("runtime").getOrCreate()
    spark.sparkContext.setLogLevel("WARN")

    // The source deliberately has identity before id, and age before names.
    val identity = StructType(Seq(
      StructField("age", IntegerType),
      StructField("names", ArrayType(StringType))
    ))
    val schema = StructType(Seq(
      StructField("identity", identity),
      StructField("id", IntegerType)
    ))
    val df = spark.createDataFrame(List(
      Row(Row(25, Seq("ada", "alan")), 42),
      Row(Row(30, Seq("maeva")), 7)
    ).asJava, schema)

    def fields(d: org.apache.spark.sql.DataFrame, at: String): List[String] =
      d.schema(at).dataType.asInstanceOf[StructType].fieldNames.toList

    // 2. Shape: columns laid out in schema order, at every level.
    val laid = df.select(Shape[SDoc].columns(n => col(s"`$n`")): _*)
    assert(laid.schema.fieldNames.toList == List("id", "identity"), "top-level order")
    assert(fields(laid, "identity") == List("names", "age"), "nested order")
    println("2. Shape ok")

    // 3. Target: insertion, removal and renaming of a nested field.
    val tgt = implicitly[Target["identity" :: "adult" :: HNil]]
    val withAdult = tgt.insert(laid, lit(true))
    assert(fields(withAdult, "identity").contains("adult"), "insert")
    assert(!fields(tgt.remove(withAdult), "identity").contains("adult"), "remove")
    val renamed = implicitly[Target["identity" :: "age" :: HNil]].renameTo(laid, "years")
    assert(fields(renamed, "identity").contains("years"), "renameTo")
    println("3. Target ok")

    // 4. A UDF over the whole record, as the operators register it.
    val fn: SDoc => Boolean = s => s.tail.head.tail.head >= 18
    spark.udf.register(
      "hf_probe",
      new UDF1[Row, Boolean] { def call(r: Row): Boolean = fn(decode(r, 0)) },
      SparkType[Boolean].dataType
    )
    val out = laid.withColumn("adult", expr("hf_probe(struct(*))"))
      .select("id", "adult").collect().map(r => (r.getInt(0), r.getBoolean(1))).toSet
    assert(out == Set((42, true), (7, true)), s"udf output: $out")
    println("4. record-level UDF ok")

    spark.stop()
    println("RuntimeSpec: all checks passed")
  }
}
