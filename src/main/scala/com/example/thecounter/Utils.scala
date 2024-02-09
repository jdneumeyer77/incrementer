package com.example.thecounter

import zio.json._
import zio.http._
import zio._

object Utils {
  // Extension method on req.Body that deserializes json body as A failing fast
  // if it cannot deserialize it.
  implicit final class BodyExt(val body: Body) extends AnyVal {
    def asJson[A: JsonDecoder]() = {
      body.asString.flatMap(str => ZIO.fromEither(str.fromJson[A]).mapError(Response.badRequest))
    }
  }
}
