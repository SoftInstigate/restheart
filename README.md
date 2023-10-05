# RESTHeart - Ready to use backend for the modern Web.

##  REST, GraphQL and WebSocket APIs for MongoDB.

[![GitHub last commit](https://img.shields.io/github/last-commit/softinstigate/restheart)](https://github.com/SoftInstigate/restheart/commits/master)
[![Github stars](https://img.shields.io/github/stars/SoftInstigate/restheart?label=Github%20Stars)](https://github.com/SoftInstigate/restheart)
[![Maven Central](https://img.shields.io/maven-central/v/org.restheart/restheart.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22org.restheart%22%20AND%20a:%22restheart%22)
[![Docker Pulls](https://img.shields.io/docker/pulls/softinstigate/restheart.svg?maxAge=2592000)](https://hub.docker.com/r/softinstigate/restheart/)
[![Join the chat on Slack](https://img.shields.io/badge/chat-on%20slack-orange)](https://join.slack.com/t/restheart/shared_invite/zt-1olrhtoq8-5DdYLBWYDonFGEALhmgSXQ)

RESTHeart offers the following features:

1. **Out-of-the-box data persistence** on [MongoDB](https://www.mongodb.com) as well as **compatible databases** such as [AWS DocumentDB](https://aws.amazon.com/documentdb/?nc1=h_ls) or [Azure Cosmos DB](https://docs.microsoft.com/en-us/azure/cosmos-db/mongodb/mongodb-introduction), all accessible through REST, GraphQL, and WebSocket APIs.

2. **Declarative authentication, authorization, and user management** for your applications.

3. A **polyglot development framework** that supports Java, Kotlin, JavaScript, and TypeScript.

With RESTHeart, you can harness **100% of MongoDB's capabilities using simple HTTP calls**, eliminating the need for programming!

> **Note**: Schedule a [free 1-to-1 demo](https://calendly.com/restheart) and feel free to ask us anything!

During startup, RESTHeart **automatically establishes a connection to the configured MongoDB database** and provides comprehensive API access. Refer to the example session below, which employs [HTTPie](https://httpie.io) for making REST calls:

![See RESTHeart in action](https://github.com/SoftInstigate/restheart-website/raw/aa2a9be0fc13c5d70f3ad4ed9e337875525394bc/images/restheart.gif)

**Developers can reduce backend code by at least 80%** when managing security and delivering content to mobile apps, Angular, React, Vue, or other single-page application (SPA) frameworks.

![RESTHeart use cases](https://restheart.org/images/clients.png)

## Supported databases

RESTHeart works with any MongoDB compatible database. The actual features supported depend on the level of compatibility of the database with the official MongoDB Java driver.

**Note**: RESTHeart releases are tested in continuous integration with official MongoDB distributions only (from MongoDB 4.2 to 7.0).

<a href="https://mongodb.com"><img src="https://upload.wikimedia.org/wikipedia/commons/9/93/MongoDB_Logo.svg" width="200px"></a>
<a href="https://www.ferretdb.io"><img src="https://dbdb.io/media/logos/ferretdb.svg" width="200px"></a>
<a href="https://developer.azurecosmosdb.com"><img src="https://devblogs.microsoft.com/wp-content/uploads/sites/31/2019/04/cosmosdb_fi.png" width="100px"></a>
<a href="https://aws.amazon.com/documentdb/features/"><img src="https://d2908q01vomqb2.cloudfront.net/887309d048beef83ad3eabf2a79a64a389ab1c9f/2020/02/04/DocumentDB.png" width="160px"></a>
<a href="https://www.percona.com/software/mongodb/percona-server-for-mongodb"><img src="https://www.percona.com/blog/wp-content/uploads/2015/12/psmdb-logo.png" width="200px"></a>

- MongoDB Community, Enterprise and Atlas Cloud are 100% supported in all their functionalities.
- Azure Cosmos DB and Amazon DocumentDB offer partial support for the MongoDB API, but most common RESTHeart features work as expected.
- FerretDB offers partial support for the MongoDB API on top of PostgreSQL, but its level of compatibility with MongoDB is growing daily. FerretDB plans to support more relational databases in the future.
- Percona Server for MongoDB is, in general, fully compatible with MongoDB, so RESTHeart usually works perfectly with it.

## Advanced Capabilities

RESTHeart incorporates [Undertow](https://undertow.io), a versatile and high-performance web server crafted in Java. Undertow furnishes both blocking and non-blocking HTTP APIs built upon [NIO](https://en.wikipedia.org/wiki/Non-blocking_I/O_(Java)). Notably, Undertow serves as the foundational HTTP server for [RedHat's Wildfly](https://www.wildfly.org).

Setting up RESTHeart is effortless, as it operates seamlessly after installation and configuration. It is exceptionally well-suited for deployment within a [Docker container](https://hub.docker.com/r/softinstigate/restheart), making it a natural fit for deployment in **Kubernetes** and **AWS ECS** clusters.

RESTHeart additionally provides support for [GraalVM](https://restheart.org/docs/graalvm/), an innovative Java Virtual Machine developed by Oracle. GraalVM introduces a **polyglot runtime environment** and the ability to compile Java applications into **native binary images**.

The internal architecture relies on a system of [plugins](https://restheart.org/docs/plugins/overview/), exposing an API that empowers the implementation of custom services in Java, Kotlin, JavaScript, or TypeScript.

To [enhance the default functionality](https://restheart.org/docs/plugins/overview/), you can implement the following Java interfaces:

- __Service__ - Expands the API by introducing new web services.
- __Interceptor__ - Observes and modifies requests and responses at various stages of the request lifecycle.
- __Initializer__ - Executes initialization logic during startup.
- __Providers__ - Implements the Dependency Injection mechanism to supply objects to other plugins using the @Inject annotation.

The default [GraphQL](https://restheart.org/docs/graphql/) plugin seamlessly coexists with the existing REST endpoints, yielding a managed and unified GraphQL API tailored for modern applications.

Furthermore, the embedded WebSocket server can expose MongoDB's [Change Streams](https://docs.mongodb.com/manual/changeStreams/), permitting applications to access real-time data alterations.

Given these considerations, __RESTHeart emerges as the ideal "low code," self-contained backend solution for contemporary web and mobile applications__. Its design is centered on streamlining development and deployment processes.
## Download

Download prebuilt packages from [releases](https://github.com/SoftInstigate/restheart/releases)

Find setup instructions at [Setup](https://restheart.org/docs/setup/) documentation page.

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

## Documentation

The full documentation is available [here](https://restheart.org/docs/).

You can also have a look at our [introductory video](https://youtu.be/9KroH-RvjS0) on Youtube:

[![Watch the video!](https://img.youtube.com/vi/9KroH-RvjS0/hqdefault.jpg)](https://youtu.be/9KroH-RvjS0)

## Contacts and Support

- Open a [issue on GitHub](https://github.com/SoftInstigate/restheart/issues/new) to report a specific problem.
- Ask technical questions on [Stackoverflow](https://stackoverflow.com/questions/ask?tags=restheart).
- Chat with other users on [Slack](https://join.slack.com/t/restheart/shared_invite/zt-1olrhtoq8-5DdYLBWYDonFGEALhmgSXQ).
- Book a [free 1-to-1 demo](https://calendly.com/restheart) with us.
- Write us an [e-mail](mailto:ask@restheart.org?subject=RESTHeart).

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
