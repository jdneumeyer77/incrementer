package com.example.thecounter

import zio.http._
import zio._
import com.github.plokhotnyuk.jsoniter_scala.macros._
import com.github.plokhotnyuk.jsoniter_scala.core._
import scala.util.Try

object Utils {
  // Extension method on req.Body that deserializes json body as A failing fast
  // if it cannot deserialize it.
  implicit final class BodyExt(val body: Body) extends AnyVal {
    def asJson[A: JsonValueCodec]() = {
      body.asArray.flatMap(arr =>
        ZIO.fromTry(Try(readFromArray[A](arr))).mapError(err => Response.badRequest(err.getMessage))
      )
    }
  }
}
