package com.example.thecounter

import zio._
import zio.json._
import zio.http._
import zio.http.endpoint._
import zio.http.Response

import Utils._
import zio.stream.ZStream
import scala.collection.concurrent.TrieMap

object MainApp extends ZIOAppDefault {

  val routes = Routes(
    Method.POST / "increment" -> handler { (req: Request) =>
      for {
        // TODO: validate not negative
        incr <- req.body.asJson[Model.Increment]()
        // TODO: what to do if couldn't enqueue?
        result <- InboundQueue.enqueue(incr)
        _ <- if (!result) Console.printLine(s"InboundQueue full!") else ZIO.succeed(())
      } yield {
        Response.status(Status.Accepted)
      }
    },
    Method.GET / "increment" -> handler { (req: Request) =>
      IncrementRepo.getAll().map { incrs =>
        println(incrs)
        Response.json(incrs.toJson)
      }
    }
  ).sandbox

  // val queue = ZLayer.succeed(runtime.run(InboundQueue.make(1024)))
  // val incrRepo = TestIncrementRepo.layer

  override val run = {
    for {
      q <- InboundQueue.make(1024)
      queue = ZLayer.succeed(q)
      incrRepoRaw = TestIncrementRepo(TrieMap())
      incrRepo = ZLayer.succeed(incrRepoRaw)
      streamFiber <- IncrementStreamer
        .make(4096, 2.seconds, 16, 5.seconds)
        .flatMap(x => x.foreach(Console.printLine(_)))
        .provide(queue, incrRepo)
        .fork
      service <- Server.serve(routes.toHttpApp).provide(Server.defaultWithPort(3333), queue, incrRepo).fork
      _ <- Console.printLine("Application started at http://localhost:3333")
      _ <- streamFiber.join
      _ <- service.join
    } yield Exit.Success
  }
}
