# RESTHeart AI Coding Agent Instructions

## Project Overview
RESTHeart is a Java backend framework for rapid development of REST, GraphQL, and WebSocket APIs with MongoDB. It's a multi-module Maven project using Java 25, built on Undertow, with pluggable architecture for extensibility.

## Architecture & Module Structure

**Multi-Module Maven Layout:**
- `commons/` - Plugin APIs (Apache 2.0 licensed) for developing extensions
- `mongoclient/` - MongoDB client wrapper
- `security/` - Authentication and authorization plugins
- `mongodb/` - MongoDB REST/WebSocket API capabilities
- `graphql/` - GraphQL API support
- `polyglot/` - GraalVM polyglot plugin support (JavaScript, etc.)
- `metrics/` - Metrics collection and reporting
- `core/` - Runtime process, plugin registry, request routing, security enforcement
- `test-plugins/` - Test plugin implementations
- `examples/` - Diverse plugin examples (Java, Kotlin, JavaScript)

**Core builds to:** `core/target/restheart.jar` (thin) or with `-P shade` profile (fat JAR)

## Plugin Architecture (Critical)

RESTHeart's entire extensibility model is based on plugins annotated with `@RegisterPlugin`:

**Plugin Types:**
1. **Services** - Handle HTTP requests (e.g., `JsonService`, `ByteArrayService`, `BsonService`)
2. **Interceptors** - Modify requests/responses at specific intercept points
3. **Providers** - Dependency injection sources (use `@Inject("provider-name")`)
4. **Initializers** - Startup tasks (set `initPoint` to `BEFORE_STARTUP` or `AFTER_STARTUP`)
5. **Authenticators** - Verify credentials (e.g., `mongoRealmAuthenticator`)
6. **Authorizers** - Control access (e.g., `mongoAclAuthorizer`)
7. **Token Managers** - Generate/verify auth tokens

**Example Service:**
```java
@RegisterPlugin(name = "myService", description = "...", defaultURI = "/myapi")
public class MyService implements JsonService {
    @Inject("mclient")
    private MongoClient mclient;
    
    @Override
    public void handle(JsonRequest req, JsonResponse res) { ... }
}
```

**Key Annotations:**
- `@RegisterPlugin` - Required on all plugins (name, description, secure, blocking, etc.)
- `@Inject("provider-name")` - Inject dependencies like `mclient`, `rh-config`, `registry`
- Common providers: `mclient` (MongoClient), `rh-config` (Configuration), `registry` (PluginsRegistry)

**Plugin Discovery:** Plugins are discovered via classpath scanning for classes annotated with `@RegisterPlugin`. Place JARs in `plugins/` directory or build as part of project.

## Build & Test Workflows

**Build Commands:**
```bash
# Build thin JAR
./mvnw clean package

# Build fat JAR with all dependencies
./mvnw clean package -P shade

# Run integration tests (starts MongoDB container)
./mvnw clean verify

# Skip integration tests
./mvnw clean verify -DskipITs

# Test with specific MongoDB version
./mvnw clean verify -Dmongodb.version="8.0"

# Skip updating license headers (faster builds)
./mvnw clean package -DskipUpdateLicense=true
```

**Running RESTHeart:**
```bash
# Standard run
java -jar core/target/restheart.jar

# With custom MongoDB connection
RHO='/mclient/connection-string->"mongodb://host:27017"' java -jar core/target/restheart.jar

# Standalone mode (no MongoDB required)
java -jar core/target/restheart.jar -s
```

**Docker Development:**
```bash
# Run with custom plugins
docker run --rm -p 8080:8080 \
  -e RHO="/fileRealmAuthenticator/users[userid='admin']/password->'secret';/http-listener/host->'0.0.0.0'" \
  -v ./target:/opt/restheart/plugins/custom \
  softinstigate/restheart:latest
```

## Configuration System

**Default config:** `core/src/main/resources/restheart-default-config.yml`

**Override with RHO env variable:**
```bash
# Override connection string
RHO='/mclient/connection-string->"mongodb://newhost"'

# Override multiple values (semicolon-separated)
RHO='/http-listener/port->9090;/mclient/connection-string->"mongodb://host"'
```

**Key config sections:**
- `mclient` - MongoDB connection string
- `mongo` - REST API settings, mongo-mounts, caching, limits
- `graphql` - GraphQL API configuration
- Authentication mechanisms: `basicAuthMechanism`, `jwtAuthenticationMechanism`, etc.
- Authorizers: `mongoAclAuthorizer`, `fileAclAuthorizer`
- `logging` - Log levels, file output, request logging modes (0=none, 1=light, 2=detailed)

