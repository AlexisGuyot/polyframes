package polyframes.bench

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import scala.io.Source
import scala.util.Using

/**
 * Reads results.csv and writes a reading of it.
 *
 * Kept apart from the programs that measure, so that the report can be
 * rebuilt without measuring again, and so that nothing it says can come
 * from anywhere but the file.
 */
object Report {

  private def parse(line: String): List[String] = {
    val out = List.newBuilder[String]
    val cell = new StringBuilder
    var quoted = false
    var i = 0
    while (i < line.length) {
      val c = line.charAt(i)
      if (quoted) {
        if (c == '"') {
          if (i + 1 < line.length && line.charAt(i + 1) == '"') { cell += '"'; i += 1 }
          else quoted = false
        } else cell += c
      } else if (c == '"') quoted = true
      else if (c == ',') { out += cell.toString; cell.clear() }
      else cell += c
      i += 1
    }
    out += cell.toString
    out.result()
  }

  final case class Row(experiment: String, subject: String, baseline: String,
                       family: String, volume: String, width: String,
                       metric: String, value: String, note: String)

  def main(args: Array[String]): Unit = {
    if (!Files.exists(Results.path)) {
      System.err.println(s"${Results.path} not found. Run the experiments first.")
      sys.exit(1)
    }

    val rows = Using.resource(Source.fromFile(Results.path.toFile, "UTF-8")) { src =>
      src.getLines().drop(1).filter(_.nonEmpty).map(parse).collect {
        case List(e, s, b, f, v, w, m, value, n) => Row(e, s, b, f, v, w, m, value, n)
      }.toList
    }

    def find(p: Row => Boolean): Option[String] = rows.find(p).map(_.value)

    val sb = new StringBuilder
    sb ++= "# Results\n\n"
    sb ++= "Produced by `sbt experiments`, then `sbt \"bench/runMain polyframes.bench.Report\"`.\n"
    sb ++= "Every figure below is a row of `results.csv` and nothing else.\n"
    sb ++= "The machine, the pinned versions and the extracted data are described in\n"
    sb ++= "`README.md` and `data/PROVENANCE.txt`.\n\n"

    // --- where each error is caught ---
    val families = List(
      "noattr" -> "an attribute absent from the schema",
      "badtype" -> "an attribute of an unexpected domain",
      "badmodel" -> "data in an unexpected model",
      "badtransfo" -> "a transformation breaking the announced model")
    val baselines = List("polyframes" -> "pf", "dataframe" -> "df", "dataset" -> "ds")

    List(("P1", ""), ("P2", "2")).foreach { case (pipeline, suffix) =>
    sb ++= s"## Where each error is caught, $pipeline\n\n"
    sb ++= "| error | " + baselines.map(_._1).mkString(" | ") + " |\n"
    sb ++= "|---|" + baselines.map(_ => "---").mkString("|") + "|\n"
    families.foreach { case (family, label) =>
      val cells = baselines.map { case (_, prefix) =>
        val name = s"$prefix$suffix-$family"
        find(r => r.subject == name && r.metric == "compiles") match {
          case Some("false") => "compile time"
          case Some("true") =>
            find(r => r.subject == name && r.metric == "runs") match {
              case Some("false") => "run time"
              case Some("true")  => "**not detected**"
              case _             => "not run"
            }
          case _ => "-"
        }
      }
      sb ++= s"| $label | " + cells.mkString(" | ") + " |\n"
    }
    sb ++= "\n"
    }

    // --- compilation of the correct pipelines ---
    sb ++= "## Compiling the correct pipelines\n\n"
    sb ++= "| pipeline | compilation (ms) |\n|---|---|\n"
    List("p1", "p1-dataframe", "p1-dataset", "p2", "p2-dataframe", "p2-dataset").foreach { s =>
      sb ++= s"| $s | ${find(r => r.subject == s && r.metric == "compile-ms").getOrElse("-")} |\n"
    }

    // --- running the correct pipelines ---
    val volumes = rows.filter(_.experiment == "volume").map(_.volume).distinct
      .flatMap(v => scala.util.Try(v.toInt).toOption).sorted
    if (volumes.nonEmpty) {
      val subjects = List("p1", "p1-dataframe", "p1-dataset", "p2", "p2-dataframe", "p2-dataset")
      sb ++= "\n## Running the correct pipelines\n\n"
      sb ++= "| products | " + subjects
        .flatMap(s => List(s"$s ms", s"$s MB")).mkString(" | ") + " |\n"
      sb ++= "|---|" + (1 to subjects.size * 2).map(_ => "---").mkString("|") + "|\n"
      volumes.foreach { n =>
        val cells = subjects.flatMap { s =>
          List(
            find(r => r.experiment == "volume" && r.subject == s &&
              r.volume == n.toString && r.metric == "run-ms").getOrElse("-"),
            find(r => r.experiment == "volume" && r.subject == s &&
              r.volume == n.toString && r.metric == "heap-mb").getOrElse("-"))
        }
        sb ++= s"| $n | " + cells.mkString(" | ") + " |\n"
      }
    }

    // --- width ---
    val widths = rows.filter(_.experiment == "width")
      .flatMap(r => scala.util.Try(r.width.toInt).toOption.map(_ -> r.value)).sortBy(_._1)
    if (widths.nonEmpty) {
      sb ++= "\n## Compiling a declaration of growing width\n\n"
      sb ++= "| declared attributes | compilation (ms) |\n|---|---|\n"
      widths.foreach { case (w, v) => sb ++= s"| $w | $v |\n" }
    }

    // A guard against the failure mode that once made this table look right
    // for the wrong reason: variants dying of something the harness did
    // rather than of the error each was written to exhibit.
    //
    // The test is not that a message repeats. Variants of one family are
    // meant to fail alike, and do: every badmodel variant hits the same
    // multi-valued attribute at the sink, whichever API wrote it. What
    // would be suspicious is one message shared across families, since
    // four different mistakes have no reason to produce one failure.
    val failures = rows.filter(r => r.metric == "runs" && r.value == "false")
      .filter(_.note.nonEmpty)
    val acrossFamilies = failures.groupBy(_.note)
      .filter { case (_, all) => all.map(_.family).distinct.size > 1 }
    if (acrossFamilies.nonEmpty) {
      sb ++= "\n## Warning\n\n"
      sb ++= "One failure is shared by variants of different families, which means\n"
      sb ++= "they failed for a reason of their own rather than for the error each\n"
      sb ++= "was written to exhibit:\n\n"
      acrossFamilies.foreach { case (msg, all) =>
        sb ++= s"- ${all.map(_.subject).mkString(", ")}: `$msg`\n"
      }
    }

    sb ++= "\n## What each baseline reports, and when\n\n"
    sb ++= "| variant | failure |\n|---|---|\n"
    failures.foreach { r =>
      sb ++= s"| ${r.subject} | ${r.note.take(120).replace("|", "\\|")} |\n"
    }

    sb ++= "\n## Reading these figures\n\n"
    sb ++= "Compilation is timed in process, on a warm JVM, and the first run of\n"
    sb ++= "each file is discarded. The figures are therefore lower than what a\n"
    sb ++= "user waiting on a cold build would see, and are meant for comparing\n"
    sb ++= "the variants with one another.\n\n"
    sb ++= "Memory is sampled, not computed: a thread reads the used heap every\n"
    sb ++= "50 ms and the highest reading is kept. It sees the whole virtual\n"
    sb ++= "machine, not the pipeline alone.\n"

    Files.write(Workspace.resolve("RESULTS.md"), sb.toString.getBytes(StandardCharsets.UTF_8))
    println(sb.toString)
    println(s"Wrote ${Workspace.resolve("RESULTS.md")}")
  }
}
