package com.example.thecounter

import zio._
import zio.stream._
object IncrementStreamer {
  def make[A: Tag](
      perBatchSize: Int,
      perBatchCollectionTime: Duration,
      updateBatchSize: Int,
      updateBatchTime: Duration
  ): ZIO[InboundQueue, Nothing, ZStream[IncrementRepo[A], Nothing, Boolean]] = {
    for {
      queueStream <- InboundQueue.attachToStream
      _ <- Console.printLine("Stream created.").ignore
    } yield queueStream
      .via(batchIncrements(perBatchSize, perBatchCollectionTime))
      .via(coordinateBatches(updateBatchSize, updateBatchTime))
      .flattenZIO

  }

  // The streamed Map which can also be looked at like List[Increments]/List[(String,Int)].
  def batchIncrements(
      bufferSize: Int = 1024,
      maxCollectionDuration: Duration = 2.seconds
  ) = {
    ZPipeline
      .apply[Model.Increment]
      .filter(_.value != 0) // filter out no-ops where value == 0, but not necessary 0 + anything is anything.
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
      bufferSize: Int = 16,
      maxCollectionDuration: Duration = 6.seconds
  ): ZPipeline[IncrementRepo[B], Nothing, Map[String, Int], UIO[Boolean]] = {

    ZPipeline
      .apply[Map[String, Int]]
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
        // TODO: failure handling/retry logic.
        IncrementRepo.submitBatchUpdate(batch)
      }
  }
}
