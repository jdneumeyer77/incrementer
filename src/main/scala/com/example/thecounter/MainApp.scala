package com.example.thecounter

import zio._
import zio.http._
import zio.http.endpoint._
import zio.http.Response

import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.core.JsonWriter._
import com.github.plokhotnyuk.jsoniter_scala.macros._
import zio.stream.ZStream
import scala.collection.concurrent.TrieMap
import io.getquill.context.qzio._
import io.getquill._
import com.github.jasync.sql.db.postgresql.PostgreSQLConnection
import io.getquill.util.LoadConfig

object DBContext extends PostgresZioJAsyncContext(SnakeCase)

object MainApp extends ZIOAppDefault {
  // TODO: Move to application.conf
  // Queue blocks upon reaching the size limit
  // and times out after so many seconds.
  // This applies backpressure on the client
  // but also may indicate the need for more nodes.
  val InboundQueueTimeout = 1.second
  val InboundQueueBufferSize = 8096 * 2

  // Collect up to StreamBatchSize or wait up to StreamCollectionTime
  // when collecting a batch.
  val StreamBatchSize = 8096 * 8
  val StreamCollectionTime = 5.seconds

  // This is number of concurrent batch submissions/connections to Postgres
  // TODO: This should align with the size of postgres connection pool.
  val StreamParrelBatch = 16

  val dbConfig = ZLayer.succeed(PostgresJAsyncContextConfig(LoadConfig.apply("counterdb")))
  val connection =
    dbConfig >>>
      ZioJAsyncConnection.live[PostgreSQLConnection]
  val postgresIncrementRepo = (connection >>> ZLayer.fromZIO(IncrementRepo.makeLive)).memoize

  def makeQueue = if (InboundQueueBufferSize == -1) {
    Queue.unbounded[Model.Increment]
  } else {
    Queue.bounded[Model.Increment](InboundQueueBufferSize)
  }

  override val run = {
    for {
      q <- makeQueue
      repo <- postgresIncrementRepo
      // Stream runs in the background
      streamFiber <- IncrementStreamer
        .make(q, StreamBatchSize, StreamCollectionTime, StreamParrelBatch)
        .runDrain
        .provide(repo)
        .fork
      _ <- Console.printLine("Stream has started!")
      service <- IncrementService.makeLive(q, InboundQueueTimeout).provide(repo)
      routes <- IncrementRoutes.make().provide(service)
      // concurrently with the webservice.
      service <- Server
        .serve(routes.toHttpApp)
        .provide(Server.defaultWithPort(3333))
        .fork
      _ <- Console.printLine("Service started at http://localhost:3333")
      _ <- streamFiber.zip(service).join
    } yield Exit.Success
  }
}
