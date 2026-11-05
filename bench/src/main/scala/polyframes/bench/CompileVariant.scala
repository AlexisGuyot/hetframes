package polyframes.bench

import java.nio.file.{Files, Paths}

import scala.tools.nsc.reporters.ConsoleReporter

/**
 * Compiles one file once and prints the compiler's messages in full, as a
 * user would see them. For trying a variant by hand: nothing is measured.
 *
 *   sbt "bench/runMain polyframes.bench.CompileVariant variants/pf-noattr.scala"
 *
 * A relative path is resolved against the root of the repository.
 */
object CompileVariant {

  def main(args: Array[String]): Unit = {
    val arg = args.headOption.getOrElse {
      System.err.println("usage: CompileVariant <file.scala>")
      sys.exit(2)
    }
    val path = Paths.get(arg)
    val source = if (path.isAbsolute) path else Workspace.root.resolve(path)
    if (!Files.exists(source)) {
      System.err.println(s"no such file: $source")
      sys.exit(2)
    }

    val compiles = Compiler.withTempDir("polyframes-compile") { out =>
      val global = Compiler.newGlobal(out, new ConsoleReporter(_))
      new global.Run().compile(List(source.toAbsolutePath.toString))
      val ok = !global.reporter.hasErrors
      global.close()
      ok
    }

    val name = source.getFileName
    println(if (compiles) s"\n$name: compiles" else s"\n$name: rejected at compile time")
  }
}
