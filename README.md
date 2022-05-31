# RESTHeart - Ready to use backend for the modern Web.

##  REST, GraphQL and WebSocket APIs for MongoDB.

[![GitHub last commit](https://img.shields.io/github/last-commit/softinstigate/restheart)](https://github.com/SoftInstigate/restheart/commits/master)
[![Github stars](https://img.shields.io/github/stars/SoftInstigate/restheart?label=Github%20Stars)](https://github.com/SoftInstigate/restheart)
[![Maven Central](https://img.shields.io/maven-central/v/org.restheart/restheart.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22org.restheart%22%20AND%20a:%22restheart%22)
[![Docker Pulls](https://img.shields.io/docker/pulls/softinstigate/restheart.svg?maxAge=2592000)](https://hub.docker.com/r/softinstigate/restheart/)
[![Join the chat at https://gitter.im/SoftInstigate/restheart](https://badges.gitter.im/SoftInstigate/restheart.svg)](https://gitter.im/SoftInstigate/restheart?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

RESTHeart provides:

1. **Out of the box data persistence on [MongoDB](https://www.mongodb.com)** and any compatible database, like [AWS DocumentDB](https://aws.amazon.com/documentdb/?nc1=h_ls) or [Azure Cosmos DB](https://docs.microsoft.com/en-us/azure/cosmos-db/mongodb/mongodb-introduction), with REST, GraphQL and WebSocket APIs.
2. **Declarative authentication, authorization and user management** for your applications.
3. **Polyglot development framework** supporting Java, Kotlin, JavaScript and TypeScript.

> **Note**: With RESTHeart you can access 100% of MongoDB capabilities with plain HTTP calls, no driver programming is needed!

RESTHeart embeds [Undertow](https://undertow.io), a flexible and performant web server written in Java, providing both blocking and non-blocking HTTP API’s based on [NIO](https://en.wikipedia.org/wiki/Non-blocking_I/O_(Java)). Undertow is the underlying HTTP server of [RedHat's Wildfly](https://www.wildfly.org).

RESTHeart works out-of-the-box by merely installing and configuring it. It is particularly suitable to run as a [Docker container](https://hub.docker.com/r/softinstigate/restheart), so it works perfectly in **Kubernetes** and **AWS ECS** clusters.

At startup, RESTHeart **connects automatically to the configured MongoDB database** and exposes all of its capabilities via a complete REST API. See the below example session, that uses [HTTPie](https://httpie.io) for REST calls:

![RESTHeart in action](https://github.com/SoftInstigate/restheart-website/raw/aa2a9be0fc13c5d70f3ad4ed9e337875525394bc/images/restheart.gif)

RESTHeart also supports [GraalVM](https://restheart.org/docs/graalvm/), a new Java Virtual Machine from Oracle that offers a **polyglot runtime environment** and the ability to compile Java applications to **native binary images**.

Its architecture is based on [plugins](https://restheart.org/docs/plugins/overview/) and exposes an internal API that allows to implement additional custom services in Java, Kotlin, JavaScript or TypeScript.

**Developers can save at least 80% of backend code** to manage security and serve content to Mobile Apps, Angular, React, Vue or other SPA frameworks.

![RESTHeart use cases](https://restheart.org/images/clients.png)

## Download

Download prebuilt packages from [releases](https://github.com/SoftInstigate/restheart/releases)

Find setup instructions at [Setup](https://restheart.org/docs/setup/) documentation page.

## Build from source

> **Note**: Building RESTHeart from scratch requires at least Java 17 and maven 3.6.

```bash
$ ./mvnw clean package
```

You can then run it with (make sure to have mongod running):

```bash
$ java -jar core/target/restheart.jar core/etc/restheart.yml -e core/etc/default.properties
```

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

## Customization

To [extend the default behavior](https://restheart.org/docs/plugins/overview/) you can implement the following Java interfaces:

- __Service__ - to develop custom business logic and web services.
- __Interceptor__ - to snoop and modify requests and responses "on the fly", at different stages of the HTTP lifecycle.
- __Initializer__ - to execute any kind of initialization logic at system startup time.

The [GraphQL](https://restheart.org/docs/graphql/) default plugin works side by side with the already existing REST endpoints to get a managed, unified GraphQL API for modern applications.

The embedded WebSocket server can expose MongoDB's [Change Streams](https://docs.mongodb.com/manual/changeStreams/), which allow applications to access real-time data changes.

For all these reasons, __RESTHeart is the perfect "low code", self-contained backend for modern Web and Mobile apps__, designed to radically simplify development and deployment.

## Documentation

The full documentation is available [here](https://restheart.org/docs/).

You can also have a look at our [introductory video](https://youtu.be/9KroH-RvjS0) on Youtube:

[![Watch the video!](https://img.youtube.com/vi/9KroH-RvjS0/hqdefault.jpg)](https://youtu.be/9KroH-RvjS0)


## Become a Sponsor to RESTHeart

You can support the development of RESTHeart via GitHub Sponsor program and receive public acknowledgment of your help.

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

_Made with :heart: by [SoftInstigate](http://www.softinstigate.com/). Follow us on [Twitter](https://twitter.com/softinstigate)_.
