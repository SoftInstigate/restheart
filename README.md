# <img src="https://cloud.restheart.com/assets/img/restheart%20logo.svg" alt="RESTHeart logo" width="32px" height="auto" /> RESTHeart

**REST, GraphQL and WebSocket APIs for MongoDB**

[![GitHub last commit](https://img.shields.io/github/last-commit/softinstigate/restheart)](https://github.com/SoftInstigate/restheart/commits/master)
[![Build snapshot release](https://github.com/SoftInstigate/restheart/actions/workflows/branch.yml/badge.svg)](https://github.com/SoftInstigate/restheart/actions/workflows/branch.yml)
[![Github stars](https://img.shields.io/github/stars/SoftInstigate/restheart?label=Github%20Stars)](https://github.com/SoftInstigate/restheart)
[![Maven Central Version](https://img.shields.io/maven-central/v/org.restheart/restheart)](https://central.sonatype.com/namespace/org.restheart)
[![javadoc](https://javadoc.io/badge2/org.restheart/restheart-commons/javadoc.svg)](https://javadoc.io/doc/org.restheart/restheart-commons)
[![Docker Pulls](https://img.shields.io/docker/pulls/softinstigate/restheart.svg?maxAge=2592000)](https://hub.docker.com/r/softinstigate/restheart/)
[![Join the chat on Slack](https://img.shields.io/badge/chat-on%20slack-orange)](https://join.slack.com/t/restheart/shared_invite/zt-1olrhtoq8-5DdYLBWYDonFGEALhmgSXQ)

---

## What is RESTHeart?

RESTHeart transforms MongoDB into a complete backend platform with automatically generated REST APIs, configurable GraphQL, WebSocket support for real-time data, and a plugin system for custom logic.

Built on Java with proven production deployments at scale.

**Core capabilities:**
- **REST API** — Full CRUD, aggregations, filtering, sorting, pagination
- **GraphQL** — Define schemas and map them to MongoDB queries
- **WebSocket** — Real-time change streams and data sync
- **Authentication & Authorization** — JWT, OAuth2, LDAP, MongoDB-based users, ACL rules
- **Plugin system** — Extend with Java, JavaScript, or Python when you need custom logic

**No code required for standard database operations.** Write plugins only for custom business logic.

Works with MongoDB, MongoDB Atlas, Percona, DocumentDB, Cosmos DB, and FerretDB.

---

## Quick Start

```bash
# Start MongoDB + RESTHeart with Docker Compose
curl https://raw.githubusercontent.com/SoftInstigate/restheart/master/docker-compose.yml \
  --output docker-compose.yml && docker compose up --attach restheart

# Test it
curl http://localhost:8080/ping
```

Default credentials: `admin` / `secret` (change in production)

More options: https://restheart.org/docs/foundations/quick-start

---

## Example: Query MongoDB via HTTP

```javascript
const url = encodeURI('https://demo.restheart.org/messages?filter={"from":"Bob"}&pagesize=1');

fetch(url)
  .then(response => response.json())
  .then(json => console.log(JSON.stringify(json, null, 2)));
```

That's it. No Express routes, no Mongoose schemas, no middleware setup.

📄 Full documentation: https://restheart.org/docs/

---

## Architecture

![RESTHeart architecture diagram](docs/restheart-architecture.svg)

RESTHeart sits between clients and MongoDB, providing:
- **API layer** — REST, GraphQL, WebSocket endpoints
- **Security** — Authentication, authorization, request validation
- **Plugin runtime** — Custom business logic in Java, JavaScript, or Python
- **Observability** — Metrics, logging, health checks

---

## Key Features

- 🔌 **Zero-config API** — Collections automatically become REST endpoints
- 🔐 **Production-ready security** — JWT, OAuth2, LDAP, role-based access control
- ⚡ **High performance** — Undertow NIO + Java Virtual Threads, handles 10K+ concurrent connections
- 💬 **Real-time** — Native WebSocket support for MongoDB change streams
- 🧩 **Extensible** — Plugin system supports Java, JavaScript, TypeScript, Python
- 🚀 **Stateless** — Scales horizontally, runs on VMs, containers, Kubernetes, or as native binary

**Battle-tested:** 2M+ downloads, 10+ years in production at scale.

---

## Use Cases

- **API development without boilerplate** — Skip CRUD code, focus on business logic
- **Mobile and web backends** — Get REST/GraphQL APIs immediately
- **Real-time applications** — WebSocket support for chat, notifications, live dashboards
- **MongoDB Data API replacement** — Self-hosted alternative to the deprecated Atlas Data API ([migration guide](https://www.softinstigate.com/en/blog/posts/mongodb-deprecates-data-api/))
- **Legacy modernization** — Add modern APIs to existing MongoDB databases
- **PostgreSQL with MongoDB API** — Use via FerretDB for PostgreSQL storage ([tutorial](https://www.softinstigate.com/en/blog/posts/ferretdb-tutorial/))

---

## Extend with Plugins

Write custom logic only when you need it. RESTHeart handles the rest.

### Java Plugin

```java
@RegisterPlugin(name = "greetings")
public class GreeterService implements JsonService {
    @Override
    public void handle(JsonRequest req, JsonResponse res) {
        res.setContent(object()
            .put("message", "Hello World!")
            .put("timestamp", Instant.now()));
    }
}
```

### JavaScript Plugin

```javascript
export const options = {
    name: "greetings",
    uri: "/greetings"
}

export function handle(request, response) {
    response.setContent(JSON.stringify({
        message: 'Hello World!',
        timestamp: new Date().toISOString()
    }));
    response.setContentTypeAsJson();
}
```

**Plugin capabilities:**
- Services (custom REST endpoints)
- Interceptors (modify requests/responses, add validation)
- Initializers (run code at startup)
- Providers (dependency injection)

📖 Plugin development: https://restheart.org/docs/plugins/overview/

🔧 Use [restheart-cli](https://github.com/SoftInstigate/restheart-cli) for scaffolding, testing, and hot-reload.

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

### Native Executables

Prebuilt binaries for macOS, Linux, Windows with faster startup and lower memory.

See [docs/native-executables.md](docs/native-executables.md) for download links.

### RESTHeart Cloud

Fully managed service: [cloud.restheart.com](https://cloud.restheart.com)

- Instant provisioning
- Automatic scaling
- Free tier available
- Premium plugins (Webhooks, Sophia AI, Facet)

---

## Database Compatibility

| Database | Support Level | Notes |
|----------|---------------|-------|
| ✅ **MongoDB** | Full | All versions 3.6+ |
| ✅ **MongoDB Atlas** | Full | Cloud-native support |
| ✅ **Percona Server** | Full | Drop-in MongoDB replacement |
| ⚙️ **FerretDB** | Partial | PostgreSQL-backed ([tutorial](https://www.softinstigate.com/en/blog/posts/ferretdb-tutorial/)) |
| ⚙️ **AWS DocumentDB** | Partial | Most features work |
| ⚙️ **Azure Cosmos DB** | Partial | MongoDB API compatibility layer |

---

## Community & Support

- 📄 **[Documentation](https://restheart.org/docs/)** — API reference and guides
- 🤖 **[Ask Sophia](https://sophia.restheart.com)** — AI documentation assistant
- 💬 **[Slack](https://join.slack.com/t/restheart/shared_invite/zt-1olrhtoq8-5DdYLBWYDonFGEALhmgSXQ)** — Community chat
- 🐛 **[GitHub Issues](https://github.com/SoftInstigate/restheart/issues/new)** — Bug reports and feature requests
- 💡 **[Stack Overflow](https://stackoverflow.com/questions/ask?tags=restheart)** — Tag: `restheart`

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
- **AGPL v3** — Open source (free). Plugin development under Apache 2.0.
- **Commercial** — For proprietary applications.

Same features in both licenses.

Commercial inquiries: [info@softinstigate.com](mailto:info@softinstigate.com)

---

_Built with ❤️ by [SoftInstigate](https://www.softinstigate.com) | [GitHub](https://github.com/SoftInstigate/restheart) | [Website](https://restheart.org) | [Cloud](https://cloud.restheart.com)_

---

<div align="center">
<img src="docs/made-in-eu-logo.svg" alt="Made in EU" width="180px" />
</div>
