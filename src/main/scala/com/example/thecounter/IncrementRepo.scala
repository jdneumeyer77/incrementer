package com.example.thecounter

import zio._
import scala.collection.concurrent.TrieMap
import io.getquill._
import java.time.Instant
import java.util.Date
import io.getquill.context.qzio.ZioJAsyncConnection

import Model.IncrementResult

object IncrementRepo {
  def submitBatchUpdate(batch: Iterator[(String, Long)]) =
    ZIO.serviceWith[PostgresIncrementRepo](_.submitBatchUpdate(batch)).flatten

  def getAll() = ZIO.serviceWith[PostgresIncrementRepo](_.getAll()).flatten

  def get(key: String) = ZIO.serviceWith[PostgresIncrementRepo](_.get(key)).flatten

  def makeLive = for {
    connection <- ZIO.environment[ZioJAsyncConnection]
  } yield new PostgresIncrementRepo(connection)
}

// TODO: Move to tests
final case class TestIncrementRepo(val map: TrieMap[String, IncrementResult]) {

  def submitBatchUpdate(batch: Iterator[(String, Long)]): UIO[Boolean] = ZIO.succeed {
    batch.foreach { case (key, incoming_value) =>
      map.get(key) match {
        case None    => map.put(key, IncrementResult(key, incoming_value, Instant.now(), Instant.now()))
        case Some(v) => map.put(key, v.copy(value = v.value + incoming_value, lastUpdatedAt = Instant.now()))
      }
    }
    true
  }

  def getAll(): Task[List[IncrementResult]] = ZIO.succeed(map.values.toList)

  def get(key: String): Task[Option[IncrementResult]] = ZIO.succeed(map.get(key))

}

final class PostgresIncrementRepo(connection: ZEnvironment[ZioJAsyncConnection]) {
  import DBContext._

  implicit val instantEncoder: MappedEncoding[Instant, Date] =
    MappedEncoding[Instant, Date](i => Date.from(i))
  implicit val instantDecoder: MappedEncoding[Date, Instant] =
    MappedEncoding[Date, Instant](d => d.toInstant)

  def submitBatchUpdate(batch: Iterator[(String, Long)]): ZIO[Any, Throwable, Boolean] = {
    val list = batch.map { case (key, value) => IncrementResult(key, value, Instant.now(), Instant.now()) }.toList

    // batched queries:
    // INSERT INTO increment_result AS t (key,value,created_at,last_updated_at) VALUES (?, ?, ?, ?)
    // ON CONFLICT (key)
    // DO UPDATE SET
    // value = (old.value + EXCLUDED.value)
    // last_updated_at = now
    val q = quote {
      liftQuery(list).foreach { insert =>
        query[IncrementResult]
          .insertValue(insert)
          .onConflictUpdate(_.key)(
            (t, next) => t.value -> (t.value + next.value),
            (t, next) => t.lastUpdatedAt -> lift(Instant.now())
          )

      }
    }

    Console.printLine(s"updating by batch") *>
      run(q)
        .map(_.length > 0)
        .provideEnvironment(connection)
  }

  def getAll(): RIO[Any, Seq[IncrementResult]] = {
    // SELECT x.key, x.value, x.created_at AS createdAt, x.last_updated_at AS lastUpdatedAt
    //  FROM increment_result x
    val q = quote {
      query[IncrementResult]
    }

    Console.printLine("Fetching all key/values") *>
      run(q)
        .provideEnvironment(connection)
  }

  def get(key: String): RIO[Any, Option[IncrementResult]] = {
    // SELECT incr.key, incr.value, incr.created_at AS createdAt, incr.last_updated_at AS lastUpdatedAt
    // FROM increment_result incr
    // WHERE incr.key = ?
    // LIMIT 1
    val q = quote {
      query[IncrementResult].filter(incr => incr.key == lift(key)).take(1)
    }

    Console.printLine(s"Fetching by key($key)") *>
      run(q).map(_.headOption).provideEnvironment(connection)
  }

}
