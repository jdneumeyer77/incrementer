
# Pre
You'll need to install sbt (via brew or favorite package manager) and a JVM (JDK11 or 17 should work fine). Benchmarks need wrk installed.

# Running tests
```
sbt test
```

# Running service
## If using docker Postgres
```
docker build -t the-counter-db ./
docker run -d --name the-counter-db -p 5432:5432 the-counter-db
```
Note: I couldn't it to run my init.sql. So you may have to run it manually to create the table.

## Otherwise adjust 
`/src/main/resources/application.conf` and run `./migrations/init.sql`.

Finally kick off the service.
```
sbt run
```

Note: don't worry about the netty warnings. They're bugs in JAsync and don't actual affect the batch updates.

# Benchmarks
Make sure service is running. Pick a number of threads, connections, duration and a script.
```
wrk -t10 -c100 -d5m --latency -s ./benchmark/$script.lua http://localhost:3333
```