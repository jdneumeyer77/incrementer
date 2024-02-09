import Dependencies._

organization := "com.example"
version := "0.0.2"
scalaVersion := "2.13.12"
// ThisBuild / fork              := true
//ThisBuild / scalacOptions     += "-Yrangepos"
//ThisBuild / semanticdbEnabled := true
//ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

def settingsApp = Seq(
  name := "the-counter",
  Compile / run / mainClass := Option("com.example.thecounter.MainApp"),
  testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
  libraryDependencies ++= Seq(
    zioHttp,
    zioJson,
    zioTest,
    zioTestSBT,
    zioTestMagnolia,
    zioQuill,
    zioQuillPostgresAsync
  )
)

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging)
  .settings(settingsApp)

// addCommandAlias("fmt", "scalafmt; Test / scalafmt; sFix;")
//addCommandAlias("fmtCheck", "scalafmtCheck; Test / scalafmtCheck; sFixCheck")
