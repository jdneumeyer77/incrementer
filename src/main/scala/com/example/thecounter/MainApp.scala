package com.example.thecounter

import zio._
import zio.json._
import zio.http._
import zio.http.endpoint._
import zio.http.Response

object MainApp extends ZIOAppDefault {

  // Extension method on req.Body that deserializes json body as A failing fast
  // if it cannot deserialize it.
  implicit final class BodyExt(val body: Body) extends AnyVal {
    def asJson[A: JsonDecoder]() = {
      body.asString.flatMap(str => ZIO.fromEither(str.fromJson[A]).mapError(Response.badRequest))
    }
  }

  val routes = Routes(
    Method.POST / "increment" -> handler { (req: Request) =>
      for {
        incr <- req.body.asJson[Model.Increment]()
        // enque into Zio Buffer/Queue that's hooked into the stream

      } yield {
        println(s"$incr")
        Response.status(Status.Accepted)
      }
    }
  ).sandbox

  override val run = {
    Console.printLine("Application started at http://localhost:3333") *>
      Server.serve(routes.toHttpApp).provide(Server.defaultWithPort(3333))
  }
}
