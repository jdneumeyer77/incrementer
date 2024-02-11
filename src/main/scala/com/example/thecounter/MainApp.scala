package com.example.thecounter

import zio._
import zio.http._
import zio.http.endpoint._
import zio.http.Response

import Utils._
import zio.stream.ZStream
import scala.collection.concurrent.TrieMap
import io.getquill.context.qzio._
import io.getquill._
import com.github.jasync.sql.db.postgresql.PostgreSQLConnection
import io.getquill.util.LoadConfig

object DBContext extends PostgresZioJAsyncContext(SnakeCase)

object MainApp extends ZIOAppDefault {

  val dbConfig = ZLayer.succeed(PostgresJAsyncContextConfig(LoadConfig.apply("testPostgresDB")))

  val connection =
    ZioJAsyncConnection.live[PostgreSQLConnection]

  val postgresIncrementRepo = connection ++ ZLayer.succeed(new PostgresIncrementRepo())

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
        Response.json(incrs.toString)
      }
    }
  ).sandbox

  override val run = {
    for {
      q <- InboundQueue.make(1024)
      queue = ZLayer.succeed(q)
      streamFiber <- IncrementStreamer
        .make(4096, 5.seconds, 16)
        .flatMap(x => x.runDrain)
        .provide(queue, postgresIncrementRepo, dbConfig)
        .fork
      service <- Server
        .serve(routes.toHttpApp)
        .provide(Server.defaultWithPort(3333), queue, postgresIncrementRepo, dbConfig)
        .fork
      _ <- Console.printLine("Application started at http://localhost:3333")
      _ <- streamFiber.zip(service).join
    } yield Exit.Success
  }
}
