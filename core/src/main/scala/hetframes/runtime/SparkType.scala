package hetframes.runtime

import org.apache.spark.sql.types._
import shapeless.labelled.FieldType
import shapeless.{::, HList, HNil, Witness}

import scala.annotation.implicitNotFound

/**
 * Spark data type of an attribute domain, needed to declare the column an
 * operator creates. It replaces the `TypeTag` a Scala UDF would require,
 * which keeps Scala reflection out of the library.
 */
@implicitNotFound("HetFrames: ${T} has no counterpart among Spark data types.")
trait SparkType[T] { def dataType: DataType }

object SparkType {
  def apply[T](implicit st: SparkType[T]): SparkType[T] = st

  private def of[T](dt: DataType): SparkType[T] = new SparkType[T] {
    val dataType: DataType = dt
  }

  implicit val sparkString: SparkType[String]   = of(StringType)
  implicit val sparkInt: SparkType[Int]         = of(IntegerType)
  implicit val sparkLong: SparkType[Long]       = of(LongType)
  implicit val sparkDouble: SparkType[Double]   = of(DoubleType)
  implicit val sparkBoolean: SparkType[Boolean] = of(BooleanType)

  implicit def sparkList[T](implicit element: SparkType[T]): SparkType[List[T]] =
    of(ArrayType(element.dataType))

  implicit def sparkNested[S <: HList](implicit inner: SparkSchema[S]): SparkType[S] =
    of(inner.structType)
}

/**
 * Spark schema of a schema type, handed to the JSON reader so that it reads
 * only the declared attributes, without an inference pass over the data.
 */
@implicitNotFound("HetFrames: no SparkSQL schema for\n    ${S}")
trait SparkSchema[S <: HList] {
  def fields: List[StructField]
  final def structType: StructType = StructType(fields)
}

object SparkSchema {
  def apply[S <: HList](implicit s: SparkSchema[S]): SparkSchema[S] = s

  implicit val empty: SparkSchema[HNil] = new SparkSchema[HNil] {
    val fields: List[StructField] = Nil
  }

  implicit def attribute[K <: String, T, N <: HList](
      implicit
      key: Witness.Aux[K],
      domain: SparkType[T],
      tail: SparkSchema[N]
  ): SparkSchema[FieldType[K, T] :: N] = new SparkSchema[FieldType[K, T] :: N] {
    val fields: List[StructField] =
      StructField(key.value, domain.dataType, nullable = true) :: tail.fields
  }
}
