# RESTHeart - Low-Code API Server and Framework

✅ Develop your backend in minutes  
✅ Instant REST, GraphQL, and Websockets APIs for MongoDB   
✅ Declarative security with no code required  
✅ Powered by Java Virtual Threads  
✅ Based on the super-fast Undertow HTTP engine  
✅ Designed for Docker and Kubernetes Microservices 


[![GitHub last commit](https://img.shields.io/github/last-commit/softinstigate/restheart)](https://github.com/SoftInstigate/restheart/commits/master)
[![Build snapshot release](https://github.com/SoftInstigate/restheart/actions/workflows/branch.yml/badge.svg)](https://github.com/SoftInstigate/restheart/actions/workflows/branch.yml)
[![Github stars](https://img.shields.io/github/stars/SoftInstigate/restheart?label=Github%20Stars)](https://github.com/SoftInstigate/restheart)
[![Maven Central Version](https://img.shields.io/maven-central/v/org.restheart/restheart-core)](https://central.sonatype.com/namespace/org.restheart)
[![Docker Pulls](https://img.shields.io/docker/pulls/softinstigate/restheart.svg?maxAge=2592000)](https://hub.docker.com/r/softinstigate/restheart/)
[![Join the chat on Slack](https://img.shields.io/badge/chat-on%20slack-orange)](https://join.slack.com/t/restheart/shared_invite/zt-1olrhtoq8-5DdYLBWYDonFGEALhmgSXQ)

RESTHeart is solution for developing microservices, offering a robust and efficient framework built on top of [Undertow](https://undertow.io/), a high-performance web server written in Java providing both blocking and non-blocking API’s based on NIO. RESTHeart leverages [Java Virtual Threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html), which are lightweight threads designed to simplify the writing, maintaining, and debugging of high-throughput concurrent applications.

RESTHeart simplifies and accelerates the development process by exposing the full capabilities of [MongoDB](https://www.mongodb.com/) through **REST**, **GraphQL**, and **Websockets** APIs, all without requiring backend code. This significantly reduces development time. It supports MongoDB, Mongo Atlas, Percona Server, FerretDB, AWS DocumentDB, and Azure CosmosDB.

RESTHeart also provides comprehensive **authentication** and **authorization** services, supporting various security schemes. It enables the management of users, roles, and permissions directly within MongoDB collections, eliminating the need for backend code and further streamlining the development process.

## Documentation

The full documentation is available on [restheart.org/docs](https://restheart.org/docs/).

If you want to play with the APIs, you can start from:
- [The REST Data API Tutorial](https://restheart.org/docs/mongodb-rest/tutorial)
- [The GraphQL Data API Tutorial](https://restheart.org/docs/mongodb-graphql/tutorial)

## Installation

Refer to the documentation sections [Setup](https://restheart.org/docs/setup) or [Setup with Docker](https://restheart.org/docs/setup-with-docker).

One-liner to run RESTHeart with Docker:

```
curl https://raw.githubusercontent.com/SoftInstigate/restheart/master/docker-compose.yml --output docker-compose.yml && docker compose up --attach restheart
```

## Become a Sponsor

You can support the development of RESTHeart via the GitHub Sponsor program and receive public acknowledgment of your help.

[Go and see available sponsor tiers.](https://github.com/sponsors/SoftInstigate)

## Software Development Framework

RESTHeart offers a SDK to develop custom plugins. A plugin in RESTHeart is a software component that enhances the API by adding new features and capabilities. RESTHeart supports creating plugins in Java, Kotlin or any language supported by the JVM and JavaScript or any language supported by GraalVM.

There are four types of plugins in RESTHeart:

1. **Service**: These plugins add new web services to the API.
2. **Interceptor**: These plugins monitor and modify requests and responses at various stages of the request lifecycle.
3. **Initializer**: These plugins run initialization logic during system startup.
4. **Provider**: These plugins supply objects to other plugins using the @Inject annotation.

Additionally, security plugins can be developed to customize the security layer.

## Build from source

Build the thin JAR:

```bash
$ ./mvnw clean package
```

Build the fat JAR:

```bash
$ ./mvnw clean package -Pshade
```

Then you can check the build version:

```bash
$ java -jar core/target/restheart-core.jar -v
RESTHeart Version 8.0.7-SNAPSHOT Build-Time 2024-07-17
```
## Run

Start MongoDB on `localhost:27017` and then run RESTHeart with:

```bash
$ java -jar core/target/restheart.jar
```

## Execute the integration tests suite

To execute the integration test suite:

```bash
$ ./mvnw clean verify
```

The `verify` goal starts the RESTHeart process and a MongoDB Docker container before running the integration tests.

To avoid starting the MongoDB Docker container, specify the system property `-P-mongodb`.

The integration tests use the MongoDB connection string `mongodb://127.0.0.1` by default. To use a different connection string, specify the property `test-connection-string`.

The following example shows how to run the integration test suite against an instance of [FerretDB](https://www.ferretdb.io) running on `localhost`.

```bash
# run FerretDB
$ docker run -d --rm --name ferretdb -p 27017:27017 ghcr.io/ferretdb/all-in-one
# execute the integration tests
$ ./mvnw clean verify -DskipUTs -P-mongodb -Dtest-connection-string="mongodb://username:password@localhost/ferretdb?authMechanism=PLAIN" -Dkarate.options="--tags ~@requires-replica-set"
```

This example also specifies the karate options to skip tests tagged with `requires-replica-set` (FerretDB does not supports change stream and transactions) and `-DskipUTs` to skip the execution of unit tests.

## Automatic snapshot builds

Snapshot builds are available from [sonatype.org](https://s01.oss.sonatype.org/content/repositories/snapshots/org/restheart/restheart/)

Docker images of snapshots are also available:

```bash
$ docker pull softinstigate/restheart-snapshot:[commit-short-hash]
```

For commit short hash you need the first 7 digits of the hash, e.g.

```bash
$ git log

commit 2108ce033da8a8c0b65afea0b5b478337e44e464 (HEAD -> master, origin/master, origin/HEAD)
Author: Andrea Di Cesare <andrea@softinstigate.com>
Date:   Fri Oct 22 12:46:00 2021 +0200

    :bookmark: Bump to version 6.2.0-SNAPSHOT

...
```

The short hash is `2108ce0` and the docker pull command is therefore

```bash
$ docker pull softinstigate/restheart-snapshot:2108ce0
```

## Community Support

- Open a [issue on GitHub](https://github.com/SoftInstigate/restheart/issues/new) to report a specific problem.
- Ask technical questions on [Stackoverflow](https://stackoverflow.com/questions/ask?tags=restheart).
- Chat with other users on [Slack](https://join.slack.com/t/restheart/shared_invite/zt-1olrhtoq8-5DdYLBWYDonFGEALhmgSXQ).
- Book a [free 1-to-1 demo](https://calendly.com/restheart) with us.

### Sponsors

<table>
  <tbody>
    <tr>
      <td align="center" valign="middle">
        <a href="https://www.softinstigate.com" target="_blank">
          <img width="222px" src="https://www.softinstigate.com/images/logo.png">
        </a>
      </td>
    </tr>
  </tbody>
</table>

---

_Made with :heart: by [SoftInstigate](https://www.softinstigate.com). Follow us on [Twitter](https://twitter.com/softinstigate)_.
