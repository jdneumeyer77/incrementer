# Approach
The basic idea was to hand-off the post from "/increment" to a queue. The queue is then consumed
using a background stream processor, running side-by-side the http server, that allowed back-pressure while batching updates to Postgres in parallel.

# Technology
For this project I chose Scala and ZIO. ZIO a concurrent effects framework that uses lightweight
fibers, rather than threads, in Scala on the JVM. It includes ZStream, and ZIO (think promises & futures that run on fibers).

* zio-http - Simply a http framework to write endpoints.
* jsonitor-scala - one of the fastest json serialization libraries for the JVM.
* Quill + JASync - DB support. JASync was used for connection pool and natively async unlike JDBC. It is less mature however.

## Queue
Zio.Queue is a concurrent lock-free ring-buffer used heavily within ZIO/ZStream itself.

### Enqueue
After the post is deserialized and validated. It's enqueued. This queue should probably be bounded, i.e. a specific size, which is the default setup. This allows some back-pressure from all clients and a way to monitor performance. If it's slowing down we can easily autoscale on that information.

## Stream

High level overview of the stream that runs independently from http-service, i.e. ZIO forked fiber. ZStreams allow you to build composable pieces like the function batchIncrements. This allows them to easily tested from different sources then ZIO.Queue like a simple List.


```scala
ZStream.fromQueue(queue).
// Only keep values > 0
.filter(_.value > 0)
// collect up to buffer size or until max collection time.
.groupedWithin(bufferSize, maxCollection)
// group by key, add/consolidate values together. Addition is associative.
.map(chunk => chunk.groupMapReduce(_.key)(_.value)(_ + _))
.buffer(parallelBatches * 4)
// Process batches in parallel (again addition is associative; order doesn't matter).
.mapZIOParUnordered(parallelBatches) { batch =>
  submitBatchUpdate(batch).retryN(2)
}
```

### GroupedWithin
This stage does the initial batch collection of incoming requests. At high volume it'll fill quickly and pass along the next stage in the stream. The max collection time is important for when the service gets little-to-no traffic, i.e. a slow drip. So if 1 item comes in over 5 seconds that create a batch of one. As long as this collection time is less then 10s it'll meet the requirement that all updates need to be completed within 10s.

### GroupMapReduce
This consolidates the batch by key. Since addition is associative it doesn't matter in which order they're added together. This is key understanding for performance here and later in the stream.

### Parallel Batching
The batches are buffered to ensure Postgres does't get overloaded and to have the batches ready to go. The amount of parallelism should be less then or equal to the number of connections to Postgres (connection pooling). These batches are bulk upserted for efficiency. If the key already exists it's updated with the current timestamp and the old value incremented accordingly. Again since addition is associative it doesn't matter if two or parallel batches containing the same key are upserted at the "same" time. "Same" time since Postgres serializes incoming queries.

## Future Considerations
The queue piece is a lynchpin. If the system goes down while processing a high load those messages are lost. In order to not lose any messages one could enqueue onto a message broker like Kafka or Rabbit. It may come at a latency cost for updates. However, ZStream is portable and can use Kafka/Rabbit/etc. as a source instead of the ZIO.Queue.

This is an otherwise stateless service (beyond in-flight messages) and can easily be scaled horizontally.
