package com.example.thecounter

import zio._
import zio.stream._
import io.getquill.context.qzio.ZioJAsyncConnection

object IncrementStreamer {
  def make(
      queue: Queue[Model.Increment],
      perBatchSize: Int,
      perBatchCollectionTime: Duration,
      updateBatchSize: Int
  ): ZStream[PostgresIncrementRepo, Throwable, Boolean] = {
    ZStream
      .fromQueue(queue)
      .via(batchIncrements(perBatchSize, perBatchCollectionTime))
      // buffer batches if necessary.
      .buffer(updateBatchSize * 4)
      // order doesn't matter since addition is associative,
      // i.e. 1 + 2 + 3 = 6 no matter if (1 + 2) + 3 or (3 + 1) + 2.
      .mapZIOParUnordered(updateBatchSize) { batch =>
        // TODO: failure handling/retry logic.
        IncrementRepo.submitBatchUpdate(batch.iterator).retryN(2)
      }
  }

  // For tests only.
  def makeInMemory(
      queue: Queue[Model.Increment],
      perBatchSize: Int,
      perBatchCollectionTime: Duration,
      updateBatchSize: Int
  ): ZStream[TestIncrementRepo, Nothing, Boolean] = {
    ZStream
      .fromQueue(queue)
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
      // Keep everything above zero.
      .filter(_.value > 0)
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
      .tap(in => Console.printLine(s"consolidated batch size: ${in.size}").ignore)
  }
}
