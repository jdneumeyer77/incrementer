package com.example.thecounter

import zio._
import zio.http._

object IncrementService {
  def makeLive(queue: Queue[Model.Increment], timeout: Duration) = for {
    repo <- ZIO.service[PostgresIncrementRepo]
  } yield ZLayer.succeed(new IncrementServiceProd(queue, repo, timeout))
}

trait IncrementService {
  def enqueue(incr: Model.Increment): ZIO[Any, Response, Boolean]
  def all(): ZIO[Any, Response, Seq[Model.IncrementResult]]
  def lookup(key: String): ZIO[Any, Response, Option[Model.IncrementResult]]
}

final class IncrementServiceProd(inbound: Queue[Model.Increment], repo: PostgresIncrementRepo, timeout: Duration)
    extends IncrementService {

  override def enqueue(item: Model.Increment): ZIO[Any, Response, Boolean] =
    inbound.offer(item).timeoutFail(Response.gatewayTimeout("timed out waiting to enqueue item."))(timeout)

  override def all(): ZIO[Any, Response, Seq[Model.IncrementResult]] =
    repo.getAll().mapError(throwable => Response.internalServerError(throwable.getMessage))

  override def lookup(key: String): ZIO[Any, Response, Option[Model.IncrementResult]] = {
    repo.get(key).mapError(throwable => Response.internalServerError(throwable.getMessage))
  }

}
