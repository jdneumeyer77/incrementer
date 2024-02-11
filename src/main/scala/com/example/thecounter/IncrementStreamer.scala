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
      // buffer batches if necessary.
      .buffer(updateBatchSize * 4)
      // order doesn't matter since addition is associative,
      // i.e. 1 + 2 + 3 = 6 no matter if (1 + 2) + 3 or (3 + 1) + 2.
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
      maxCollection: Duration
  ) = {
    ZPipeline
      .apply[Model.Increment]
      // filter out no-ops where value == 0, but not necessary 0 + anything is anything.
      .filter(_.value != 0)
      // collect up to buffer size or until max collection duration.
      .groupedWithin(bufferSize, maxCollection)
      .tap(in =>
        Console
          .printLine(
            s"collected a batch of ${in.length}/${bufferSize} within ${maxCollection.getSeconds()} seconds"
          )
          .ignoreLogged
      )
      // group by key, add/consolidate values together. Addition is associative.
      .map(chunk => chunk.groupMapReduce(_.key)(_.value)(_ + _))
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
