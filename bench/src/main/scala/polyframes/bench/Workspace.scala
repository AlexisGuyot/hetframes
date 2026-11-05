package polyframes.bench

import java.nio.file.{Files, Path, Paths}

/**
 * Where the project is on disk: the first directory holding a build.sbt,
 * walking up from the current one, whatever directory a program is
 * launched from (sbt, an IDE, a shell).
 */
object Workspace {

  lazy val root: Path = {
    val start = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    Iterator.iterate(start)(_.getParent)
      .takeWhile(_ != null)
      .find(d => Files.exists(d.resolve("build.sbt")))
      .getOrElse(start)
  }

  def resolve(segments: String*): Path =
    segments.foldLeft(root)((p, s) => p.resolve(s))

  /** The extracted subset holding `products` products. */
  def data(products: Int): String =
    resolve("data", s"products-$products.jsonl").toString

  /**
   * A fresh output directory for each write, outside the repository: on
   * Windows, Spark fails to clear one a previous run left behind. The output
   * is never read; writing it is what makes a pipeline run.
   */
  def out(name: String): String =
    Paths.get(outBase.toString, s"$name-${System.nanoTime()}").toString

  /**
   * The system temporary folder, unless the property or environment
   * variable `POLYFRAMES_OUT` says otherwise (on Windows, a folder excluded
   * from antivirus scanning avoids intermittent "Mkdirs failed" errors).
   */
  private def outBase: Path =
    sys.props.get("POLYFRAMES_OUT").orElse(sys.env.get("POLYFRAMES_OUT"))
      .map(Paths.get(_))
      .getOrElse(Paths.get(sys.props("java.io.tmpdir"), "polyframes-out"))

  /** Best-effort removal of everything the runs above wrote. */
  def clearOut(): Unit = {
    val base = outBase
    if (Files.exists(base)) {
      try {
        Files.walk(base).sorted(java.util.Comparator.reverseOrder[Path]())
          .forEach(p => { Files.deleteIfExists(p); () })
      } catch { case _: Exception => () }
    }
  }

  /** The reference table P2 integrates with, versioned with the code. */
  def grades: String = resolve("reference", "nutriscore-grades.csv").toString
}
