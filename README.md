# RESTHeart - Open Source Low-code API development framework

_Featuring ready-to-go Security and MongoDB API_

[![GitHub last commit](https://img.shields.io/github/last-commit/softinstigate/restheart)](https://github.com/SoftInstigate/restheart/commits/master)
[![Github stars](https://img.shields.io/github/stars/SoftInstigate/restheart?label=Github%20Stars)](https://github.com/SoftInstigate/restheart)
[![Maven Central](https://img.shields.io/maven-central/v/org.restheart/restheart.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:org.restheart)
[![Docker Pulls](https://img.shields.io/docker/pulls/softinstigate/restheart.svg?maxAge=2592000)](https://hub.docker.com/r/softinstigate/restheart/)
[![Join the chat on Slack](https://img.shields.io/badge/chat-on%20slack-orange)](https://join.slack.com/t/restheart/shared_invite/zt-1olrhtoq8-5DdYLBWYDonFGEALhmgSXQ)

> **Note**: Schedule a [free 1-to-1 demo](https://calendly.com/restheart) and feel free to ask us anything!

RESTHeart is a framework for building HTTP microservices that aims to empower developers with intuitive APIs out of the box. It is built for developers, with a focus on simplicity, speed and ease of use.

RESTHeart is classified as a low-code framework because it provides pre-configured APIs like authentication/authorization and data management through MongoDB integration. This approach reduces the amount of code developers need to write, streamlining the development process and making it easier and faster to build cloud-native HTTP microservices

The **MongoDB plugin** exposes the full database capabilities through REST, GraphQL and Websockets with no backend code required. This cuts development time significantly. Supports MongoDB, Mongo Atlas, FerretDB, AWS DocumentDB, and Azure CosmosDB.

RESTHeart offers comprehensive authentication and authorization services, supporting various security schemes. It enables the management of users and permissions directly in MongoDB collections, eliminating the need for backend code. This streamlined approach significantly reduces development time.

## Documentation

The full documentation is available on [restheart.org/docs](https://restheart.org/docs/).

## Installation

Refer to the documentation sections [Setup](https://restheart.org/docs/setup) or [Setup with Docker](https://restheart.org/docs/setup-with-docker).

One-liner to run RESTHeart with Docker:

```
$ curl https://raw.githubusercontent.com/SoftInstigate/restheart/master/docker-compose.yml --output docker-compose.yml && docker compose up --attach restheart
```

## The Framework

A plugin is a software component that extends the functionality of RESTHeart, allowing you to add additional features and capabilities to the basic API. RESTHeart supports the development of plugins in Java, Kotlin, and JavaScript.

There are four types of plugins in RESTHeart:

- Service: These plugins extend the API by adding web services.
- Interceptor: These plugins snoop and modify requests and responses at different stages of the request lifecycle.
- Initializer: These plugins execute initialization logic at system startup time.
- Provider: These plugins provide objects to other plugins via the @Inject annotation.

Additionally, it is also possible to develop security plugins to customize the security layer.

## Build from source

> **Note**: Building RESTHeart from scratch requires at least Java 17 and maven 3.6.

```bash
$ ./mvnw clean package
```

You can then run it with (make sure to have `mongod` running on `localhost:27017`):

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

## Become a Sponsor

You can support the development of RESTHeart via the GitHub Sponsor program and receive public acknowledgment of your help.

[Go and see available sponsor tiers.](https://github.com/sponsors/SoftInstigate)

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
