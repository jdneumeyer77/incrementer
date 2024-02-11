package com.example.thecounter

import zio._
import zio.stream._
import io.getquill.context.qzio.ZioJAsyncConnection

object IncrementStreamer {
  def make(
      perBatchSize: Int,
      perBatchCollectionTime: Duration,
      updateBatchSize: Int
  ): ZIO[InboundQueue, Nothing, ZStream[PostgresIncrementRepo with ZioJAsyncConnection, Throwable, Boolean]] = {
    for {
      queueStream <- InboundQueue.attachToStream
      _ <- Console.printLine("Stream created.").ignore
    } yield queueStream
      .via(batchIncrements(perBatchSize, perBatchCollectionTime))
      .buffer(updateBatchSize * 4)
      .mapZIOParUnordered(updateBatchSize) { batch =>
        // TODO: failure handling/retry logic.
        IncrementRepo.submitBatchUpdate(batch.iterator)
      }
  }

  // For tests only.
  def makeInMemory(
      perBatchSize: Int,
      perBatchCollectionTime: Duration,
      updateBatchSize: Int
  ): ZIO[InboundQueue, Nothing, ZStream[TestIncrementRepo, Nothing, Boolean]] = {
    for {
      queueStream <- InboundQueue.attachToStream
      _ <- Console.printLine("Stream created.").ignore
    } yield queueStream
      .via(batchIncrements(perBatchSize, perBatchCollectionTime))
      .buffer(updateBatchSize * 4)
      .mapZIOParUnordered(updateBatchSize) { batch =>
        // TODO: failure handling/retry logic.
        ZIO.serviceWith[TestIncrementRepo](_.submitBatchUpdate(batch.iterator)).flatten
      }
  }

  // The streamed Map which can also be looked at like List[Increment]/List[(String,Int)].
  def batchIncrements(
      bufferSize: Int,
      maxCollectionDuration: Duration
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

  // def coordinateBatchesPostgres(bufferSize: Int = 16, maxCollectionDuration: Duration = 6.seconds) =
  //   coordinateBatches(bufferSize, maxCollectionDuration)
  //     .mapZIOParUnordered(bufferSize) { batch =>
  //       // TODO: failure handling/retry logic.
  //       IncrementRepo.submitBatchUpdate(batch.iterator)
  //     }

  // def coordinateBatchesInMemory(bufferSize: Int = 16, maxCollectionDuration: Duration = 6.seconds) =
  //   coordinateBatches(bufferSize, maxCollectionDuration)
  //     .mapZIOParUnordered(bufferSize) { batch =>
  //       ZIO.serviceWith[TestIncrementRepo](_.submitBatchUpdate(batch.iterator)).flatten
  //     }

  // // Since addition is associative in nature batches can be inserted in any order and parallel.
  // def coordinateBatches(
  //     bufferSize: Int = 16,
  //     maxCollectionDuration: Duration = 6.seconds
  // ): ZPipeline[Any, Nothing, Map[String, Long], Map[String, Long]] = {
  //   ZPipeline
  //     .apply[Map[String, Long]]
  //     .groupedWithin(bufferSize, maxCollectionDuration)
  //     .tap(in =>
  //       Console
  //         .printLine(s"collected a batch of ${in.length} within ${maxCollectionDuration.getSeconds()} seconds")
  //         .ignoreLogged
  //     )
  //     .flattenChunks
  // }
}
