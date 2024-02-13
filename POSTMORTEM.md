# Summary
My initial architecture worked well. It was surprisingly fast even without SQL update batching (see Quill section). Using the benchmarking tool wrk I saw anywhere from 30-99k requests/second with generally low latencies (both averages and P99; see benchmarking runs). After fixing batching problems with Quill, update lag was significantly less. That lag still depends on the number of unique keys and batch size. For instance if it's the ten Top Gun pilots in the basic benchmark then the batch size is always 10 and takes 5-20ms to update. Whereas if the key is a random number between 1 to 10,000 the batch size ranged from 4k-6k. Under heavy load it updated in 1.5s to 4s. Still within the requirements of 10s.

# Lessons Learned

## ZIO
ZIO is and continues to be hard to bootstrap. Particularly its Runtime/Dependency Injection, i.e. the R in ZIO[R,E,A]. It's tricky for a number of reasons. ZIO is lazy; basically ZIO(doSomething) is a blueprint to run later (a program running a program). This applies equally to its dependency (ZLayers), which can be counter-intuitive. It has gone through a couple evolutions on how to do this (using R vs constructor injection), and older ZIO libraries still use the older way like Quill. Forking fibers (stream, http-service) didn't allow you to provide those layers without creating multiple instances. Like if two queues are created then one will remain empty, but the other was being enqueued and growing. So it was confusing to track down. This also led to more refactoring to make things modular and testable.

I forgot about this and my previous experience with ZIO someone else had mostly set up the bootstrapping of the app. I could have chosen Akka/Pekko which would have been easier to set up, but may not have been as performant.


## Quill
I wasn't super familiar with Quill to begin with, but I didn't want to use some of the other libraries such as Slick or Doobie. I ran into some funky compile errors for a bit. Last minute change after adding some additional logging (execution time) I realized it was not actually batching. That led to increasing lag and batch execution times (10s+). After fixing it, it was dependent on the key and/or batch size. Under heavy loads and high cardinality keys it was between 1.5s to 3s. Small number (<50) of keys resulted in 5-20ms.


# Should we ship it?
I think there's still things to do. All the "extra" features for instance are critical for production. Plus some load balancing and autoscaling.

## Monitoring
There are ZIO libraries for metrics that can easily be added along the stream. Collection of the requests/second, enqueue time, average batch size, batch time spent in queue, and batch upsert execution time are some useful metrics that would be handy to monitor and tune the service.

## Load balancing
The service can easily be put behind a load balancer increasing its reliability through high-availability.


## Authorization
This is important for a public facing service. Something like an api-key would be important.


I'd probably implement it with an on-load bloom filter, i.e. check that it doesn't exist, with some way to refresh it(periodically, webhook, kafka compact log, etc.).

## Rate-limiting
Prevents abuse and overloading the service. I'd probably implement this with an api-key token-bucketing approach.

These two likely need to be externalized or sit in front of the service especially with load balancing. Maybe consistent hash load balancing? 

## Config
It should be configurable by a loadable file like the application.conf.

## Migrations
Actual SQL migrations management such as flyway.

## Tuning
General tuning around Postgres and the JVM.

Knowledge around how many keys there are if there is a finite amount can be used to tune in application settings. Plus with monitoring in place it can also be used to tune the application.

## Remove or move "delete /increment" + GET
Obviously dangerous. It needs to be walled off as an internal-only tool or removed. It was for testing purposes only when creating the benchmarks. (I was hoping to use the benchmarking tool for automated verification of results at the end of a run.)

## Graceful shutdown
Right now there isn't graceful shutdown. This is required for updating the software. Allowing the queues to drain while it shutdown so data is not lost.

## Follow 12 factor app design
https://12factor.net/

## Other changes
I don't care for the current table name. I went with the default quill name for the increment_result table.
