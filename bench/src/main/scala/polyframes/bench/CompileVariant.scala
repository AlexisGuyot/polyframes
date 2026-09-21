package polyframes.bench

import java.nio.file.{Files, Paths}

import scala.reflect.io.{Directory, PlainDirectory}
import scala.tools.nsc.reporters.ConsoleReporter
import scala.tools.nsc.{Global, Settings}

/**
 * Compiles one file, once, and prints what the compiler reports, in full
 * and in the form a user of the library would see it.
 *
 * Meant for trying a variant by hand. Nothing is timed and nothing is
 * written to results.csv: the measured protocol is `sbt experiments`.
 *
 *   sbt "bench/runMain polyframes.bench.CompileVariant variants/pf-noattr.scala"
 *
 * A relative path is resolved against the root of the repository, since
 * sbt runs a forked program from the base directory of the subproject.
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

    val out = Files.createTempDirectory("polyframes-compile")
    val compiles =
      try {
        val settings = new Settings()
        settings.classpath.value = sys.props("java.class.path")
        settings.usejavacp.value = false
        settings.outputDirs.setSingleOutput(
          new PlainDirectory(new Directory(out.toFile))
        )
        val reporter = new ConsoleReporter(settings)
        val global = new Global(settings, reporter)
        new global.Run().compile(List(source.toAbsolutePath.toString))
        val ok = !reporter.hasErrors
        global.close()
        ok
      } finally {
        Directory(out.toFile).deleteRecursively()
        ()
      }

    val name = source.getFileName
    println(if (compiles) s"\n$name: compiles" else s"\n$name: rejected at compile time")
  }
}
