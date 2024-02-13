package com.example.thecounter

import zio._
import zio.http._
import zio.http.endpoint._
import zio.http.Response

import com.github.plokhotnyuk.jsoniter_scala.macros._
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker._
import com.github.plokhotnyuk.jsoniter_scala.core._
import java.time.Instant
import java.util.UUID

object Model {
  case class Batch(values: Map[String, Long], createdAt: Instant = Instant.now, id: UUID = UUID.randomUUID())

  case class Increment(key: String, value: Long)

  implicit val incrCodec: JsonValueCodec[Increment] = JsonCodecMaker.make

  case class IncrementResult(key: String, value: Long, createdAt: Instant, lastUpdatedAt: Instant)

  implicit val incrResultCodec: JsonValueCodec[IncrementResult] = JsonCodecMaker.make
  // Not sure why these don't autogen.
  implicit val incrResultSeqCodec: JsonValueCodec[Seq[IncrementResult]] = JsonCodecMaker.make
  implicit val incrResultOptCodec: JsonValueCodec[Option[IncrementResult]] = JsonCodecMaker.make
}