## Request/Response Types

Services must match Request/Response types:

- **JsonService** → `JsonRequest`/`JsonResponse`
- **BsonService** → `BsonRequest`/`BsonResponse`
- **ByteArrayService** → `ByteArrayRequest`/`ByteArrayResponse`
- **GraphQLService** → `GraphQLRequest`/`GraphQLResponse`
- **MongoService** → `MongoRequest`/`MongoResponse`

Interceptors must match the request/response types of services they intercept, or use `WildcardInterceptor` for all services.

## GraalVM Native Image

**Build native image:**
```bash
# Local OS
./mvnw clean package -Pnative -DskipTests

# Linux (in Docker)
docker run -it --rm \
  -v "$PWD":/opt/app \
  -v "$HOME"/.m2:/root/.m2 \
  softinstigate/graalvm-maven \
  clean package -Pnative -DskipTests -Dnative.gc="--gc=G1"
```

**Native image config:** `core/src/main/resources/META-INF/native-image/org.restheart/restheart/native-image.properties`

**Requirements:** GraalVM >= 25-graal (`sdk install java 25-graalce`)

## Version Management

**Set version:**
```bash
./setversion.sh 9.0.0          # Release version
./setversion.sh 9.0.1-SNAPSHOT # Development version
./setversion.sh 9.0.0 --dry-run # Preview changes
```

This updates all `pom.xml` files and creates git commit/tag if not a SNAPSHOT.

## Testing Conventions

- **Unit tests:** `*Test.java` - Run with `./mvnw test` or `-DskipUTs`
- **Integration tests:** `*IT.java` - Run with `./mvnw verify` or skip with `-DskipITs`
- Integration tests require MongoDB (started via Testcontainers by default)
- Test with FerretDB: Use `-P-mongodb -Dtest-connection-string=...` 
- Skip replica-set tests: Add `-Dkarate.options="--tags ~@requires-replica-set"`

## Plugin Development Examples

**Location:** `examples/` directory contains 15+ working examples

**Key examples:**
- `greeter-service/` - Basic JsonService and ByteArrayService
- `mongo-status-service/` - Injecting MongoClient
- `csv-interceptor/` - Request/response modification
- `user-signup/` - Complex flow with interceptors, validation, security
- `instance-name/` - WildcardInterceptor usage
- `js-plugin/` - JavaScript/GraalVM polyglot plugins

**Build example:** `cd examples/example-name && ../mvnw clean package`

## Common Patterns

**Dependency Injection:**
```java
@Inject("mclient") private MongoClient mclient;
@Inject("rh-config") private Configuration config;
@Inject("registry") private PluginsRegistry registry;
```

**Interceptor Points:**
- `REQUEST_BEFORE_AUTH` - Before authentication
- `REQUEST_AFTER_AUTH` - After authentication
- `RESPONSE` - Before sending response
- `RESPONSE_ASYNC` - Async response processing

**Service Blocking:**
- `@RegisterPlugin(blocking=true)` - Runs on worker thread pool
- `@RegisterPlugin(blocking=false)` - Runs on I/O thread (default, for async/non-blocking)

## Security Model

**Default setup:**
- Admin user: `admin` / `secret` (bcrypt hashed in config)
- Users stored in: `restheart.users` collection
- ACL stored in: `restheart.acl` collection
- Root role: `admin` (can execute any request)

**Auth flow:** Auth mechanism → Authenticator → Authorizer → Service

## Development Tips

- Plugin JARs go in `plugins/` directory (or `core/target/plugins` for testing)
- Use `./mvnw` wrapper (no Maven install needed)
- Java 25 required (`sdk install java 25-tem`)
- Main class: `org.restheart.Bootstrapper`
- Check plugin registration: Watch logs for "Registered plugin: ..." messages
- Request logging: Set `logging.requests-log-mode: 2` for detailed dumps (dev only)
- Metrics registries are created lazily on first matching request (empty response before traffic occurs)

## Documentation References

- Plugin API JavaDoc: https://javadoc.io/doc/org.restheart/restheart-commons
- Official docs: https://restheart.org/docs
- Docker setup: https://restheart.org/docs/setup-with-docker
- Plugin development: https://restheart.org/docs/plugins/overview
