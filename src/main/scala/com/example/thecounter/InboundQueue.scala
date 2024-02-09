package com.example.thecounter

import zio._
import zio.stream.ZStream

object InboundQueue {
  def make(bufferSize: Int) = if (bufferSize == -1) {
    Queue.unbounded[Model.Increment].map(InboundQueue.apply _)
  } else {
    Queue.bounded[Model.Increment](bufferSize).map(InboundQueue.apply _)
  }

  def enqueue(item: Model.Increment) = ZIO.serviceWith[InboundQueue](_.enqueue(item)).flatten
  def attachToStream = ZIO.serviceWith[InboundQueue](_.attachToStream)
  def get = ZIO.serviceWith[InboundQueue](_.queue)
}

final case class InboundQueue(queue: Queue[Model.Increment]) {
  def enqueue(item: Model.Increment) = {
    for {
      res <- Console.printLine(s"enq'd item...") *>
        queue.offer(item)
      numOfQuedItems <- queue.size
      _ <- Console.printLine(s"num of items: ${numOfQuedItems}")
    } yield res
  }

  val attachToStream = ZStream.fromQueue(queue)
}
