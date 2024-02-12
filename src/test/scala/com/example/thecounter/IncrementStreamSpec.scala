package com.example.thecounter

import zio.test._
import zio.stream._
import zio.ZIO
import zio.ZLayer
import com.github.plokhotnyuk.jsoniter_scala.core._
import zio._

import Model._
import IncrementStreamer._

object IncrementStreamSpec extends ZIOSpecDefault {

  def spec = suite("suite for IncrementRoutes")(
    test("basic batch collection test") {

      val increments = List("1" -> 1, "2" -> 3, "1" -> 3, "3" -> 0, "1" -> 5).map { case (k, v) => Increment(k, v) }

      val stream = ZStream
        .fromIterator(increments.iterator)
        .via(batchIncrements(10, 33.milli))
        .map { batch =>
          assertTrue(batch.size == 2) &&
          assertTrue(!batch.contains("3")) &&
          assertTrue(batch("1") == 9) &&
          assertTrue(batch("2") == 3)
        }
        .run(ZSink.foldLeft(assertTrue(true)) { case (acc, next) => acc && next })

      runtime.run(stream)
    },
    test("test spillover") {

      val increments = List("1" -> 1, "2" -> 3, "1" -> 3, "3" -> 0, "1" -> 5).map { case (k, v) => Increment(k, v) }

      val stream = ZStream
        .fromIterator(increments.iterator)
        .via(batchIncrements(1, 33.milli))
        .run(ZSink.count.map(count => assertTrue(increments.length.toLong - 1 == count)))

      runtime.run(stream)
    }
  )

}
