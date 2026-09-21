// =====================================================================
// PolyFrames - companion proof of concept for
// "Leveraging Types to Improve the Robustness of Analytical Pipelines
//  in Schema-on-Read Systems"
// =====================================================================

ThisBuild / scalaVersion := "2.13.16"     
ThisBuild / organization := "org.polyframes"
ThisBuild / version      := "0.1.0"

lazy val sparkVersion     = "4.0.1"       
lazy val shapelessVersion = "2.3.13"      

ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlint",
  "-Wconf:cat=unused-params:s" // Mute warnings implicits
)

lazy val sparkModuleOptions = Seq(
  "--add-opens=java.base/java.lang=ALL-UNNAMED",
  "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
  "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
  "--add-opens=java.base/java.io=ALL-UNNAMED",
  "--add-opens=java.base/java.net=ALL-UNNAMED",
  "--add-opens=java.base/java.nio=ALL-UNNAMED",
  "--add-opens=java.base/java.util=ALL-UNNAMED",
  "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
  "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
  "--add-opens=java.base/jdk.internal.ref=ALL-UNNAMED",
  "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
  "--add-opens=java.base/sun.nio.cs=ALL-UNNAMED",
  "--add-opens=java.base/sun.security.action=ALL-UNNAMED",
  "--add-opens=java.base/sun.util.calendar=ALL-UNNAMED",
  "--add-opens=java.security.jgss/sun.security.krb5=ALL-UNNAMED",
  "-Djdk.reflect.useDirectMethodHandle=false"
)

ThisBuild / fork := true  // Forked, so that the options above actually reach the JVM
ThisBuild / javaOptions ++= sparkModuleOptions
ThisBuild / javaOptions += "-Xmx8g"

// ---------------------------------------------------------------------
// core: the library. Kernel (types + inference) plus the runtime
// layer binding it to SparkSQL DataFrames.
// ---------------------------------------------------------------------
lazy val core = (project in file("core"))
  .settings(
    name := "polyframes",
    libraryDependencies ++= Seq(
      "com.chuusai"      %% "shapeless"  % shapelessVersion,
      "org.apache.spark" %% "spark-core" % sparkVersion,
      "org.apache.spark" %% "spark-sql"  % sparkVersion
    )
  )

// ---------------------------------------------------------------------
// bench: the experimental harness of section 5. Depends on core, and is
// kept separate so that the library alone can be compiled and timed.
// ---------------------------------------------------------------------
lazy val bench = (project in file("bench"))
  .dependsOn(core)
  .settings(
    name := "polyframes-bench",
    libraryDependencies ++= Seq(
      "org.apache.spark" %% "spark-core" % sparkVersion,
      "org.apache.spark" %% "spark-sql"  % sparkVersion,
      "org.scala-lang"    % "scala-compiler" % scalaVersion.value   // Needs the compiler as library to compile the pipeline variants one file at a time
    )
  )

lazy val root = (project in file("."))
  .aggregate(core, bench)
  .settings(name := "polyframes-root", publish / skip := true)

// Four programs, four JVMs
addCommandAlias("experiments",
  ";bench/runMain polyframes.bench.CompileWidth" +
  ";bench/runMain polyframes.bench.CompileVariants" +
  ";bench/runMain polyframes.bench.RunExperiments" +
  ";bench/runMain polyframes.bench.Report")
