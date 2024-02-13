# Summary
My initial architecture worked well. It was surprisingly fast even without batching (see Quill section). Using the benchmarking tool wrk I saw anywhere from 30-99k request/second with low latencies (see benchmarking runs). After fixing batching problem with Quill update lag was siginficantly less. That lag still depends on the number of unique keys and batch size. For instance if it's the ten Topgun pilots in the basic benchmark then the batch size is always 10 and takes 5-20ms to update. Whereas if the key is a random number between 1 to 10,000 the batch size ranged from 4k-6k. Under heavy load it udpated in 1.5s to 4s. Still within the requirement, but it took significantly longing.

# Lessons Learned

## ZIO
ZIO is and continues to be hard to bootstrap. Particularly its Runtime/Depedency Injection, i.e. the R in ZIO[R,E,A]. It's tricky for a number of reasons. ZIO is lazy; basically ZIO(doSomething) is a blueprint to run the later. This applies equaly to its dependency (ZLayers), which can be counter-intuitive. It has gone through a couple evolutions on how to do this (using R vs constructor injection), and older ZIO libraries still use the older way like Quill. Forking fibers (stream, http-service) didn't allow you to provide those layers without creating multiple instances. Like if two queues are created then one will remain empty, but the other was being enqued and growing. So it was confusing to track down. This also lead to more refactoring to make things more testable.

I forgot about this and my previous experience with ZIO someone else had mostly setup the bootstraping. I could have choosen Akka/Pekko which would have been easier to setup, but may not have been as performant.

## Quill
I wasn't super familiar with Quill to begin with, but I didn't want to use some of the other libraries such as Slick or Doobie. I ran into some funky compile errors for a bit. Last minute after adding some additional logging realized it was not actually batching. That lead to increasing lag and batch execution times (10s+). After fixing 

# Should we ship it?
I think there's still things to do. All the "extra" features for instance are critical for production. Plus some load balancing and autoscaling.

## Monitoring
There are zio libraries for metrics that can easily be added to the stream. Collection of the requests/second, enqueue time, average batch size, batch time spent in queue, and batch upsert execution time some useful metrics that would handy to monitor and tune the service.

## Load balancing
The service can easily be put behind a load balancing increasing its reliability through high-availability.

## Authorization
This is important for a public facing service. Something like api-key would be imporant.

I'd probably implement it with an on-load bloom filter, i.e. check that it doesn't exist, with some way to refresh it(periodically, webhook, kafka compact log).

## Rate-limiting
Prevents abuse and overloading the service. I'd probably implement this with api-key token-bucketing approach.

These two likely need to be externalized or sit in front of the service especially with load balancing.
