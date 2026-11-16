package hetframes.bench

import java.nio.file.Path

/**
 * The 30 programs section 5 compiles: the reference pipeline written
 * three ways, and the four families of engineering errors injected into
 * each of the three.
 *
 * The three correct pipelines are read from where they live, in the source
 * tree of the harness, rather than copied into `variants/`. Only the
 * erroneous ones need to sit outside a source directory, and only because
 * four of them must not compile.
 */
object Variants {

  final case class Variant(
      name: String,
      baseline: String,
      family: String,
      source: Path,
      expectedToCompile: Boolean,
      objectName: String = ""
  )

  private def bench(file: String): Path =
    Workspace.resolve("bench", "src", "main", "scala", "hetframes", "bench", file)

  private def variant(file: String): Path = Workspace.resolve("variants", file)

  val All: List[Variant] = List(
    Variant("p1",            "hetframes", "none",       bench("P1.scala"),          true,  "hetframes.bench.P1"),
    Variant("p1-dataframe",  "dataframe",  "none",       bench("P1DataFrame.scala"), true,  "hetframes.bench.P1DataFrame"),
    Variant("p1-dataset",    "dataset",    "none",       bench("P1Dataset.scala"),   true,  "hetframes.bench.P1Dataset"),

    Variant("hf-noattr",     "hetframes", "noattr",     variant("hf-noattr.scala"),     false, "hetframes.variants.HfNoAttr"),
    Variant("hf-badtype",    "hetframes", "badtype",    variant("hf-badtype.scala"),    false, "hetframes.variants.HfBadType"),
    Variant("hf-badmodel",   "hetframes", "badmodel",   variant("hf-badmodel.scala"),   false, "hetframes.variants.HfBadModel"),
    Variant("hf-badtransfo", "hetframes", "badtransfo", variant("hf-badtransfo.scala"), false, "hetframes.variants.HfBadTransfo"),

    Variant("df-noattr",     "dataframe",  "noattr",     variant("df-noattr.scala"),     true,  "hetframes.variants.DfNoAttr"),
    Variant("df-badtype",    "dataframe",  "badtype",    variant("df-badtype.scala"),    true,  "hetframes.variants.DfBadType"),
    Variant("df-badmodel",   "dataframe",  "badmodel",   variant("df-badmodel.scala"),   true,  "hetframes.variants.DfBadModel"),
    Variant("df-badtransfo", "dataframe",  "badtransfo", variant("df-badtransfo.scala"), true,  "hetframes.variants.DfBadTransfo"),

    Variant("ds-noattr",     "dataset",    "noattr",     variant("ds-noattr.scala"),     false, "hetframes.variants.DsNoAttr"),
    Variant("ds-badtype",    "dataset",    "badtype",    variant("ds-badtype.scala"),    false, "hetframes.variants.DsBadType"),
    Variant("ds-badmodel",   "dataset",    "badmodel",   variant("ds-badmodel.scala"),   true,  "hetframes.variants.DsBadModel"),
    Variant("ds-badtransfo", "dataset",    "badtransfo", variant("ds-badtransfo.scala"), true,  "hetframes.variants.DsBadTransfo"),

    // P2: the same grid, on the pipeline that integrates two sources with
    // the whole palette of operators.
    Variant("p2",            "hetframes", "none",       bench("P2.scala"),          true,  "hetframes.bench.P2"),
    Variant("p2-dataframe",  "dataframe",  "none",       bench("P2DataFrame.scala"), true,  "hetframes.bench.P2DataFrame"),
    Variant("p2-dataset",    "dataset",    "none",       bench("P2Dataset.scala"),   true,  "hetframes.bench.P2Dataset"),

    Variant("hf2-noattr",     "hetframes", "noattr",     variant("hf2-noattr.scala"),     false, "hetframes.variants.Hf2NoAttr"),
    Variant("hf2-badtype",    "hetframes", "badtype",    variant("hf2-badtype.scala"),    false, "hetframes.variants.Hf2BadType"),
    Variant("hf2-badmodel",   "hetframes", "badmodel",   variant("hf2-badmodel.scala"),   false, "hetframes.variants.Hf2BadModel"),
    Variant("hf2-badtransfo", "hetframes", "badtransfo", variant("hf2-badtransfo.scala"), false, "hetframes.variants.Hf2BadTransfo"),

    Variant("df2-noattr",     "dataframe",  "noattr",     variant("df2-noattr.scala"),     true,  "hetframes.variants.Df2NoAttr"),
    Variant("df2-badtype",    "dataframe",  "badtype",    variant("df2-badtype.scala"),    true,  "hetframes.variants.Df2BadType"),
    Variant("df2-badmodel",   "dataframe",  "badmodel",   variant("df2-badmodel.scala"),   true,  "hetframes.variants.Df2BadModel"),
    Variant("df2-badtransfo", "dataframe",  "badtransfo", variant("df2-badtransfo.scala"), true,  "hetframes.variants.Df2BadTransfo"),

    Variant("ds2-noattr",     "dataset",    "noattr",     variant("ds2-noattr.scala"),     false, "hetframes.variants.Ds2NoAttr"),
    Variant("ds2-badtype",    "dataset",    "badtype",    variant("ds2-badtype.scala"),    false, "hetframes.variants.Ds2BadType"),
    Variant("ds2-badmodel",   "dataset",    "badmodel",   variant("ds2-badmodel.scala"),   true,  "hetframes.variants.Ds2BadModel"),
    Variant("ds2-badtransfo", "dataset",    "badtransfo", variant("ds2-badtransfo.scala"), true,  "hetframes.variants.Ds2BadTransfo")
  )

  /** Which pipeline a variant belongs to, read off its name. */
  def pipelineOf(v: Variant): String = if (v.name.contains("2")) "P2" else "P1"

  /** The width axis: the same three steps over declarations of growing
    * width. Only their compilation is measured. */
  val Widths: List[(Int, Variant)] = List(5, 10, 20, 40, 80).map { n =>
    n -> Variant(s"width-$n", "hetframes", "none",
      variant(s"width-$n.scala"), true, s"hetframes.variants.Width$n")
  }
}
