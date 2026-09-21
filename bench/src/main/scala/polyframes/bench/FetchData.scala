package polyframes.bench

import java.io.{BufferedWriter, FileOutputStream, OutputStreamWriter, PrintWriter}
import java.net.{HttpURLConnection, URI}
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.GZIPInputStream

import scala.io.Source
import scala.util.Using

/**
 * Extracts the working subset from the Open Food Facts products dump.
 *
 * The dump is a gzipped JSONL file of several gigabytes. Nothing here
 * downloads it whole: the stream is decompressed on the fly and closed as
 * soon as enough lines have been read, so only the head of the file
 * travels. The largest volume point is written first and the smaller ones
 * are prefixes of it, which keeps them nested rather than independent
 * samples and makes the volume axis a growth rather than a resampling.
 *
 *   sbt "bench/runMain polyframes.bench.FetchData"
 *   sbt "bench/runMain polyframes.bench.FetchData 200000"
 *
 * Writes data/products-<n>.jsonl for each volume point, and
 * data/PROVENANCE.txt recording where the data came from and when.
 */
object FetchData {

  val DumpUrl = "https://static.openfoodfacts.org/data/openfoodfacts-products.jsonl.gz"

  val VolumePoints: List[Int] = List(10000, 50000, 100000, 250000, 500000)

  def main(args: Array[String]): Unit = {
    val points = args.headOption.map(a => List(a.toInt)).getOrElse(VolumePoints).sorted
    val largest = points.max
    val dir = Workspace.resolve("data")
    Files.createDirectories(dir)

    println(s"Fetching the first $largest products from $DumpUrl")
    println("Only the head of the dump is transferred; the stream is closed early.")

    val connection = URI.create(DumpUrl).toURL.openConnection().asInstanceOf[HttpURLConnection]
    connection.setRequestProperty("User-Agent", "PolyFrames/0.1 (research artefact)")
    connection.connect()

    val code = connection.getResponseCode
    if (code != 200) {
      System.err.println(s"HTTP $code for $DumpUrl")
      System.err.println("If the dump has moved, adjust DumpUrl in FetchData.scala.")
      sys.exit(1)
    }
    val lastModified = Option(connection.getHeaderField("Last-Modified")).getOrElse("unknown")

    val writers = points.map { n =>
      val w = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
        new FileOutputStream(dir.resolve(s"products-$n.jsonl").toFile),
        StandardCharsets.UTF_8
      )))
      (n, w)
    }

    var kept = 0
    Using.resource(new GZIPInputStream(connection.getInputStream, 1 << 16)) { gz =>
      val lines = Source.fromInputStream(gz, "UTF-8").getLines()
      while (kept < largest && lines.hasNext) {
        val line = lines.next()
        if (line.nonEmpty) {
          writers.foreach { case (n, w) => if (kept < n) w.println(line) }
          kept += 1
          if (kept % 25000 == 0) println(s"  $kept products")
        }
      }
    }
    writers.foreach(_._2.close())
    connection.disconnect()

    val provenance =
      s"""Source: $DumpUrl
         |Licence: Open Database License (ODbL). Contents under DbCL.
         |Attribution: Data from Open Food Facts, licensed under the ODbL.
         |Dump Last-Modified: $lastModified
         |Retrieved: ${java.time.Instant.now()}
         |Products kept: $kept
         |Volume points: ${points.mkString(", ")}
         |
         |Each file is a prefix of the next, so the volume axis grows a
         |single sample rather than drawing independent ones.
         |""".stripMargin
    Files.write(dir.resolve("PROVENANCE.txt"), provenance.getBytes(StandardCharsets.UTF_8))

    println(s"\nWrote $kept products under ${dir.toAbsolutePath}")
    points.foreach(n => println(s"  products-$n.jsonl"))
    println("  PROVENANCE.txt")
    println(s"\nDump Last-Modified: $lastModified")
    println("Report this date in DATA.md and in section 5.1.")
  }
}
