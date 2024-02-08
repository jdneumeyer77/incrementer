package com.example.thecounter

import zio._
import scala.collection.concurrent.TrieMap

trait IncrementRepo[A] {
  def prepareBatch(batch: Iterator[(String, Int)]): A

  def submitBatchUpdate(batch: A): UIO[Boolean]

  def getAll(): Task[Map[String, Int]]

  def get(key: String): Task[Option[Int]]
}

object IncrementRepo {
  def prepareBatch[A: Tag](batch: Iterator[(String, Int)]) = ZIO.serviceWith[IncrementRepo[A]](_.prepareBatch(batch))

  def submitBatchUpdate[A: Tag](batch: A) = ZIO.serviceWith[IncrementRepo[A]](_.submitBatchUpdate(batch))

  def getAll[A: Tag]() = ZIO.serviceWith[IncrementRepo[A]](_.getAll())

  def getOne[A: Tag](key: String) = ZIO.serviceWith[IncrementRepo[A]](_.get(key))
}

// TODO: Move to tests
object TestIncrementRepo {
  val layer = ZLayer.succeed(TestIncrementRepo(TrieMap()))
}

final case class TestIncrementRepo(val map: TrieMap[String, Int]) extends IncrementRepo[List[(String, Int)]] {

  override def prepareBatch(batch: Iterator[(String, Int)]): List[(String, Int)] = batch.toList

  override def submitBatchUpdate(batch: List[(String, Int)]): UIO[Boolean] = {
    batch.foreach { case (k, v) =>
      map.get(k) match {
        case None        => map.update(k, v)
        case Some(value) => map.update(k, v + value)
      }
    }
    ZIO.succeed(true)
  }

  override def getAll(): Task[Map[String, Int]] = ZIO.succeed(map.toMap)

  override def get(key: String): Task[Option[Int]] = ZIO.succeed(map.get(key))

}
