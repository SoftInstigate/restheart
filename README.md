# RESTHeart

**The backend framework that instantly turns MongoDB into REST, GraphQL, and WebSocket APIs — no code required.**

[![GitHub last commit](https://img.shields.io/github/last-commit/softinstigate/restheart)](https://github.com/SoftInstigate/restheart/commits/master)
[![Build snapshot release](https://github.com/SoftInstigate/restheart/actions/workflows/branch.yml/badge.svg)](https://github.com/SoftInstigate/restheart/actions/workflows/branch.yml)
[![Github stars](https://img.shields.io/github/stars/SoftInstigate/restheart?label=Github%20Stars)](https://github.com/SoftInstigate/restheart)
[![Maven Central Version](https://img.shields.io/maven-central/v/org.restheart/restheart)](https://central.sonatype.com/namespace/org.restheart)
[![javadoc](https://javadoc.io/badge2/org.restheart/restheart-commons/javadoc.svg)](https://javadoc.io/doc/org.restheart/restheart-commons)
[![Docker Pulls](https://img.shields.io/docker/pulls/softinstigate/restheart.svg?maxAge=2592000)](https://hub.docker.com/r/softinstigate/restheart/)
[![Join the chat on Slack](https://img.shields.io/badge/chat-on%20slack-orange)](https://join.slack.com/t/restheart/shared_invite/zt-1olrhtoq8-5DdYLBWYDonFGEALhmgSXQ)

---

## What is RESTHeart?

RESTHeart is a Java backend framework that instantly exposes your MongoDB database through secure **REST**, **GraphQL**, and **WebSocket** APIs — **no backend code required**.

The framework allows direct data access through standard HTTP clients, without requiring SDKs or specialized libraries.

For example, MongoDB documents can be queried directly from a browser using standard JavaScript:

```javascript
const url = encodeURI('https://demo.restheart.org/messages?filter={"from":"Bob"}&pagesize=1');

fetch(url)
  .then(response => response.json())
  .then(json => console.log(JSON.stringify(json, null, 2)));
```

📄 **Complete documentation** available at <https://restheart.org/docs/>

---

## Why RESTHeart?

RESTHeart isn’t just a MongoDB REST layer — it’s a **complete backend foundation**.

| Capability | Description |
|-------------|--------------|
| 🧩 **Automatic API generation** | Exposes MongoDB collections and documents via REST and GraphQL |
| ⚙️ **Plugin framework** | Extend functionality with custom services, interceptors, and validators in Java or JavaScript |
| 🔐 **Security layer** | Built-in authentication, authorization, role-based access control, and TLS support |
| 💬 **WebSocket support** | Real-time data updates and change stream notifications |
| 🚀 **Undertow-based runtime** | Non-blocking I/O with Virtual Threads (Project Loom) support |
| 🌐 **Polyglot plugins** | Run JavaScript or Python plugins when using GraalVM |
| 📈 **Monitoring** | Prometheus metrics, health checks, and observability endpoints |
| 🧰 **Development tools** | CLI tooling, hot-reload support, Docker images, and native compilation |

RESTHeart is suitable for:

- 🚧 Rapid prototyping and MVPs  
- 🧠 Scalable microservices architectures  
- 🧩 Enterprise backends extending MongoDB with custom logic  
- ☁️ Deployments on AWS, GCP, Azure, or RESTHeart Cloud  

---

## Quick Start

Run MongoDB + RESTHeart in **30 seconds**:

```sh
curl https://raw.githubusercontent.com/SoftInstigate/restheart/master/docker-compose.yml --output docker-compose.yml && docker compose up --pull=always --attach restheart
```

Verify the deployment:

```sh
curl localhost:8080/ping
```

RESTHeart is now running and connected to MongoDB.

---

## Examples

### Query documents via REST

```bash
curl 'http://localhost:8080/people?filter={"age":{"$gt":30}}'
```

### Query documents via GraphQL

```graphql
{
  people(filter: "{'age': {'$gt': 30}}", pagesize: 5) {
    _id
    name
    age
  }
}
```

### Receive real-time updates via WebSocket

```javascript
const ws = new WebSocket("wss://demo.restheart.org/ws/messages");
ws.onmessage = e => console.log("New message:", e.data);
```

---

## Configuration

RESTHeart configuration is managed through a YAML file or the `RHO` environment variable for runtime overrides.

Configurable components include:

- MongoDB connection strings and client settings
- Authentication mechanisms and user management
- Authorization policies and ACL rules
- Plugin configuration and registration
- Metrics collection and logging levels

See the [Configuration Guide](https://restheart.org/docs/configuration).

---

## Plugin Architecture

The plugin architecture allows extending RESTHeart with custom services, interceptors, and validators.

- Implement custom APIs in Java, JavaScript and other GraalVM suported languages
- Register plugins via configuration or programmatically at runtime
- Use the [restheart-cli](https://github.com/SoftInstigate/restheart-cli) tool for scaffolding, testing, and hot-reload during development

---

## RESTHeart Cloud

Want RESTHeart as a managed service?  
[RESTHeart Cloud](https://cloud.restheart.com) provides:

- Managed deployment and infrastructure
- Automatic scaling and load balancing
- Centralized monitoring and logging
- API management and publishing capabilities

Suitable for production deployments or development environments.

---

## Supported Databases

- ✅ MongoDB / MongoDB Atlas  
- ✅ Percona Server for MongoDB  
- ⚙️ FerretDB (partial)  
- ⚙️ Amazon DocumentDB (partial)  
- ⚙️ Azure CosmosDB (partial)  

Compatibility depends on MongoDB wire protocol support.

---

## Community and Support

- 📄 [Documentation](https://restheart.org/docs/) - Comprehensive configuration, development and deployment documents.
- 🤖 [Ask Sophia](https://sophia.restheart.com) — AI-powered documentation assistant  
- 💬 [Slack](https://join.slack.com/t/restheart/shared_invite/zt-1olrhtoq8-5DdYLBWYDonFGEALhmgSXQ) — Community discussion channel  
- 🐛 [GitHub Issues](https://github.com/SoftInstigate/restheart/issues/new) — Bug reports and feature requests  
- 💡 [Stack Overflow](https://stackoverflow.com/questions/ask?tags=restheart) — Technical questions  
- 📅 [Book a demo](https://calendly.com/restheart) — Schedule a consultation

---

_Made with ❤️ by [SoftInstigate](https://www.softinstigate.com)_
