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

## What RESTHeart is

RESTHeart is an open source API server for MongoDB. It sits between your database and your clients, and turns a MongoDB collection into a set of ready-to-use APIs, with authentication, authorization, and data access already handled.

Point it at a MongoDB instance and it exposes:

- [**REST API**](https://restheart.org/docs/mongodb-rest/): CRUD, aggregations, filtering, sorting, pagination, transactions
- [**GraphQL API**](https://restheart.org/docs/graphql/): schema-driven mapping to MongoDB queries
- [**WebSocket and SSE**](https://restheart.org/docs/websocket/): change streams and live data for dashboards, chat, IoT feeds
- [**Security**](https://restheart.org/docs/security/overview): JWT, OAuth2, LDAP, MongoDB-based users, ACL rules
- [**Plugin framework**](https://restheart.org/docs/framework/overview): Java, Kotlin, JavaScript, or TypeScript, for the logic that a generic API can't cover

It runs as a Docker image or a GraalVM native binary, on Java 25, Undertow, and virtual threads. Full documentation: [restheart.org/docs](https://restheart.org/docs/)

## The Agent-Ready Backend for MongoDB

The same APIs that serve applications also serve AI agents. RESTHeart ships a native MCP server, so any MCP-compatible client (Claude, Claude Code, Cursor, VS Code) can read and write to MongoDB directly, without custom integration code.

![RESTHeart logical architecture](docs/restheart_logic_architecture.png)

**Core capabilities:**

- **MCP Server**: Native Model Context Protocol server. Connect any MCP-compatible AI client (Claude, Claude Code, Cursor, VS Code) directly to your MongoDB data with one line of config.
- **REST API**: Full CRUD, aggregations, filtering, sorting, pagination
- **GraphQL**: Schema-driven mapping to MongoDB queries
- **WebSocket**: Real-time change streams and data sync
- **SSE**: Server-Sent Events for live dashboards, IoT feeds, and event streams
- **Metrics & Monitoring**: Built-in Prometheus-compatible metrics endpoint with a real-time browser dashboard at `/metrics-ui`, tracking request rates, latency percentiles, and HTTP status distribution
- **IoT / MQTT**: Connect devices and ingest telemetry directly into MongoDB *(coming soon)*
- **Authentication and Authorization**: JWT, OAuth2, LDAP, MongoDB-based users, ACL rules
- **Plugin system**: Extend with Java, Kotlin, JavaScript, or TypeScript for custom business logic

---
