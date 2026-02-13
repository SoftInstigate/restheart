# Build RESTHeart from Source

Build the thin JAR:

```sh
./mvnw clean package
```

Check the build version:

```sh
java -jar core/target/restheart.jar -v
RESTHeart Version 8.0.7-SNAPSHOT Build-Time 2024-07-17
```

To build the fat JAR, add the `shade` Maven profile:

```sh
./mvnw clean package -P shade
```

## Running Integration Tests

To execute the integration test suite:

```sh
./mvnw clean verify
```

The `verify` goal starts the RESTHeart process and a MongoDB Docker container before running the integration tests.

To avoid starting the MongoDB Docker container, specify the system property `-P-mongodb`.

The integration tests use the MongoDB connection string `mongodb://127.0.0.1` by default. To use a different connection string, specify the property `test-connection-string`.

## Test with FerretDB

[FerretDB](https://www.ferretdb.com/) allows you to use MongoDB drivers seamlessly with __PostgreSQL__ as the database backend. Use all tools, drivers, UIs, and the same query language and stay open-source. 

Example of running the integration test suite against an instance of FerretDB on `localhost`:

```sh
# Run FerretDB
docker run -d --rm --name ferretdb -p 27017:27017 ghcr.io/ferretdb/all-in-one
# Execute the integration tests
./mvnw clean verify -DskipUTs -P-mongodb -Dtest-connection-string="mongodb://username:password@localhost/ferretdb?authMechanism=PLAIN" -Dkarate.options="--tags ~@requires-replica-set"
```

This example skips tests tagged with `requires-replica-set` (FerretDB so far does not support change stream and transactions) and uses `-DskipUTs` to skip the execution of unit tests.

## Automatic SNAPSHOT Builds

Snapshot Maven artifacts are available from [sonatype.org](https://s01.oss.sonatype.org/content/repositories/snapshots/org/restheart/restheart/) repository.

Docker images of snapshots are also available from [Docker Hub](https://hub.docker.com/r/softinstigate/restheart-snapshot).

```sh
docker pull softinstigate/restheart-snapshot
```

You can even pull a [specific commit](https://hub.docker.com/r/softinstigate/restheart-snapshot/tags).