import sbt._

object Dependencies {

  val zioVersion = "2.0.21"
  val zioHttpVersion = "3.0.0-RC4"

  val zioHttp = "dev.zio" %% "zio-http" % zioHttpVersion

  val jsoniter = "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core" % "2.28.2"
  val jsoniterMacros =
    "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "2.28.2" % "compile-internal"

  val zioQuill = "io.getquill" %% "quill-zio" % "4.8.1"
  val zioQuillPostgresAsync = "io.getquill" %% "quill-jasync-zio-postgres" % "4.8.0"

  val zioTest = "dev.zio" %% "zio-test" % zioVersion % Test
  val zioTestSBT = "dev.zio" %% "zio-test-sbt" % zioVersion % Test
  val zioTestMagnolia = "dev.zio" %% "zio-test-magnolia" % zioVersion % Test
}
