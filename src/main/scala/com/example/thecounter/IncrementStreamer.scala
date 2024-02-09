package com.example.thecounter

import zio._
import zio.stream._
object IncrementStreamer {
  def make[A: Tag](
      perBatchSize: Int,
      perBatchCollectionTime: Duration,
      updateBatchSize: Int,
      updateBatchTime: Duration
  ): ZIO[InboundQueue, Nothing, ZStream[IncrementRepo, Nothing, Boolean]] = {
    for {
      queueStream <- InboundQueue.attachToStream
      _ <- Console.printLine("Stream created.").ignore
    } yield queueStream
      .via(batchIncrements(perBatchSize, perBatchCollectionTime))
      .via(coordinateBatches(updateBatchSize, updateBatchTime))

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
  def coordinateBatches(
      bufferSize: Int = 16,
      maxCollectionDuration: Duration = 6.seconds
  ): ZPipeline[IncrementRepo, Nothing, Map[String, Long], Boolean] = {

    ZPipeline
      .apply[Map[String, Long]]
      .groupedWithin(bufferSize, maxCollectionDuration)
      .tap(in =>
        Console
          .printLine(s"collected a batch of ${in.length} within ${maxCollectionDuration.getSeconds()} seconds")
          .ignoreLogged
      )
      .flattenChunks
      .mapZIOParUnordered(bufferSize) { batch =>
        // TODO: failure handling/retry logic.
        IncrementRepo.submitBatchUpdate(batch.iterator)
      }
  }
}
