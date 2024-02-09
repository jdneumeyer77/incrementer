package com.example.thecounter

import zio._
import scala.collection.concurrent.TrieMap
// import io.getquill._
// import io.getquill.jdbczio.Quill

trait IncrementRepo[A] {
  def prepareBatch(batch: Iterator[(String, Int)]): A

  def submitBatchUpdate(batch: A): UIO[Boolean]

  def getAll(): Task[Map[String, Int]]

  def get(key: String): Task[Option[Int]]
}

object IncrementRepo {
  def prepareBatch[A: Tag](batch: Iterator[(String, Int)]) = ZIO.serviceWith[IncrementRepo[A]](_.prepareBatch(batch))

  def submitBatchUpdate[A: Tag](batch: A) = ZIO.serviceWith[IncrementRepo[A]](_.submitBatchUpdate(batch))

  def getAll[A: Tag]() = ZIO.serviceWith[IncrementRepo[A]](_.getAll()).flatten

  def getOne[A: Tag](key: String) = ZIO.serviceWith[IncrementRepo[A]](_.get(key)).flatten
}

// TODO: Move to tests
object TestIncrementRepo {
  type TestBatch = List[(String, Int)]
  val layer = ZLayer.succeed[IncrementRepo[TestBatch]](TestIncrementRepo(TrieMap()))
}

final case class TestIncrementRepo(val map: TrieMap[String, Int]) extends IncrementRepo[TestIncrementRepo.TestBatch] {
  import TestIncrementRepo.TestBatch

  println("Instance created")
  override def prepareBatch(batch: Iterator[(String, Int)]): TestBatch = batch.toList

  override def submitBatchUpdate(batch: TestBatch): UIO[Boolean] = ZIO.succeed {
    batch.foreach { case (k, v) =>
      map.get(k) match {
        case None        => map.put(k, v)
        case Some(value) => map.put(k, v + value)
      }
    }
    true
  }

  override def getAll(): Task[Map[String, Int]] = ZIO.succeed(map.toMap)

  override def get(key: String): Task[Option[Int]] = ZIO.succeed(map.get(key))

}

final class PostgresIncrementRepo() {}
