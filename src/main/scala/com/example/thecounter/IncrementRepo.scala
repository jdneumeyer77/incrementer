package com.example.thecounter

import zio._
import scala.collection.concurrent.TrieMap
import io.getquill._
import io.getquill.PostgresJAsyncContext
import java.time.Instant

trait IncrementRepo {
  import IncrementRepo.IncrementResult

  def submitBatchUpdate(batch: Iterator[(String, Long)]): UIO[Boolean]

  def getAll(): Task[List[IncrementResult]]

  def get(key: String): Task[Option[IncrementResult]]
}

object IncrementRepo {
  import zio.json._
  case class IncrementResult(key: String, value: Long, createdAt: Instant, lastUpdatedAt: Instant)

  implicit val encoder: JsonEncoder[IncrementResult] = DeriveJsonEncoder.gen[IncrementResult]

  def submitBatchUpdate(batch: Iterator[(String, Long)]) =
    ZIO.serviceWith[IncrementRepo](_.submitBatchUpdate(batch)).flatten

  def getAll() = ZIO.serviceWith[IncrementRepo](_.getAll()).flatten

  def getOne[A: Tag](key: String) = ZIO.serviceWith[IncrementRepo](_.get(key)).flatten
}

// TODO: Move to tests
final case class TestIncrementRepo(val map: TrieMap[String, IncrementRepo.IncrementResult]) extends IncrementRepo {
  import IncrementRepo.IncrementResult

  println("Instance created")

  override def submitBatchUpdate(batch: Iterator[(String, Long)]): UIO[Boolean] = ZIO.succeed {
    batch.foreach { case (key, incoming_value) =>
      map.get(key) match {
        case None    => map.put(key, IncrementResult(key, incoming_value, Instant.now(), Instant.now()))
        case Some(v) => map.put(key, v.copy(value = v.value + incoming_value, lastUpdatedAt = Instant.now()))
      }
    }
    true
  }

  override def getAll(): Task[List[IncrementResult]] = ZIO.succeed(map.values.toList)

  override def get(key: String): Task[Option[IncrementResult]] = ZIO.succeed(map.get(key))

}

final class PostgresIncrementRepo(ctx: PostgresJAsyncContext[SnakeCase]) extends IncrementRepo {
  import ctx._
  import IncrementRepo.IncrementResult
  override def submitBatchUpdate(batch: Iterator[(String, Long)]): UIO[Boolean] = {
    val list = batch.map { case (key, value) => IncrementResult(key, value, Instant.now(), Instant.now()) }.toList

    val q = quote {
      lift(list).foreach { insert =>
        query[IncrementResult]
          .insertValue(insert)
          .onConflictUpdate(_.value, _.lastUpdatedAt)(
            (old, next) => old.value -> (old.value + next.value),
            (old, next) => old.lastUpdatedAt -> Instant.now()
          )

      }
    }

    Console.printLine(s"debug... ${translate(q)}") *>
      run(q)
  }

  override def getAll(): Task[List[IncrementRepo.IncrementResult]] = {
    val q = quote {
      query[IncrementResult]
    }

    Console.printLine("fetching all key/values") *>
      run(q)
  }

  override def get(key: String): Task[Option[IncrementResult]] = {
    val q = quote {
      query[IncrementRepo.IncrementResult].filter(incr => incr.key == key)
    }
    Console.printLine(s"fetch value for key: $key") *>
      run(q)
  }

}
