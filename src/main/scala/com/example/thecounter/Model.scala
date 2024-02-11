package com.example.thecounter

import zio._
import zio.http._
import zio.http.endpoint._
import zio.http.Response

import com.github.plokhotnyuk.jsoniter_scala.macros._
import com.github.plokhotnyuk.jsoniter_scala.core._

object Model {
  case class Increment(key: String, value: Long)

  implicit val decoder: JsonValueCodec[Increment] = JsonCodecMaker.make
}
