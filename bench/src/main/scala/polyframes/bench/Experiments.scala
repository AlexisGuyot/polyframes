package polyframes.bench

import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * The protocol of section 5, in four programs.
 *
 *   sbt experiments
 *
 * which is an alias for running the four below in turn, each in a JVM of
 * its own. That separation is not a matter of tidiness, and it was arrived
 * at twice by experiment.
 *
 * Compiling and running cannot share a process: the compilation phase
 * leaves the compiler's own heap behind, and it would be counted as the
 * memory a pipeline requires. Measuring the two together gave figures that
 * disagreed with themselves.
 *
 * Nor can the two compilation phases: a fresh compiler is instantiated for
 * every file, and after a hundred of them the process spends its time
 * collecting garbage rather than compiling. The width axis is measured
 * first, and alone, because it is the phase whose figures are most
 * sensitive to that.
 */
object CompileWidth {

  def main(args: Array[String]): Unit = {
    val runs = args.headOption.map(_.toInt).getOrElse(3)
    println(s"project root: ${Workspace.root}")

    // Preflight. Everything measured from here on leans on the library
    // being visible to the compiler; if it is not, every file fails for
    // the same irrelevant reason and the run says nothing at all. This is
    // the first of the four programs, so it checks once for all of them.
    val probe = Files.createTempFile("polyframes-probe", ".scala")
    Files.write(probe, ("object PolyFramesProbe { " +
      "val m: polyframes.types.Model = null; " +
      "val r = polyframes.bench.Workspace.root }").getBytes("UTF-8"))
    val preflight = Compiler.compile(probe)
    Files.deleteIfExists(probe)
    if (!preflight.compiles) {
      System.err.println("The library is not visible to the compiler: " + preflight.firstError)
      System.err.println("Run `sbt bench/compile` first.")
      sys.exit(1)
    }
    println("preflight: the library is on the compiler's classpath")

    val missing = (Variants.All ++ Variants.Widths.map(_._2))
      .filterNot(v => Files.exists(v.source))
    if (missing.nonEmpty) {
      System.err.println("Missing source files:")
      missing.foreach(v => System.err.println("  " + v.source))
      sys.exit(1)
    }

    Results.start()

    // Measured first, and in a process of its own. A Global is created per
    // compilation, and the state of the heap after a hundred of them is
    // not the state a measurement should be taken in: the figures of this
    // axis moved by a factor of three between two runs for that reason
    // alone, before the phases were separated.
    println("\n=== compiling declarations of growing width ===")
    Variants.Widths.foreach { case (width, v) =>
      if (!Files.exists(v.source)) println(s"${v.name}: missing, skipped")
      else {
        val o = Compiler.timed(v.source, runs)
        println(f"$width%4d attributes  ${o.millis}%7d ms  compiles=${o.compiles}  " +
          o.firstError.take(60))
        Results.append(Measurement("width", v.name, "polyframes", "none", "",
          width.toString, "compile-ms", o.millis.toString,
          if (o.compiles) "" else o.firstError))
      }
    }

    println(s"\nAppended to ${Results.path}")
  }
}

object CompileVariants {

  def main(args: Array[String]): Unit = {
    val runs = args.headOption.map(_.toInt).getOrElse(3)
    println(s"project root: ${Workspace.root}")

    println("\n=== compiling every variant ===")
    Variants.All.foreach { v =>
      val o = Compiler.timed(v.source, runs)
      val agrees = if (o.compiles == v.expectedToCompile) "" else "   <-- UNEXPECTED"
      println(f"${v.name}%-15s compiles=${o.compiles}%-6s ${o.millis}%6d ms  " +
        o.firstError.take(70) + agrees)
      Results.appendAll(Seq(
        Measurement("error-prevention", v.name, v.baseline, v.family, "", "",
          "compiles", o.compiles.toString, o.firstError),
        Measurement("error-prevention", v.name, v.baseline, v.family, "", "",
          "compile-ms", o.millis.toString, s"median of ${runs - 1} runs")
      ))
    }

    println(s"\nAppended to ${Results.path}")
  }
}

/**
 * Runs what compiles, and measures what the correct pipelines cost.
 *
 * One protocol throughout: every figure is the median of `runs` executions
 * with the first discarded, and every volume point is measured the same
 * way, the smallest one included. There is no separate overhead
 * measurement, because a separate measurement means a differently warmed
 * JVM and figures that cannot be compared with the rest.
 */
object RunExperiments {

