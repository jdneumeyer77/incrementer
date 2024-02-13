package com.example.thecounter

import zio._
import scala.collection.concurrent.TrieMap
import io.getquill._
import java.time.Instant
import java.util.Date
import io.getquill.context.qzio.ZioJAsyncConnection

import Model.IncrementResult

object IncrementRepo {
  def submitBatchUpdate(batch: Model.Batch) =
    ZIO.serviceWith[PostgresIncrementRepo](_.submitBatchUpdate(batch)).flatten

  def getAll() = ZIO.serviceWith[PostgresIncrementRepo](_.getAll()).flatten

  def get(key: String) = ZIO.serviceWith[PostgresIncrementRepo](_.get(key)).flatten

  def makeLive = for {
    connection <- ZIO.environment[ZioJAsyncConnection]
  } yield new PostgresIncrementRepo(connection)
}

final class PostgresIncrementRepo(connection: ZEnvironment[ZioJAsyncConnection]) {
  import DBContext._

  implicit val instantEncoder: MappedEncoding[Instant, Date] =
    MappedEncoding[Instant, Date](i => Date.from(i))
  implicit val instantDecoder: MappedEncoding[Date, Instant] =
    MappedEncoding[Date, Instant](d => d.toInstant)

  def submitBatchUpdate(batch: Model.Batch): Task[Boolean] = {
    val list = batch.values.map { case (key, value) =>
      IncrementResult(key, value, batch.createdAt, batch.createdAt)
    }

    val now = Instant.now()
    // batched queries:
    // INSERT INTO increment_result AS t (key,value,created_at,last_updated_at) VALUES (?, ?, ?, ?)
    // ON CONFLICT (key)
    // DO UPDATE SET
    // value = (old.value + EXCLUDED.value)
    // last_updated_at = ?
    val q = quote {
      liftQuery(list).foreach { insert =>
        query[IncrementResult]
          .insertValue(insert)
          .onConflictUpdate(_.key)(
            (t, next) => t.value -> (t.value + next.value),
            (t, next) => t.lastUpdatedAt -> lift(now)
          )

      }
    }

    // 4096 is the batch insertion size. 32k is the limit;
    // however, query substitutions i.e., ? in the above there's 5
    // 32/5 = ~6.5. Stay under that limit. If the collection (batch)
    // is bigger it automatics splits it across multiple batches of 4096
    // https://zio.dev/zio-quill/writing-queries/#batch-optimization
    Console.printLine(s"updating by batch: ${batch.id}") *>
      run(q, 4096)
        .flatMap { in =>
          val diffnow = Instant.now()
          val timediff = diffnow.toEpochMilli - batch.createdAt.toEpochMilli
          val executiondiff = diffnow.toEpochMilli - now.toEpochMilli
          Console
            .printLine(s"Batch ${batch.id} took ${timediff}ms since added. Execution: ${executiondiff}ms")
            .ignore *>
            ZIO.succeed(in)
        }
        .map(_.length > 0)
        .provideEnvironment(connection)
  }

  def getAll(): Task[Seq[IncrementResult]] = {
    // SELECT x.key, x.value, x.created_at AS createdAt, x.last_updated_at AS lastUpdatedAt
    //  FROM increment_result x
    val q = quote {
      query[IncrementResult]
    }

    Console.printLine("Fetching all key/values") *>
      run(q)
        .provideEnvironment(connection)
  }

  def get(key: String): Task[Option[IncrementResult]] = {
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

  def deleteAll(): Task[Unit] = {
    val q = quote {
      query[IncrementResult].delete
    }

    Console.printLine("deleting all keys") *>
      run(q).map(_ => ()).provideEnvironment(connection)
  }

}
