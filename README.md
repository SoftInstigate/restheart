# RESTHeart

RESTHeart is a __Java__ backend framework that facilitates the rapid development of __REST__, __GraphQL__, and __WebSocket__ APIs, leveraging __MongoDB__ for data storage.

[![GitHub last commit](https://img.shields.io/github/last-commit/softinstigate/restheart)](https://github.com/SoftInstigate/restheart/commits/master)
[![Build snapshot release](https://github.com/SoftInstigate/restheart/actions/workflows/branch.yml/badge.svg)](https://github.com/SoftInstigate/restheart/actions/workflows/branch.yml)
[![Github stars](https://img.shields.io/github/stars/SoftInstigate/restheart?label=Github%20Stars)](https://github.com/SoftInstigate/restheart)
[![Maven Central Version](https://img.shields.io/maven-central/v/org.restheart/restheart)](https://central.sonatype.com/namespace/org.restheart)
[![javadoc](https://javadoc.io/badge2/org.restheart/restheart/javadoc.svg)](https://javadoc.io/doc/org.restheart/restheart)
[![Docker Pulls](https://img.shields.io/docker/pulls/softinstigate/restheart.svg?maxAge=2592000)](https://hub.docker.com/r/softinstigate/restheart/)
[![Join the chat on Slack](https://img.shields.io/badge/chat-on%20slack-orange)](https://join.slack.com/t/restheart/shared_invite/zt-1olrhtoq8-5DdYLBWYDonFGEALhmgSXQ)

### Who Benefits from RESTHeart?

RESTHeart is designed for:
- __Developers__ seeking a low-code solution to build scalable, secure, and high-performance APIs.
- __Teams__ aiming to reduce development time and complexity when working with MongoDB.
- __Businesses__ looking for a reliable and efficient way to expose data through REST or GraphQL APIs.

---

## Key Features

✅ **Low-Code Development**: Create APIs with minimal coding effort.  
✅ **Seamless MongoDB Integration**: Instantly expose your MongoDB collections via REST, GraphQL, and WebSocket APIs.  
✅ **Built-in Security**: Provides declarative authentication and authorization mechanisms.  
✅ **High Performance**: Utilizes Java 21 Virtual Threads for efficient concurrency.  
✅ **Customizable**: Flexible architecture that adapts to your specific needs.  

## Compatible Databases

- [MongoDB](https://www.mongodb.com/)
- [MongoDB Atlas Cloud Database](https://www.mongodb.com/products/platform/atlas-database)
- [Percona Server for MongoDB](https://www.percona.com/mongodb/software/percona-server-for-mongodb)
- [FerretDB](https://www.ferretdb.com/) (*)
- [Amazon DocumentDB](https://docs.aws.amazon.com/documentdb/latest/developerguide/what-is.html) (*)
- [Microsoft Azure CosmosDB](https://learn.microsoft.com/en-us/azure/cosmos-db/mongodb/) (*)

> (*) Might be partially compatibile with MongoDB.

## Default Configuration

- The [default configuration](https://restheart.org/docs/default-configuration) connects to a local MongoDB instance using the connection string `mongodb://127.0.0.1`.  
- To prevent unintentional exposure of all your data, the API root (`"/"`) is, by default, mapped to a database named `"restheart"`. If you want to expose existing databases and collections through the API, you must [update your configuration](https://restheart.org/docs/configuration) by replacing the default admin password and explicitly mapping your own databases to API endpoints.

## Ask Sophia AI!

Before digging the [full documentation](https://restheart.org/docs/), our AI assistant [Sophia](https://sophia.restheart.com/) can quickly help you with any question about RESTHeart. 

## Quick Start with Docker Compose

1. Run both RESTHeart and MongoDB with Docker Compose using the following one-liner:

```sh
$ curl https://raw.githubusercontent.com/SoftInstigate/restheart/master/docker-compose.yml \
  --output docker-compose.yml \
  && docker compose up --pull=always --attach restheart
```

1. Call RESTHeart's ping service with [curl](https://curl.se/) to verify it's running:

```sh
$ curl -i localhost:8080/ping

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

ℹ️ Look at [Setup with Docker](https://restheart.org/docs/setup-with-docker) for more.

## Running Without Docker

1. Download a pre-built binary of RESTHeart from the [Releases](https://github.com/SoftInstigate/restheart/releases) page.
2. Uncompress the `restheart.tar.gz` or `restheart.zip` archive
3. `cd restheart`
4. Start MongoDB on `localhost:27017` and then run RESTHeart with its default configuration:
   
```sh
$ java -jar restheart.jar
```

### Connect to a remote MongoDB

To connect to a MongoDB server running in a remote host, you can quickly [modify the configuration with the RHO env var](https://restheart.org/docs/configuration#modify-the-configuration-with-the-rho-env-var).

Example:

```sh
$ RHO='/mclient/connection-string->"mongodb://127.0.0.1"' java -jar restheart.jar
```

## Native executables

Or CI/CD pipeline builds native executables for main MacOS, Linux and Windows. Have a look at the [Native Executables](/native-executables.md) document for more information.

ℹ️ The native executables use the same [configuration approach](https://restheart.org/docs/configuration) as the Java version.

## Build from Source

To build from source or to run the integration tests suite look at [BUILD.md](BUILD.md).

## Documentation

The full documentation is available on [restheart.org/docs](https://restheart.org/docs/).

To explore the APIs, start with:
- [The REST Data API Tutorial](https://restheart.org/docs/mongodb-rest/tutorial)
- [The GraphQL Data API Tutorial](https://restheart.org/docs/mongodb-graphql/tutorial)

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

If you enjoy using this open-source project, you can support the development of RESTHeart via the [GitHub Sponsor program](https://github.com/sponsors/SoftInstigate).

---

_Made with :heart: by [SoftInstigate](https://www.softinstigate.com). Follow us on [Twitter](https://twitter.com/softinstigate)_.
