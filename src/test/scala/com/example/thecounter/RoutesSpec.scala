package com.example.thecounter

import zio.test._
import zio.http._
import zio.ZIO
import zio.ZLayer
import com.github.plokhotnyuk.jsoniter_scala.core._

import Model._

object IncrementRouteSpec extends ZIOSpecDefault {

  object NoopIncrementService extends IncrementService {

    override def deleteAll(): ZIO[Any, Response, Unit] = ZIO.unit

    override def enqueue(incr: Model.Increment): ZIO[Any, Response, Boolean] = ZIO.succeed(true)

    override def all(): ZIO[Any, Response, Seq[Model.IncrementResult]] = ZIO.succeed(List.empty)

    override def lookup(key: String): ZIO[Any, Response, Option[Model.IncrementResult]] = ZIO.succeed(None)

  }

  val noopService = ZLayer.succeed(NoopIncrementService)

  def incrPostReq(increment: Increment) = {
    val json = writeToString(increment)
    Request.post("/increment", Body.fromCharSequence(json))
  }

  def runRequest(request: Request)(respFn: Response => TestResult): ZIO[Any, Nothing, TestResult] = {
    for {
      routes <- IncrementRoutes.make.provide(noopService)
      resp <- routes.toHttpApp.runZIO(request)
    } yield respFn(resp)
  }

  def run(increment: Increment)(respFn: Response => TestResult): ZIO[Any, Nothing, TestResult] = {
    runRequest(incrPostReq(increment))(respFn)
  }

  def spec = suite("suite for IncrementRoutes")(
    test("basic k/v increments call") {
      run(Increment("bob", 25)) { resp =>
        assertTrue(resp.status.code == 202)
      }
    },
    test("value must be positive") {
      run(Increment("bob", 0)) { resp =>
        assertTrue(resp.status.code == 400)
      }
    },
    test("invalid json") {
      val req = """{"keay": "32", "valu3": 33}"""
      runRequest(Request.post("/increment", Body.fromCharSequence(req))) { resp =>
        assertTrue(resp.status.code == 400)
      }
    }
  )
}
