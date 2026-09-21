package polyframes.bench

import java.nio.file.{Files, Path}

import scala.reflect.io.{Directory, PlainDirectory}
import scala.tools.nsc.reporters.StoreReporter
import scala.tools.nsc.{Global, Settings}

/** What compiling one file produced. */
final case class CompileOutcome(
    compiles: Boolean,
    millis: Long,
    firstError: String
)

/**
 * Compiles one source file at a time, against the classpath of the
 * harness itself.
 *
 * Compiling in process rather than by launching sbt once per file has two
 * reasons. It measures the compilation and nothing else, where a fresh sbt
 * would also measure its own startup and its dependency resolution; and it
 * lets a file that must fail to compile do so without stopping the others,
 * which is why the erroneous variants live outside the source tree in the
 * first place.
 *
 * The price is that the compiler runs on a warm JVM. A timing taken this
 * way is lower than what a user waiting on a cold sbt would see, so the
 * figures reported are useful for comparing variants with one another
 * rather than as absolute costs. The harness discards the first run of
 * each file for the same reason.
 */
object Compiler {

  /** The classpath of the forked JVM running the harness, which sbt has
    * already populated with Spark, Shapeless and the compiled library. */
  private val classpath: String = sys.props("java.class.path")

  /** Reports how long the current file has been compiling, so that a
    * compilation that has stopped making progress is visible within the
    * minute rather than after an afternoon. */
  private def beat(name: String, startedNanos: Long): Thread = {
    val t = new Thread(() => {
      try while (!Thread.currentThread().isInterrupted) {
        Thread.sleep(30000L)
        val s = (System.nanoTime() - startedNanos) / 1000000000L
        println(s"  ... still compiling $name after $s s")
      } catch { case _: InterruptedException => () }
    })
    t.setDaemon(true)
    t.start()
    t
  }

  def compile(source: Path): CompileOutcome = {
    val out = Files.createTempDirectory("polyframes-compile")
    try compileTo(source, out)
    finally {
      Directory(out.toFile).deleteRecursively()
      ()
    }
  }

  /** Compiles into a directory the caller owns, so that what was produced
    * can afterwards be loaded and run. */
  def compileTo(source: Path, out: Path): CompileOutcome = {
    {
      val settings = new Settings()
      settings.classpath.value = classpath
      settings.usejavacp.value = false
      settings.outputDirs.setSingleOutput(
        new PlainDirectory(new Directory(out.toFile))
      )

      val reporter = new StoreReporter(settings)
      val global = new Global(settings, reporter)

      val started = System.nanoTime()
      val heartbeat = beat(source.getFileName.toString, started)
      try new global.Run().compile(List(source.toAbsolutePath.toString))
      finally heartbeat.interrupt()
      val elapsed = (System.nanoTime() - started) / 1000000L

      val error = reporter.infos
        .find(_.severity == reporter.ERROR)
        .map(i => i.msg.linesIterator.next().trim)
        .getOrElse("")

      // A Global holds a symbol table indexed over the whole classpath,
      // Spark included. Left to accumulate, a few dozen of them fill any
      // heap and the run collapses into garbage collection, which looks
      // from outside like a compilation that never ends.
      global.close()
      System.gc()

      CompileOutcome(!reporter.hasErrors, elapsed, error)
    }
  }

  /** Compiles a file several times and keeps the median, discarding the
    * first run, which pays for loading the compiler itself. */
  def timed(source: Path, runs: Int): CompileOutcome = {
    val outcomes = (1 to runs).map(_ => compile(source))
    val kept = if (outcomes.length > 1) outcomes.tail else outcomes
    val times = kept.map(_.millis).sorted
    outcomes.head.copy(millis = times(times.length / 2))
  }
}
