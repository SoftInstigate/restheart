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

You can start querying your data right away, using **any HTTP client** — no SDKs or special libraries needed.

For example, you can fetch MongoDB documents directly from the browser with just a few lines of JavaScript:

```javascript
const url = encodeURI('https://demo.restheart.org/messages?filter={"from":"Bob"}&pagesize=1');

fetch(url)
  .then(response => response.json())
  .then(json => console.log(JSON.stringify(json, null, 2)));
```

---

## Why RESTHeart?

RESTHeart isn’t just a MongoDB REST layer — it’s a **complete backend foundation**.

| Capability | Description |
|-------------|--------------|
| 🧩 **Zero-code APIs** | Instantly expose collections and documents over REST and GraphQL |
| ⚙️ **Extensible framework** | Add custom business logic using plugins, written in Java or JavaScript |
| 🔐 **Security built-in** | Authentication, authorization, role-based access control, and HTTPS |
| 💬 **WebSockets** | Real-time data updates and event streaming |
| 🚀 **High-performance engine** | Built on Undertow with Virtual Threads (Project Loom) |
| 🌐 **Polyglot runtime** | Run plugins in JavaScript or Python when using GraalVM |
| 📈 **Observability** | Metrics, health checks, and monitoring endpoints |
| 🧰 **Developer tools** | CLI, hot-reload, plugin scaffolding, Docker, and native binaries |

RESTHeart combines the **speed of instant APIs** with the **flexibility of a full backend framework**.

Perfect for:

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

Test your instance:

```sh
curl localhost:8080/ping
```

That’s it! You now have a running RESTHeart connected to MongoDB.

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

RESTHeart uses a single YAML configuration file or the `RHO` environment variable to customize runtime behavior.

Key areas you can configure:

- MongoDB connection(s)
- Authentication & roles
- API exposure rules
- Plugin registration
- Metrics and logging

See the [Configuration Guide](https://restheart.org/docs/configuration).

---

## Plugin Architecture

Plugins let you extend RESTHeart with **custom services, interceptors, and validators**.

- Write your own APIs in Java or JavaScript
- Register them via configuration or at runtime
- Use `restheart-cli` to scaffold, test, and package plugins with hot reload

```bash
npx restheart-cli create my-plugin
cd my-plugin
npx restheart-cli dev
```

---

## RESTHeart Cloud

Want RESTHeart as a managed service?  
[RESTHeart Cloud](https://cloud.restheart.com) provides:

- Zero deployment friction  
- Built-in scalability  
- Centralized monitoring and analytics  
- Instant API publishing  

Ideal for production APIs or rapid experimentation.

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

- 🤖 [Ask Sophia](https://sophia.restheart.com) — your AI assistant for docs and usage help  
- 💬 [Slack](https://join.slack.com/t/restheart/shared_invite/zt-1olrhtoq8-5DdYLBWYDonFGEALhmgSXQ) — community chat  
- 🐛 [GitHub Issues](https://github.com/SoftInstigate/restheart/issues/new) — report bugs  
- 💡 [Stack Overflow](https://stackoverflow.com/questions/ask?tags=restheart) — technical Q&A  
- 📅 [Book a demo](https://calendly.com/restheart) — free 1:1 session

---

## Contribute

RESTHeart is open source and community-driven.  
Help us improve it — star, fork, or contribute on GitHub.

👉 [GitHub Repository](https://github.com/SoftInstigate/restheart)

---

_Made with ❤️ by [SoftInstigate](https://www.softinstigate.com)_
