package com.example.thecounter

import zio._
import zio.http._
import zio.http.endpoint._
import zio.http.Response

import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.core.JsonWriter._
import com.github.plokhotnyuk.jsoniter_scala.macros._
import scala.util.Try

object IncrementRoutes {

  implicit final class BodyExt(val body: Body) extends AnyVal {
    def asJson[A: JsonValueCodec](): ZIO[Any, Response, A] = {
      // TODO: This should fail fast with bad request, but returns 500???
      body.asArray.flatMap(arr => ZIO.attempt(readFromArray[A](arr))).mapError { err =>
        Response.badRequest(err.getMessage)
      }
    }
  }

  def make() = for {
    incrementService <- ZIO.service[IncrementService]
  } yield {
    Routes(
      Method.POST / "increment" -> handler { (req: Request) =>
        for {
          incr <- req.body.asJson[Model.Increment]()
          validated <-
            if (incr.value <= 0) { ZIO.fail(Response.badRequest("value must be postiive")) }
            else { ZIO.succeed(incr) }
          result <- incrementService.enqueue(incr)
          _ <- if (!result) Console.printLine(s"InboundQueue full!") else ZIO.succeed(())
        } yield {
          Response.status(Status.Accepted)
        }
      },
      // These are mostly for testing.
      Method.GET / "increment" -> handler { (req: Request) =>
        incrementService.all.map { incrs =>
          Response.json(writeToString(incrs))
        }
      },
      Method.GET / "increment" / string("key") -> handler { (key: String, req: Request) =>
        incrementService.lookup(key).map { incr =>
          Response.json(writeToString(incr))
        }
      },
      // Obviously should be not be exposed in production.
      Method.DELETE / "increment" -> handler { (req: Request) =>
        incrementService.deleteAll().map(_ => Response.ok)
      }
    )
  }.sandbox
}
