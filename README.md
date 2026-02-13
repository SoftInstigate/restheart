# <img src="https://cloud.restheart.com/assets/img/restheart%20logo.svg" alt="RESTHeart logo" width="32px" height="auto" /> RESTHeart

**Open Source REST API & GraphQL Server — Instant backend APIs with low code required.**

[![GitHub last commit](https://img.shields.io/github/last-commit/softinstigate/restheart)](https://github.com/SoftInstigate/restheart/commits/master)
[![Build snapshot release](https://github.com/SoftInstigate/restheart/actions/workflows/branch.yml/badge.svg)](https://github.com/SoftInstigate/restheart/actions/workflows/branch.yml)
[![Github stars](https://img.shields.io/github/stars/SoftInstigate/restheart?label=Github%20Stars)](https://github.com/SoftInstigate/restheart)
[![Maven Central Version](https://img.shields.io/maven-central/v/org.restheart/restheart)](https://central.sonatype.com/namespace/org.restheart)
[![javadoc](https://javadoc.io/badge2/org.restheart/restheart-commons/javadoc.svg)](https://javadoc.io/doc/org.restheart/restheart-commons)
[![Docker Pulls](https://img.shields.io/docker/pulls/softinstigate/restheart.svg?maxAge=2592000)](https://hub.docker.com/r/softinstigate/restheart/)
[![Join the chat on Slack](https://img.shields.io/badge/chat-on%20slack-orange)](https://join.slack.com/t/restheart/shared_invite/zt-1olrhtoq8-5DdYLBWYDonFGEALhmgSXQ)

---

## What is RESTHeart?

RESTHeart is an **open-source Java server** that auto-generates REST, GraphQL, and WebSocket APIs from your database collections.

**Core capabilities:**

- **REST API** — Full CRUD operations, aggregations, filtering, sorting, pagination
- **GraphQL** — Create GraphQL APIs with schema definitions and MongoDB mappings
- **WebSocket** — Real-time change streams and data synchronization
- **Authentication** — JWT, OAuth2, LDAP, and MongoDB-based user management
- **Authorization** — Role-based access control (RBAC) and declarative security policies

**No code required** for standard database operations. Write plugins in Java, JavaScript, or Python only when you need custom business logic.

**Database support:** MongoDB, MongoDB Atlas, Percona Server, AWS DocumentDB, Azure Cosmos DB, FerretDB.

**Quick Start (5 min)**

```bash
# Docker Compose (recommended)
curl https://raw.githubusercontent.com/SoftInstigate/restheart/master/docker-compose.yml \
  --output docker-compose.yml && docker compose up --attach restheart

# First request
curl http://localhost:8080/ping
```

Default dev credential: `admin` / `secret` (change before production).

More options: https://restheart.org/docs/foundations/quick-start

### Example

Query MongoDB directly via HTTP:

```javascript
const url = encodeURI('https://demo.restheart.org/messages?filter={"from":"Bob"}&pagesize=1');

fetch(url)
  .then(response => response.json())
  .then(json => console.log(JSON.stringify(json, null, 2)));
```

📄 **Complete documentation** available at <https://restheart.org/docs/>

---

## Architecture & Features

![RESTHeart architecture diagram](docs/restheart-architecture.svg)

| Feature | Description |
|-------------|--------------|
| 🧩 **Automatic API Generation** | REST APIs auto-generated from MongoDB collections — no manual endpoint definition |
| 📊 **GraphQL Support** | Build GraphQL APIs with schema definitions and MongoDB query mappings |
| ⚙️ **Plugin System** | Extend via Services, Interceptors, Initializers, and Providers — hot-reload support |
| 🔐 **Security** | Pluggable authentication (JWT, OAuth2, LDAP, custom) and ACL-based authorization |
| 💬 **WebSockets** | Native change stream support with automatic client synchronization |
| 🚀 **Performance** | Undertow NIO server with Java 25 Virtual Threads — 10K+ concurrent connections |
| 🌐 **Polyglot** | Java, Kotlin native support — JavaScript, TypeScript, Python via GraalVM |
| 📈 **Observability** | Prometheus metrics, health endpoints, request/response logging |
| 🧰 **Developer Tools** | CLI for plugin development, Docker images, GraalVM native compilation |
| ☁️ **Deployment** | Stateless architecture — runs on VMs, containers, Kubernetes, or as native binary |

---

## Use Cases

- **Rapid API development** — Skip boilerplate CRUD code, focus on business logic
- **Mobile/web backends** — REST and GraphQL APIs without Express.js/Fastify setup  
- **Real-time applications** — WebSocket support for chat, notifications, live updates
- **Legacy MongoDB modernization** — Add modern APIs to existing databases without data migration
- **MongoDB Data API replacement** — Drop-in alternative for the deprecated MongoDB Atlas Data API with enhanced features
- **Prototyping & MVPs** — Functional backend in minutes for proof-of-concepts
- **Microservices** — Lightweight, stateless architecture with built-in API gateway features
- **IoT data collection** — Efficient resource usage, fast startup, edge deployment ready
- **PostgreSQL with MongoDB API** — Use MongoDB query syntax with PostgreSQL via FerretDB translation layer

---

## MongoDB Data API Replacement

**MongoDB deprecated its Atlas Data API in September 2024.** RESTHeart is a powerful, self-hosted alternative that provides everything the Data API offered — and much more.

### Why Choose RESTHeart as Your Data API Alternative?

| Feature | MongoDB Data API | RESTHeart |
|---------|------------------|-----------|
| **REST API** | ✅ Basic CRUD | ✅ Full CRUD + aggregations |
| **GraphQL** | ❌ | ✅ With schema definitions |
| **WebSocket** | ❌ | ✅ Change streams |
| **Hosting** | Atlas only | Self-hosted or cloud |
| **Authentication** | API keys | JWT, OAuth2, LDAP, custom |
| **Real-time** | ❌ | ✅ Native support |
| **Extensibility** | ❌ | ✅ Plugin system |
| **Status** | Deprecated | Active development |

### Migration Benefits

- **No vendor lock-in** — Works with MongoDB Atlas, self-hosted MongoDB, or compatible databases
- **Enhanced features** — GraphQL support, WebSockets, advanced filtering, aggregation pipelines
- **Better security** — Multiple authentication mechanisms, fine-grained authorization
- **Cost control** — Self-hosted option eliminates per-request pricing
- **Future-proof** — Active open-source community and commercial support

📖 **Read more:** [MongoDB Deprecates Data API - What's Next?](https://www.softinstigate.com/en/blog/posts/mongodb-deprecates-data-api/)

---

## PostgreSQL Support via FerretDB

While RESTHeart is MongoDB-native, you can use it with **PostgreSQL** through [FerretDB](https://www.ferretdb.com/) — an open-source MongoDB-compatible database that uses PostgreSQL as its storage engine.

### How It Works

```
Application → RESTHeart → FerretDB → PostgreSQL
             (MongoDB API) (Translation Layer) (Storage)
```

FerretDB translates MongoDB wire protocol commands to SQL queries, allowing RESTHeart to:

- Provide MongoDB-style REST and GraphQL APIs
- Use MongoDB query syntax (filters, projections, aggregations)
- Store data in PostgreSQL tables
- Leverage PostgreSQL's ACID guarantees and ecosystem

### When to Use FerretDB with RESTHeart

✅ **Good for:**
- Teams familiar with MongoDB wanting PostgreSQL's reliability
- Organizations with PostgreSQL expertise and infrastructure
- Projects requiring PostgreSQL-specific features (full-text search, PostGIS)
- Gradual migration from MongoDB to PostgreSQL

⚠️ **Limitations:**
- Some advanced MongoDB features not yet supported (check [FerretDB compatibility](https://docs.ferretdb.io/reference/supported-commands/))
- Performance characteristics differ from native MongoDB
- Change streams support depends on FerretDB version

📖 **Complete tutorial:** [Getting Started with FerretDB and RESTHeart](https://www.softinstigate.com/en/blog/posts/ferretdb-tutorial/)

---

## Comparison with Similar Tools

- **RESTHeart vs Supabase** — RESTHeart is a self-hosted API server that auto-generates REST and configurable GraphQL from MongoDB, while Supabase is a full BaaS around PostgreSQL with hosted auth, storage, and edge functions. If you want a managed BaaS like Supabase but centered on MongoDB APIs, [RESTHeart Cloud](https://cloud.restheart.com/) is the direct alternative.
- **RESTHeart vs Hasura** — Both provide auto-generated APIs; RESTHeart is MongoDB-native (and supports change streams natively), while Hasura focuses on SQL databases and auto-generates GraphQL with optional RESTified endpoints.

**When to choose RESTHeart** — If you want MongoDB-native APIs with zero-code REST plus configurable GraphQL, or you need first-class change streams and plugin extensibility.

---

## Quick Start

Start MongoDB and RESTHeart with Docker Compose:

```bash
curl https://raw.githubusercontent.com/SoftInstigate/restheart/master/docker-compose.yml \
  --output docker-compose.yml && docker compose up --pull=always --attach restheart
```

Test the deployment:

```bash
curl localhost:8080/ping
```

RESTHeart is now running. APIs are available at `http://localhost:8080/`.

---

## API Examples

### REST API

```bash
# Query with filters
curl 'http://localhost:8080/people?filter={"age":{"$gt":30}}'

# Pagination and sorting
curl 'http://localhost:8080/people?pagesize=10&page=1&sort={"name":1}'

# Projection (select specific fields)
curl 'http://localhost:8080/people?keys={"name":1,"age":1}'
```

### GraphQL API

> **Note:** GraphQL APIs require creating a GraphQL app definition with schema and mappings. See the [GraphQL documentation](https://restheart.org/docs/mongodb-graphql/getting-started) for setup instructions.

```graphql
{
  people(filter: "{'age': {'$gt': 30}}", pagesize: 5, sort: NAME_ASC) {
    _id
    name
    age
    email
  }
}
```

### WebSocket API

```javascript
// Connect to change streams
const ws = new WebSocket("wss://demo.restheart.org/ws/messages");

// Receive updates when data changes
ws.onmessage = e => {
  const update = JSON.parse(e.data);
  console.log("Update:", update);
};

// Optional: filter updates
ws.send(JSON.stringify({ 
  filter: { from: "Alice" } 
}));
```

---

## Configuration

Configuration uses sensible defaults, with optional overrides via `RHO` or a file.

**Common options:**

| Use case | Command |
| --- | --- |
| Default config (no file) | `java -jar restheart.jar` |
| Generate a full config file | `java -jar restheart.jar -t 2> restheart.yml` |
| Override specific keys | `RHO='/http-listener/port->9090' java -jar restheart.jar` |
| Override file | `java -jar restheart.jar -o overrides.yml` |

**Key areas:**

- 🔌 **MongoDB Connection** — Connection strings, replica sets, authentication
- 👥 **User Management** — Define users, roles, and permissions
- 🔐 **Authentication** — JWT, OAuth2, LDAP, custom mechanisms
- 🛡️ **Authorization** — ACL rules, role-based access control
- 🔧 **Plugin System** — Register and configure custom extensions
- 📊 **Monitoring** — Metrics collection, logging levels, health checks

Example:

```bash
# Run with default configuration
java -jar restheart.jar

# Override a few settings
RHO='/http-listener/host->"0.0.0.0";/mclient/connection-string->"mongodb://127.0.0.1"' \
  java -jar restheart.jar
```

📖 See the [Configuration Guide](https://restheart.org/docs/deployment/configuration) for complete details.

---

## Plugin Architecture

Extend RESTHeart via plugins when you need custom logic beyond generated APIs.

**Plugin Types:**

- **Services** — Custom REST endpoints and APIs
- **Interceptors** — Modify requests/responses, add validation, logging
- **Initializers** — Run code at startup (database setup, caching)
- **Providers** — Dependency injection for shared components

**Supported Languages:**

- Java, Kotlin (native JVM support)
- JavaScript, TypeScript (via GraalVM)
- Python (via GraalVM)

### Example Plugin (Java)

```java
@RegisterPlugin(
    name = "greetings",
    description = "A simple greeting service"
)
public class GreeterService implements JsonService {
    @Override
    public void handle(JsonRequest req, JsonResponse res) {
        res.setContent(object()
            .put("message", "Hello World!")
            .put("timestamp", Instant.now()));
    }
}
```

### Example Plugin (JavaScript)

```javascript
export const options = {
    name: "greetings",
    description: "A simple greeting service"
}

export function handle(request, response) {
    response.setContent(JSON.stringify({
        message: 'Hello World!',
        timestamp: new Date().toISOString()
    }));
    response.setContentTypeAsJson();
}
```

🔧 Use [restheart-cli](https://github.com/SoftInstigate/restheart-cli) for plugin scaffolding, testing, and hot-reload during development.

---

## Deployment

### Docker

```bash
docker pull softinstigate/restheart:latest

docker run -p 8080:8080 \
  -v ./restheart.yml:/opt/restheart/etc/restheart.yml \
  softinstigate/restheart
```

### Kubernetes

Stateless architecture supports horizontal scaling. Configure with ConfigMaps and Secrets.

### RESTHeart Cloud

Managed service alternative: [cloud.restheart.com](https://cloud.restheart.com)

- Fully managed infrastructure
- Automatic scaling
- Monitoring and logging
- Enterprise SLAs

---

## Database Compatibility

| Database | Support Level | Notes |
|----------|---------------|-------|
| ✅ **MongoDB** | Full | All versions 3.6+ |
| ✅ **MongoDB Atlas** | Full | Cloud-native support |
| ✅ **Percona Server** | Full | Drop-in MongoDB replacement |
| ⚙️ **FerretDB** | Partial | PostgreSQL-backed MongoDB alternative |
| ⚙️ **AWS DocumentDB** | Partial | Most features work, some MongoDB 4.0+ features missing |
| ⚙️ **Azure Cosmos DB** | Partial | MongoDB API compatibility layer |

_Compatibility depends on MongoDB wire protocol implementation._

---

## FAQ

### How does RESTHeart compare to Express.js + Mongoose?

RESTHeart auto-generates APIs from MongoDB collections, eliminating the need for:

- Manual route definitions and controllers
- Request validation and error handling middleware
- Authentication/authorization setup
- WebSocket server configuration

You only write code for custom business logic via plugins.

### Can RESTHeart replace the deprecated MongoDB Data API?

Yes. RESTHeart is an excellent replacement for the MongoDB Atlas Data API (deprecated in September 2024). It provides:

- **Full MongoDB REST API** with filtering, sorting, pagination, and aggregations
- **GraphQL support** with schema definitions and MongoDB mappings
- **WebSocket/real-time** capabilities via change streams
- **Self-hosted control** — no vendor lock-in
- **Enhanced security** with JWT, OAuth2, and custom authentication
- **Plugin extensibility** for custom business logic

Migration is straightforward since RESTHeart uses standard MongoDB connections and can connect to MongoDB Atlas or any MongoDB instance.

### How does RESTHeart differ from Hasura or Prisma?

- **Hasura/PostGraphile:** PostgreSQL-focused, auto-generated GraphQL APIs
- **Prisma:** ORM/query builder, not a standalone API server
- **RESTHeart:** MongoDB-native, auto-generates REST APIs, GraphQL APIs require schema definitions

### Does RESTHeart scale for production?

Yes. Architecture features:

- Stateless design enables horizontal scaling
- Java Virtual Threads handle 10K+ concurrent connections
- Used in production by Fortune 500 companies
- Clustering and load balancing support

### Do I need to write code?

No, for standard CRUD operations and queries. RESTHeart auto-generates all endpoints.

Yes, for custom business logic. Use the plugin system to add:

- Custom validation
- Business rules
- Third-party integrations
- Custom authentication flows

### What's the license?

Dual-licensed:

- **AGPL v3** for the core components, **Apache 2.0** for common interfaces. Free, requires open-sourcing modifications if distributed. However, extending RESTHeart with your own plugins is under the Apache 2.0 license.
- **Commercial:** For proprietary applications, same features but no source code re-distribution requirement.

### Can I use it with MongoDB Atlas Cloud?

Yes, Atlas is a very effective way to manage a MongoDB database. RESTHeart connects to any MongoDB instance:

- MongoDB Atlas
- Self-hosted MongoDB
- Replica sets and sharded clusters
- AWS DocumentDB (partial compatibility)
- Azure Cosmos DB (partial compatibility)

### Can I use RESTHeart with PostgreSQL?

Yes, via FerretDB. FerretDB is an open-source MongoDB-compatible database that uses PostgreSQL as its storage engine. It translates MongoDB wire protocol commands to SQL queries, allowing RESTHeart to work with PostgreSQL while maintaining MongoDB's document-oriented API and query language. 

This is ideal for organizations that want to use PostgreSQL's reliability and ecosystem while providing MongoDB-style APIs to their applications.

See our [complete FerretDB tutorial](https://www.softinstigate.com/en/blog/posts/ferretdb-tutorial/) for setup instructions.

---

## Community & Support

- 📄 **[Documentation](https://restheart.org/docs/)** — API reference and guides
- 🤖 **[Ask Sophia](https://sophia.restheart.com)** — AI documentation assistant
- 💬 **[Slack](https://join.slack.com/t/restheart/shared_invite/zt-1olrhtoq8-5DdYLBWYDonFGEALhmgSXQ)** — Community chat
- 🐛 **[GitHub Issues](https://github.com/SoftInstigate/restheart/issues/new)** — Bug reports and feature requests
- 💡 **[Stack Overflow](https://stackoverflow.com/questions/ask?tags=restheart)** — Tag: `restheart`
- 📅 **[Book a Demo](https://calendly.com/restheart)** — Technical consultation

---

## Contributing

Contributions welcome. RESTHeart is open source (AGPL).

- Report bugs and request features via GitHub Issues
- Submit pull requests
- Improve documentation
- Share use cases

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

---

## License

Dual-licensed:

- **AGPL v3** — Open source
- **Commercial License** — For proprietary applications

Same features in both licenses.

Commercial inquiries: [info@softinstigate.com](mailto:info@softinstigate.com)

---

_Built with ❤️ by [SoftInstigate](https://www.softinstigate.com) | [GitHub](https://github.com/SoftInstigate/restheart) | [Website](https://restheart.org) | [Cloud](https://cloud.restheart.com)_
