package com.example.thecounter

import zio._
import zio.stream.ZStream
import zio.http.Response

object InboundQueue {
  def make(bufferSize: Int, timeout: Duration) = if (bufferSize == -1) {
    Queue.unbounded[Model.Increment].map(InboundQueue.apply(_, timeout))
  } else {
    Queue.bounded[Model.Increment](bufferSize).map(InboundQueue.apply(_, timeout))
  }

  def enqueue(item: Model.Increment) = ZIO.serviceWith[InboundQueue](_.enqueue(item)).flatten
  def attachToStream = ZIO.serviceWith[InboundQueue](_.attachToStream)
  def get = ZIO.serviceWith[InboundQueue](_.queue)
}

final case class InboundQueue(queue: Queue[Model.Increment], timeout: Duration) {
  def enqueue(item: Model.Increment) = {
    queue.offer(item).timeoutFail(Response.gatewayTimeout("timed out waiting to enqueue item."))(timeout)
  }

  val attachToStream = ZStream.fromQueue(queue)
}
