# RESTHeart - Instant REST, GraphQL and WebSocket API for MongoDB.

## The low-code backend for modern Web and Mobile applications.

[![GitHub last commit](https://img.shields.io/github/last-commit/softinstigate/restheart)](https://github.com/SoftInstigate/restheart/commits/master)
[![Github stars](https://img.shields.io/github/stars/SoftInstigate/restheart?label=Github%20Stars)](https://github.com/SoftInstigate/restheart)
[![Maven Central](https://img.shields.io/maven-central/v/org.restheart/restheart.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22org.restheart%22%20AND%20a:%22restheart%22)
[![Docker Pulls](https://img.shields.io/docker/pulls/softinstigate/restheart.svg?maxAge=2592000)](https://hub.docker.com/r/softinstigate/restheart/)
[![Join the chat at https://gitter.im/SoftInstigate/restheart](https://badges.gitter.im/SoftInstigate/restheart.svg)](https://gitter.im/SoftInstigate/restheart?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

RESTHeart is a open source runtime which embeds [Undertow](https://undertow.io), a flexible performant web server written in Java, providing both blocking and non-blocking API’s based on NIO. 

RESTHeart connects automatically to [MongoDB](https://www.mongodb.com) or any compatible database (e.g. Percona Server for MongoDB, AWS DocumentDB and Azure Cosmos DB) exposing all database's features through a comprehensive REST API. 

Additionally, the [GraphQL](https://restheart.org/docs/graphql/) plugin works side by side with the already existing REST endpoints to get a managed, unified GraphQL API for modern applications. 

The embeded WebSocket server can then expose MongoDB's [Change Streams](https://docs.mongodb.com/manual/changeStreams/), which allow applications to access real-time data changes.

RESTHeart fully supports [GraalVM](https://restheart.org/docs/graalvm/), a new virtual machine from Oracle that offers a polyglot runtime environment and the ability to compile Java applications to __native binary images__.

RESTHeart __works out-of-the-box__ by merely installing and configuring it, but you can easily extend it by developing custom [plugins](https://restheart.org/docs/plugins/overview/).

For all these reasons, __RESTHeart is the perfect low code backend for most modern Web and Mobile apps__, designed to radically simplify development and deployment.

In summary, RESTHeart provides:

1. Polyglot Development Framework supporting Java, Kotlin, JavaScript and TypeScript.
2. Data persistence on MongoDB with REST, GraphQL and WebSocket APIs.
3. Authentication, authorization and user management for your applications.

__NOTE__: RESTHeart is also available as a [Docker image](https://hub.docker.com/r/softinstigate/restheart). It works perfectly in Kubernetes and AWS ECS clusters.

## Download

Download prebuilt packages from [releases](https://github.com/SoftInstigate/restheart/releases)

Find setup instructions at [Setup](https://restheart.org/docs/setup/) documentation page.

## Build from source

__NOTE__: Building RESTHeart 6+ requires JDK 16!

```bash
$ mvn clean package
```

You can then run it with (make sure to have mongod running):

```bash
$ java -jar core/target/restheart.jar core/etc/restheart.yml -e core/etc/default.properties
```

## Documentation

The full documentation is available [here](https://restheart.org/docs/).

You can also have a look at our [introductory video](https://youtu.be/9KroH-RvjS0) on Youtube:

[![Watch the video!](https://img.youtube.com/vi/9KroH-RvjS0/hqdefault.jpg)](https://youtu.be/9KroH-RvjS0)

## Sponsors

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
