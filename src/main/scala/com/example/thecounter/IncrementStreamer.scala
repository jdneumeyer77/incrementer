package com.example.thecounter

import zio._
import zio.stream._

object IncrementStreamer {
  // The streamed Map which can also be looked at like List[Increments]/List[(String,Int)].
  def batchIncrements(
      input: ZStream[Any, Nothing, Model.Increment],
      bufferSize: Int = 1024,
      maxCollectionDuration: Duration = 2.seconds
  ): ZStream[Any, Nothing, Map[String, Int]] = {
    input
      .filterNot(_.value == 0) // filter out no-ops where value == 0, but not necessary 0 + anything is anything.
      .groupedWithin(bufferSize, maxCollectionDuration)
      .tap(in =>
        Console
          .printLine(
            s"collected a batch of ${in.length}/${bufferSize} within ${maxCollectionDuration.getSeconds()} seconds"
          )
          .ignoreLogged
      )
      .map(chunk => chunk.groupMapReduce(_.key)(_.value)(_ + _)) // group by key, add/consolidate values together
  }

  // Since addition is associative in nature batches can be inserted in any order and parallel.
  // Sorry for the type gymastics to make this reusable/testable.
  def coordinateBatches[B: Tag](
      input: ZStream[Console, Nothing, Map[String, Int]],
      bufferSize: Int = 16,
      maxCollectionDuration: Duration = 6.seconds
  ): ZStream[Console & IncrementRepo[B], Nothing, Any] = {
    input
      .groupedWithin(bufferSize, maxCollectionDuration)
      .tap(in =>
        Console
          .printLine(s"collected a batch of ${in.length} within ${maxCollectionDuration.getSeconds()} seconds")
          .ignoreLogged
      )
      .mapZIOParUnordered(16) { group =>
        group.mapZIO(batch => IncrementRepo.prepareBatch(batch.iterator))
      }
      .flattenChunks
      .mapZIOParUnordered(bufferSize) { batch =>
        // TODO: retry logic.
        IncrementRepo.submitBatchUpdate(batch)
      }
      .flattenZIO
  }
}
