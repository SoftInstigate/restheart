# RESTHeart

**The backend framework that instantly turns MongoDB into REST, GraphQL, and WebSocket APIs.**

[![GitHub last commit](https://img.shields.io/github/last-commit/softinstigate/restheart)](https://github.com/SoftInstigate/restheart/commits/master)
[![Build snapshot release](https://github.com/SoftInstigate/restheart/actions/workflows/branch.yml/badge.svg)](https://github.com/SoftInstigate/restheart/actions/workflows/branch.yml)
[![Github stars](https://img.shields.io/github/stars/SoftInstigate/restheart?label=Github%20Stars)](https://github.com/SoftInstigate/restheart)
[![Maven Central Version](https://img.shields.io/maven-central/v/org.restheart/restheart)](https://central.sonatype.com/namespace/org.restheart)
[![javadoc](https://javadoc.io/badge2/org.restheart/restheart-commons/javadoc.svg)](https://javadoc.io/doc/org.restheart/restheart-commons)
[![Docker Pulls](https://img.shields.io/docker/pulls/softinstigate/restheart.svg?maxAge=2592000)](https://hub.docker.com/r/softinstigate/restheart/)
[![Join the chat on Slack](https://img.shields.io/badge/chat-on%20slack-orange)](https://join.slack.com/t/restheart/shared_invite/zt-1olrhtoq8-5DdYLBWYDonFGEALhmgSXQ)

---

## What is RESTHeart?

RESTHeart is a Java backend framework that instantly exposes your MongoDB database through secure REST, GraphQL, and WebSocket APIs — **no backend code required**.

Build production-ready APIs in minutes, then extend them with custom business logic using a powerful plugin system.

**Perfect for:**
- Building APIs without writing boilerplate CRUD code
- Rapid prototyping and MVPs
- Extending MongoDB with custom Java services
- Microservices architectures

**Key capabilities:**
- 🚀 **Zero-code APIs** — Connect to MongoDB and start querying via REST/GraphQL
- 🔐 **Built-in security** — Authentication, authorization, and role-based access control
- ⚡ **High performance** — Java Virtual Threads for efficient concurrency
- 🔌 **Extensible** — Plugin architecture for custom services, interceptors, and validators
- 📦 **Production-ready** — Docker support, metrics, monitoring, and native executables

## Quick Start

Get RESTHeart and MongoDB running in 30 seconds:


```sh
curl https://raw.githubusercontent.com/SoftInstigate/restheart/master/docker-compose.yml --output docker-compose.yml && docker compose up --pull=always --attach restheart
```

**Test it:**

```sh
curl localhost:8080/ping
```

That's it! You now have a running RESTHeart instance connected to MongoDB.


👉 **Next steps:**

- [REST API Tutorial](https://restheart.org/docs/mongodb-rest/tutorial) — Learn CRUD operations
- [GraphQL API Tutorial](https://restheart.org/docs/mongodb-graphql/tutorial) — Query with GraphQL
- [Configuration Guide](https://restheart.org/docs/configuration) — Customize your setup
- [Plugin Development](https://restheart.org/docs/plugins/overview) — Extend with custom logic

💡 **Need help?** Ask [Sophia](https://sophia.restheart.com/), our AI assistant, or join us on [Slack](https://join.slack.com/t/restheart/shared_invite/zt-1olrhtoq8-5DdYLBWYDonFGEALhmgSXQ).

## Running Without Docker

**Download and run:**

```sh
# 1. Download from releases
curl -L https://github.com/SoftInstigate/restheart/releases/latest/download/restheart.tar.gz -o restheart.tar.gz

# 2. Extract
tar -xzf restheart.tar.gz && cd restheart

# 3. Run (requires MongoDB on localhost:27017)
java -jar restheart.jar
```

**Connect to remote MongoDB:**

```sh
RHO='/mclient/connection-string->"mongodb://your-mongo-host:27017"' java -jar restheart.jar
```

**Run in standalone mode (no MongoDB required):**

```sh
java -jar restheart.jar -s
```

This mode is perfect for testing custom plugins, services, or running RESTHeart as a pure Java API server.

## Compatible Databases

Works with MongoDB and MongoDB-compatible databases:

- [MongoDB](https://www.mongodb.com/) — Full support
- [MongoDB Atlas](https://www.mongodb.com/products/platform/atlas-database) — Cloud database
- [Percona Server for MongoDB](https://www.percona.com/mongodb/software/percona-server-for-mongodb) — Full support
- [FerretDB](https://www.ferretdb.com/) — Partial compatibility
- [Amazon DocumentDB](https://docs.aws.amazon.com/documentdb/latest/developerguide/what-is.html) — Partial compatibility
- [Azure CosmosDB](https://learn.microsoft.com/en-us/azure/cosmos-db/mongodb/) — Partial compatibility

## Advanced Topics

**Native Executables:**
Pre-built native executables for macOS, Linux, and Windows are available. See [Native Executables](native-executables.md) for details.

**Build from Source:**
Want to contribute or customize? Check out [BUILD.md](BUILD.md) for build instructions and testing.

**Full Documentation:**
Complete guides, API references, and examples at [restheart.org/docs](https://restheart.org/docs/).

## Get Help

- 🤖 [Ask Sophia](https://sophia.restheart.com/) — AI assistant for quick answers
- 💬 [Join Slack](https://join.slack.com/t/restheart/shared_invite/zt-1olrhtoq8-5DdYLBWYDonFGEALhmgSXQ) — Chat with the community
- 🐛 [GitHub Issues](https://github.com/SoftInstigate/restheart/issues/new) — Report bugs or request features
- 💡 [Stack Overflow](https://stackoverflow.com/questions/ask?tags=restheart) — Technical questions
- 📅 [Book a demo](https://calendly.com/restheart) — Free 1-to-1 session

## Support This Project

RESTHeart is open source and free. If you find it valuable, consider [sponsoring us on GitHub](https://github.com/sponsors/SoftInstigate).

<table>
  <tbody>
    <tr>
      <td align="center" valign="middle">
        <a href="https://www.softinstigate.com" target="_blank">
          <img width="222px" src="https://www.softinstigate.com/images/logo.png" alt="SoftInstigate">
        </a>
      </td>
    </tr>
  </tbody>
</table>

---

_Made with :heart: by [SoftInstigate](https://www.softinstigate.com). Follow us on [Twitter](https://twitter.com/softinstigate)_.
