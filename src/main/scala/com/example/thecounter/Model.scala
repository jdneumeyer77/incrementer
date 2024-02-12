package com.example.thecounter

import zio._
import zio.http._
import zio.http.endpoint._
import zio.http.Response

import com.github.plokhotnyuk.jsoniter_scala.macros._
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker._
import com.github.plokhotnyuk.jsoniter_scala.core._
import java.time.Instant

object Model {
  case class Increment(key: String, value: Long)

  implicit val incrCodec: JsonValueCodec[Increment] = JsonCodecMaker.make

  case class IncrementResult(key: String, value: Long, created_at: Instant, lastUpdatedAt: Instant)

  implicit val incrResultCodec: JsonValueCodec[IncrementResult] = JsonCodecMaker.make
  implicit val incrResultSeqCodec: JsonValueCodec[Seq[IncrementResult]] = JsonCodecMaker.make
  implicit val incrResultOptCodec: JsonValueCodec[Option[IncrementResult]] = JsonCodecMaker.make
}