  def main(args: Array[String]): Unit = {
    val runs = args.headOption.map(_.toInt).getOrElse(3)
    println(s"project root: ${Workspace.root}")
    Workspace.clearOut()

    // Preflight. The variants are compiled and loaded through machinery of
    // the harness's own, and if that machinery is broken every one of them
    // fails for the same irrelevant reason while appearing to confirm what
    // was expected of it. So a pipeline known to work is put through the
    // very same path first.
    val check = Runner.compileAndRun(
      Variants.All.head.source, Variants.All.head.objectName,
      Array(Workspace.data(reference), Workspace.grades))
    if (!check.runs) {
      System.err.println("The reference pipeline does not run through the harness: " + check.failure)
      System.err.println("Nothing can be concluded from the variants until this passes.")
      sys.exit(1)
    }
    println(f"preflight: the reference pipeline runs (${check.millis}%d ms)")

    println("\n=== running the variants that compile ===")
    Variants.All.filter(_.family != "none").foreach { v =>
      val o = Runner.compileAndRun(v.source, v.objectName,
        Array(Workspace.data(RunExperiments.reference), Workspace.grades))
      if (o.failure.startsWith("did not compile"))
        println(f"${v.name}%-15s (rejected at compile time, nothing to run)")
      else {
        println(f"${v.name}%-15s runs=${o.runs}%-6s  ${o.failure.take(80)}")
        Results.append(Measurement("error-prevention", v.name, v.baseline, v.family,
          "", "", "runs", o.runs.toString, o.failure))
      }
    }

    println("\n=== the correct pipelines, at each volume ===")
    val points = FetchData.VolumePoints
      .filter(n => Files.exists(Workspace.resolve("data", s"products-$n.jsonl")))
    if (points.isEmpty) println("no extracted data found; run FetchData first")

    points.foreach { n =>
      val path = Workspace.data(n)
      Pipelines.Correct.foreach { case (name, baseline, run) =>
        val outcomes = (1 to runs).map { _ =>
          Pipelines.withSpark(s"$name-$n")(s => Runner.measure(() => run(s, path)))
        }
        val kept = if (outcomes.length > 1) outcomes.tail else outcomes
        val times = kept.map(_.millis).sorted
        val heaps = kept.map(_.peakHeapMb).sorted
        val failed = outcomes.filterNot(_.runs).map(_.failure).headOption.getOrElse("")
        println(f"$n%8d  $name%-15s ${times(times.length / 2)}%6d ms  " +
          f"${heaps(heaps.length / 2)}%5d MB  $failed")
        Results.appendAll(Seq(
          Measurement("volume", name, baseline, "none", n.toString, "", "run-ms",
            times(times.length / 2).toString, s"median of ${runs - 1} runs"),
          Measurement("volume", name, baseline, "none", n.toString, "", "heap-mb",
            heaps(heaps.length / 2).toString, "peak sampled every 50 ms")
        ))
      }
    }
    println(s"\nAppended to ${Results.path}")
  }

  val reference: Int = 10000
}

/** The three correct pipelines, and how to run one. */
object Pipelines {

  type Run = (org.apache.spark.sql.SparkSession, String) => Unit

  val Correct: List[(String, String, Run)] = List(
    ("p1", "polyframes",
      (s, p) => Sink.typed(P1(s, p), Workspace.out("p1"))),
    ("p1-dataframe", "dataframe",
      (s, p) => Sink.untyped(P1DataFrame(s, p), Workspace.out("p1-dataframe"))),
    ("p1-dataset", "dataset",
      (s, p) => Sink.ofDataset(P1Dataset(s, p), Workspace.out("p1-dataset"))),
    ("p2", "polyframes",
      (s, p) => Sink.typed(P2(s, p, Workspace.grades), Workspace.out("p2"))),
    ("p2-dataframe", "dataframe",
      (s, p) => Sink.untyped(P2DataFrame(s, p, Workspace.grades), Workspace.out("p2-dataframe"))),
    ("p2-dataset", "dataset",
      (s, p) => Sink.ofDataset(P2Dataset(s, p, Workspace.grades), Workspace.out("p2-dataset")))
  )

  def withSpark[A](name: String)(body: org.apache.spark.sql.SparkSession => A): A = {
    val spark = org.apache.spark.sql.SparkSession.builder()
      .master("local[*]").appName(name).getOrCreate()
    spark.sparkContext.setLogLevel("ERROR")
    try body(spark) finally spark.stop()
  }
}
