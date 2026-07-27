# <img src="https://cloud.restheart.com/assets/img/restheart%20logo.svg" alt="RESTHeart logo" width="32px" height="auto" /> RESTHeart

[![GitHub last commit](https://img.shields.io/github/last-commit/softinstigate/restheart)](https://github.com/SoftInstigate/restheart/commits/master)
[![Build snapshot release](https://github.com/SoftInstigate/restheart/actions/workflows/branch.yml/badge.svg)](https://github.com/SoftInstigate/restheart/actions/workflows/branch.yml)
[![Github stars](https://img.shields.io/github/stars/SoftInstigate/restheart?label=Github%20Stars)](https://github.com/SoftInstigate/restheart)
[![Maven Central Version](https://img.shields.io/maven-central/v/org.restheart/restheart)](https://central.sonatype.com/namespace/org.restheart)
[![javadoc](https://javadoc.io/badge2/org.restheart/restheart-commons/javadoc.svg)](https://javadoc.io/doc/org.restheart/restheart-commons)
[![Docker Pulls](https://img.shields.io/docker/pulls/softinstigate/restheart.svg?maxAge=2592000)](https://hub.docker.com/r/softinstigate/restheart/)
[![Join the chat on Slack](https://img.shields.io/badge/chat-on%20slack-orange)](https://join.slack.com/t/restheart/shared_invite/zt-1olrhtoq8-5DdYLBWYDonFGEALhmgSXQ)
[![CLA assistant](https://cla-assistant.io/readme/badge/SoftInstigate/restheart)](https://cla-assistant.io/SoftInstigate/restheart)

---

## The Open Source Backend for MongoDB

Instant REST, GraphQL, and WebSocket APIs. No backend code.

Built-in authentication and authorization. Declarative, zero boilerplate.

Need custom logic? Extend it in Java, Kotlin, JavaScript, or TypeScript.

Plus [Sophia](https://restheart.org/docs/cloud/sophia/mcp), the AI assistant: chat with the docs, or vibe code via MCP.

[![Try RESTHeart Cloud for free](https://restheart.org/images/restheart-cloud-button.svg)](https://cloud.restheart.com/signup)

Fully managed, no installation required. Or run it yourself, free and open source, see below.

![RESTHeart logical architecture](docs/restheart_logic_architecture.png)

**Core capabilities:**

- [**REST API**](https://restheart.org/docs/mongodb-rest/): Full CRUD, aggregations, filtering, sorting, pagination
- [**GraphQL**](https://restheart.org/docs/graphql/): Schema-driven mapping to MongoDB queries
- [**WebSocket**](https://restheart.org/docs/websocket/): Real-time change streams and data sync
- **SSE**: Server-Sent Events for live dashboards, IoT feeds, and event streams
- [**Authentication and Authorization**](https://restheart.org/docs/security/overview): JWT, OAuth2, LDAP, MongoDB-based users, ACL rules
- [**Plugin framework**](https://restheart.org/docs/framework/overview): Extend with Java, Kotlin, JavaScript, or TypeScript for custom business logic
- **Sophia AI assistant**: Chat with the docs in your browser, or connect Claude, Claude Code, Cursor, or VS Code to the MCP server to vibe code and configure your backend
- **Metrics & Monitoring**: Built-in Prometheus-compatible metrics endpoint with a real-time browser dashboard at `/metrics-ui`, tracking request rates, latency percentiles, and HTTP status distribution
- **IoT / MQTT**: Connect devices and ingest telemetry directly into MongoDB *(coming soon)*

Available as a Docker image and GraalVM native binary. Built on Java 25, Undertow, and virtual threads.

---
