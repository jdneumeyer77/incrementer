import sbt._

object Dependencies {

  val zioVersion = "2.0.21"
  val zioHttpVersion = "3.0.0-RC4"

  val zioHttp = "dev.zio" %% "zio-http" % zioHttpVersion

  val zioJson = "dev.zio" %% "zio-json" % "0.6.2"

  val zioQuill = "io.getquill" %% "quill-zio" % "4.8.1"
  val zioQuillPostgresAsync = "io.getquill" %% "quill-jasync-postgres" % "4.8.0"

  val zioTest = "dev.zio" %% "zio-test" % zioVersion % Test
  val zioTestSBT = "dev.zio" %% "zio-test-sbt" % zioVersion % Test
  val zioTestMagnolia = "dev.zio" %% "zio-test-magnolia" % zioVersion % Test
}
