# RESTHeart - Ready to use backend for the modern Web.

## Comprehensive Java backend solution offering a low-code API development framework on top of MongoDB.

[![GitHub last commit](https://img.shields.io/github/last-commit/softinstigate/restheart)](https://github.com/SoftInstigate/restheart/commits/master)
[![Build snapshot release](https://github.com/SoftInstigate/restheart/actions/workflows/branch.yml/badge.svg)](https://github.com/SoftInstigate/restheart/actions/workflows/branch.yml)
[![Github stars](https://img.shields.io/github/stars/SoftInstigate/restheart?label=Github%20Stars)](https://github.com/SoftInstigate/restheart)
[![Maven Central Version](https://img.shields.io/maven-central/v/org.restheart/restheart)](https://central.sonatype.com/namespace/org.restheart)
[![Docker Pulls](https://img.shields.io/docker/pulls/softinstigate/restheart.svg?maxAge=2592000)](https://hub.docker.com/r/softinstigate/restheart/)
[![Join the chat on Slack](https://img.shields.io/badge/chat-on%20slack-orange)](https://join.slack.com/t/restheart/shared_invite/zt-1olrhtoq8-5DdYLBWYDonFGEALhmgSXQ)

RESTHeart is a modern Java backend solution enabling **REST**, **GraphQL**, and **WebSocket** APIs. It features a low-code development framework, ready-to-use security, and seamless **MongoDB** API integration.

## Key Features of RESTHeart

✅ **Low-Code Backend Foundation**: RESTHeart serves as a robust foundation for creating low-code backends, offering flexibility and power for on-premises deployment.

✅ **Instant Backend Launch**: Developers can swiftly connect RESTHeart to a MongoDB database, enabling the rapid launch of a fully functional backend without extensive coding.

✅ **Seamless Integration**: It integrates seamlessly with MongoDB, providing a hassle-free connection for data handling and management.

✅ **Customizable APIs**: RESTHeart empowers developers to create and customize APIs quickly, enabling efficient communication between applications and the backend.

✅ **Security and Compliance**: Built-in security features ensure robust protection for data and endpoints, meeting compliance standards for sensitive information handling.

✅ **Scalability and Performance**: Designed for scalability, RESTHeart maintains high performance even with increased data loads, ensuring smooth operations as applications grow.

✅ **On-Premises Control**: Offers the advantages of on-premises deployment, granting developers full control over the backend infrastructure while maintaining the ease of a low-code approach.

✅ **Concurrency**: Powered by the new [Java 21 Virtual Threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html) lightweight concurrency model.

✅ **Built on Undertow**: RESTHeart is built on top of the [Undertow](https://undertow.io/) web server.

> Undertow is a high-performance web server written in Java, known for its flexibility and efficiency. It provides both blocking and non-blocking APIs based on NIO (Non-blocking I/O), making it suitable for a wide range of use cases from lightweight HTTP handlers to full-fledged servlet containers. Notably, Undertow is the default web server for the WildFly application server and [replaces JBoss Web in JBoss EAP 7](https://docs.redhat.com/en/documentation/red_hat_jboss_enterprise_application_platform/7.3/html/development_guide/undertow). 

### Supported Databases

- [MongoDB](https://www.mongodb.com/)
- [MongoDB Atlas Cloud Database](https://www.mongodb.com/products/platform/atlas-database)
- [Percona Server for MongoDB](https://www.percona.com/mongodb/software/percona-server-for-mongodb)
- [FerretDB](https://www.ferretdb.com/) (*)
- [Amazon DocumentDB](https://docs.aws.amazon.com/documentdb/latest/developerguide/what-is.html) (*)
- [Microsoft Azure CosmosDB](https://learn.microsoft.com/en-us/azure/cosmos-db/mongodb/) (*)

> (*) Some of these databases might have partial compatibility with MongoDB APIs.

## Documentation

The full documentation is available on [restheart.org/docs](https://restheart.org/docs/).

To explore the APIs, start with:
- [The REST Data API Tutorial](https://restheart.org/docs/mongodb-rest/tutorial)
- [The GraphQL Data API Tutorial](https://restheart.org/docs/mongodb-graphql/tutorial)

## Quick Start with Docker Compose

1. Run both RESTHeart and MongoDB with Docker Compose using the following one-liner:

```sh
curl https://raw.githubusercontent.com/SoftInstigate/restheart/master/docker-compose.yml \
  --output docker-compose.yml \
  && docker compose up --pull=always --attach restheart
```

2. Open another terminal and call RESTHeart's ping service with [curl](https://curl.se/) or any other tool you like, to verify it's running:

```sh
curl -i localhost:8080/ping

HTTP/1.1 200 OK
Connection: keep-alive
Access-Control-Allow-Origin: *
X-Powered-By: restheart.org
Access-Control-Allow-Credentials: true
Access-Control-Expose-Headers: Location, ETag, Auth-Token, Auth-Token-Valid-Until, Auth-Token-Location, X-Powered-By
Content-Type: application/json
Content-Length: 112
Date: Fri, 21 Mar 2025 10:27:26 GMT

{"message": "Greetings from RESTHeart!", "client_ip": "127.0.0.1", "host": "localhost:8080", "version": "8.4.0"}
```

## Running Without Docker

1. Download a pre-built binary of RESTHeart and all plugins from the [Releases](https://github.com/SoftInstigate/restheart/releases) page.
2. Uncompress the `restheart.tar.gz` or `restheart.zip` archive to create a `restheart` folder:

```
.
├── COMM-LICENSE.txt
├── LICENSE.txt
├── plugins
│   ├── restheart-graphql.jar
│   ├── restheart-metrics.jar
│   ├── restheart-mongoclient-provider.jar
│   ├── restheart-mongodb.jar
│   ├── restheart-polyglot.jar
│   └── restheart-security.jar
└── restheart.jar
```

3. Start MongoDB on `localhost:27017` and then run RESTHeart with its default configuration:

```sh
java -jar restheart.jar
```

4. To connect to a MongoDB server running in a remote host, you can quickly [modify the configuration with the RHO env var](https://restheart.org/docs/configuration#modify-the-configuration-with-the-rho-env-var).

## Software Development Framework

RESTHeart offers an SDK for developing custom plugins. A plugin in RESTHeart enhances the API by adding new features and capabilities. RESTHeart supports creating plugins in Java, Kotlin, any language supported by the JVM, JavaScript, or any language supported by GraalVM.

Types of plugins in RESTHeart:

1. **Service**: Adds new web services to the API.
2. **Interceptor**: Monitors and modifies requests and responses at various stages of the request lifecycle.
3. **Initializer**: Runs initialization logic during system startup.
4. **Provider**: Supplies objects to other plugins using the `@Inject` annotation.

Additionally, security plugins can be developed to customize the security layer.

## Building from Source

Build the thin JAR:

```sh
./mvnw clean package
```

Check the build version:

```sh
java -jar core/target/restheart.jar -v
RESTHeart Version 8.0.7-SNAPSHOT Build-Time 2024-07-17
```

To build a fat JAR, add the `shade` Maven profile:

```sh
./mvnw clean package -Pshade
```

## Running Integration Tests

To execute the integration test suite:

```sh
./mvnw clean verify
```

The `verify` goal starts the RESTHeart process and a MongoDB Docker container before running the integration tests.

To avoid starting the MongoDB Docker container, specify the system property `-P-mongodb`.

The integration tests use the MongoDB connection string `mongodb://127.0.0.1` by default. To use a different connection string, specify the property `test-connection-string`.

Example of running the integration test suite against an instance of [FerretDB](https://www.ferretdb.io) on `localhost`:

```sh
# Run FerretDB
docker run -d --rm --name ferretdb -p 27017:27017 ghcr.io/ferretdb/all-in-one
# Execute the integration tests
./mvnw clean verify -DskipUTs -P-mongodb -Dtest-connection-string="mongodb://username:password@localhost/ferretdb?authMechanism=PLAIN" -Dkarate.options="--tags ~@requires-replica-set"
```

This example skips tests tagged with `requires-replica-set` (FerretDB does not support change stream and transactions) and uses `-DskipUTs` to skip the execution of unit tests.

## Automatic Snapshot Builds

Snapshot builds are available from [sonatype.org](https://s01.oss.sonatype.org/content/repositories/snapshots/org/restheart/restheart/).

Docker images of snapshots are also available:

```sh
docker pull softinstigate/restheart-snapshot:[commit-short-hash]
```

To find the commit short hash, use:

```sh
git log
```

The short hash is the first 7 digits of the commit hash. For example, for commit `2108ce033da8a8c0b65afea0b5b478337e44e464`, the short hash is `2108ce0` and the Docker pull command is:

```

bash
docker pull softinstigate/restheart-snapshot:2108ce0
```

## Community Support

- Open an [issue on GitHub](https://github.com/SoftInstigate/restheart/issues/new) to report a specific problem.
- Ask technical questions on [Stack Overflow](https://stackoverflow.com/questions/ask?tags=restheart).
- Chat with other users on [Slack](https://join.slack.com/t/restheart/shared_invite/zt-1olrhtoq8-5DdYLBWYDonFGEALhmgSXQ).
- Book a [free 1-to-1 demo](https://calendly.com/restheart) with us.

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

### Become a Sponsor

You can support the development of RESTHeart via the GitHub Sponsor program and receive public acknowledgment of your help.

[See available sponsor tiers.](https://github.com/sponsors/SoftInstigate)

---

_Made with :heart: by [SoftInstigate](https://www.softinstigate.com). Follow us on [Twitter](https://twitter.com/softinstigate)_.
