package com.example.thecounter

import zio._
import zio.json._
import zio.http._
import zio.http.endpoint._
import zio.http.Response

object Model {
  case class Increment(key: String, value: Long)

  implicit val decoder: JsonDecoder[Increment] = DeriveJsonDecoder.gen[Increment]
}
