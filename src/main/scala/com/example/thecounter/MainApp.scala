package com.example.thecounter

import zio._
import zio.json._
import zio.http._
import zio.http.endpoint._
import zio.http.Response

object MainApp extends ZIOAppDefault {

  implicit final class BodyExt(val body: Body) extends AnyVal {
    def asJson[A: JsonDecoder]() = {
      body.asString.flatMap(str => ZIO.fromEither(str.fromJson[A]).mapError(Response.badRequest))
    }
  }

  val routes = Routes(
    Method.POST / "increment" -> handler { (req: Request) =>
      for {
        incr <- req.body.asJson[Model.Increment]()
      } yield {
        println(s"$incr")
        Response.status(Status.Accepted)
      }
    }
  ).sandbox

  override val run = {
    Console.printLine("please visit http://localhost:8080") *>
      Server.serve(routes.toHttpApp).provide(Server.default)
  }
}
