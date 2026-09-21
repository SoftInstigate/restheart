# <img src="https://cloud.restheart.com/assets/img/restheart%20logo.svg" alt="RESTHeart logo" width="32px" height="auto" /> RESTHeart

**A backend for web, mobile, AI and IoT apps.**

[![GitHub last commit](https://img.shields.io/github/last-commit/softinstigate/restheart)](https://github.com/SoftInstigate/restheart/commits/master)
[![Build snapshot release](https://github.com/SoftInstigate/restheart/actions/workflows/branch.yml/badge.svg)](https://github.com/SoftInstigate/restheart/actions/workflows/branch.yml)
[![Github stars](https://img.shields.io/github/stars/SoftInstigate/restheart?label=Github%20Stars)](https://github.com/SoftInstigate/restheart)
[![Maven Central Version](https://img.shields.io/maven-central/v/org.restheart/restheart)](https://central.sonatype.com/namespace/org.restheart)
[![javadoc](https://javadoc.io/badge2/org.restheart/restheart-commons/javadoc.svg)](https://javadoc.io/doc/org.restheart/restheart-commons)
[![Docker Pulls](https://img.shields.io/docker/pulls/softinstigate/restheart.svg?maxAge=2592000)](https://hub.docker.com/r/softinstigate/restheart/)
[![Join the chat on Slack](https://img.shields.io/badge/chat-on%20slack-orange)](https://join.slack.com/t/restheart/shared_invite/zt-1olrhtoq8-5DdYLBWYDonFGEALhmgSXQ)
[![CLA assistant](https://cla-assistant.io/readme/badge/SoftInstigate/restheart)](https://cla-assistant.io/SoftInstigate/restheart)

---

## What RESTHeart is

RESTHeart is a backend for web, mobile, AI and IoT apps. It gives an application the backend it would otherwise have to write — AI agents and RAG, accounts, permissions, data APIs, live data, email, payments — as configuration, not code. What an application needs beyond it goes into plugins, in Java, Kotlin, JavaScript or TypeScript.

![RESTHeart logical architecture](docs/restheart_logic_architecture.png)

### Example

```bash
curl "https://demo.restheart.org/messages?filter={\"from\":\"Bob\"}&pagesize=1"
```

No route was written for `/messages`. It is a MongoDB collection, and the query parameters (`filter`, `pagesize`, sorting) map directly to MongoDB's query language.

### Core capabilities

- [**AI agents**](https://restheart.org/docs/ai/mcp): an MCP server, so agents use the application's APIs under the same permissions as its users
- [**RAG and vector search**](https://restheart.org/docs/ai/vector-search): embeddings computed on write (Voyage AI, any OpenAI-compatible endpoint, Ollama), uploaded documents chunked for retrieval, results re-ranked
- [**Accounts**](https://restheart.org/docs/accounts/overview): sign-up, login, email verification, password reset, team invitations, Google sign-in, tokens and API keys
- [**Authentication and Authorization**](https://restheart.org/docs/security/overview): JWT, OAuth2, LDAP, MongoDB-based users, ACL rules per role, down to the documents a user may read or change, defined as data, not code
- [**REST API**](https://restheart.org/docs/mongodb-rest/): full CRUD, aggregations, filtering, sorting, pagination, JSON Schema validation, generated from the database schema
- [**GraphQL**](https://restheart.org/docs/graphql/): schema-driven mapping to MongoDB queries
- [**WebSocket**](https://restheart.org/docs/websocket/): change streams exposed as real-time data sync
- [**SSE**](https://restheart.org/docs/sse/): Server-Sent Events for dashboards, IoT feeds, and event streams
- [**Email**](https://restheart.org/docs/framework/emails): transactional messages from templates, over your own SMTP
- [**Payments**](https://restheart.org/docs/stripe/overview): Stripe plans, seats and products
- [**Plugin framework**](https://restheart.org/docs/framework/overview): custom services, interceptors, and initializers in Java, Kotlin, JavaScript, or TypeScript, for the logic a declarative API cannot cover
- [**Metrics and monitoring**](https://restheart.org/docs/deployment/monitoring): a Prometheus-compatible endpoint plus a browser dashboard at `/metrics-ui`, tracking request rates, latency percentiles, and HTTP status distribution
- **IoT / MQTT**: ingest device telemetry directly into MongoDB *(coming soon)*

Distributed as a Docker image and a GraalVM native binary. Built on Java 25, Undertow, and virtual threads.

### Running it

Fully managed, no installation required, and including [Sophia](https://restheart.org/docs/cloud/sophia/mcp), an MCP server and browser assistant that exposes RESTHeart's docs and plugin API to AI coding assistants such as Claude Code, Cursor, or VS Code:

[![Try RESTHeart Cloud for free](https://restheart.org/images/restheart-cloud-button.svg)](https://cloud.restheart.com/signup)

Or self-hosted, since the core is free and open source:

```bash
curl https://raw.githubusercontent.com/SoftInstigate/restheart/master/docker-compose.yml \
  --output docker-compose.yml && docker compose up --attach restheart

curl http://localhost:8080/ping
```

Full documentation: https://restheart.org/docs/

---

## License

RESTHeart core is licensed under the GNU AGPL v3.

`restheart-commons`, the Maven artifact plugins depend on to reach the Approved Interfaces, is licensed under the Apache License 2.0. Plugins that depend only on `restheart-commons`, without linking against RESTHeart's AGPL-licensed core, can be distributed under any license, including proprietary ones. See [PLUGIN_EXCEPTION.md](PLUGIN_EXCEPTION.md) for the exact terms of this permission.

---
