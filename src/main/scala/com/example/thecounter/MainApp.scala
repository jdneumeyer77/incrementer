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
  // TODO: Move to config file...
  // Queue blocks upon reaching the size limit
  // and times out after so many seconds.
  // This applies backpressure on the client
  // but also may indicate the need for more nodes.
  val InboundQueueTimeout = 1.second
  val InboundQueueBufferSize = 8096

  // Collect up to StreamBatchSize or wait up to StreamCollectionTime
  // when collecting a batch.
  val StreamBatchSize = 8096
  val StreamCollectionTime = 5.seconds

  // This is number of concurrent batch submissions/connections to Postgres
  // TODO: This should align with the size of postgres connection pool.
  val StreamParrelBatch = 16

  val dbConfig = ZLayer.succeed(PostgresJAsyncContextConfig(LoadConfig.apply("testPostgresDB")))

  val connection =
    ZioJAsyncConnection.live[PostgreSQLConnection]

  val postgresIncrementRepo = connection ++ ZLayer.succeed(new PostgresIncrementRepo())

  val routes = Routes(
    Method.POST / "increment" -> handler { (req: Request) =>
      for {
        // TODO: validate not negative
        incr <- req.body.asJson[Model.Increment]()
        validated <-
          if (incr.value < 0) { ZIO.fail(Response.badRequest("value must be postiive")) }
          else { ZIO.succeed(incr) }
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
    },
    Method.GET / "increment" / string("key") -> handler { (key: String, req: Request) =>
      IncrementRepo.get(key).map { incrs =>
        Response.json(incrs.toString)
      }
    }
  ).sandbox

  override val run = {
    for {
      q <- InboundQueue.make(InboundQueueBufferSize, InboundQueueTimeout)
      queue = ZLayer.succeed(q)
      // Stream runs in the background
      streamFiber <- IncrementStreamer
        .make(StreamBatchSize, StreamCollectionTime, StreamParrelBatch)
        .flatMap(x => x.runDrain)
        .provide(queue, postgresIncrementRepo, dbConfig)
        .fork
      // concurrently with the webservice.
      service <- Server
        .serve(routes.toHttpApp)
        .provide(Server.defaultWithPort(3333), queue, postgresIncrementRepo, dbConfig)
        .fork
      _ <- Console.printLine("Application started at http://localhost:3333")
      _ <- streamFiber.zip(service).join
    } yield Exit.Success
  }
}
