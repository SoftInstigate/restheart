# Contributing to RESTHeart

Thank you for your interest in contributing to RESTHeart! This guide covers everything you need to know to build, test, and submit changes to the project.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Building and Testing](#building-and-testing)
3. [Adding a New Module](#adding-a-new-module)
4. [Documenting a New Module or Feature](#documenting-a-new-module-or-feature)
5. [Code Style](#code-style)
6. [Pull Request Process](#pull-request-process)
7. [Release Process](#release-process)

---

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Java | 25 | Use [SDKMAN](https://sdkman.io/): `sdk install java 25-tem` |
| Maven | via wrapper | No separate installation needed — use `./mvnw` |
| Docker | any recent | Required only for integration tests, which start a MongoDB container |
| GraalVM | 25-graal | Required only for native-image builds: `sdk install java 25-graalce` |

The project uses the Maven Wrapper (`./mvnw` / `mvnw.cmd`), so you do **not** need a global Maven installation.

---

## Building and Testing

### Standard build (no tests)

```bash
./mvnw clean package
```

The main artifact is produced at `core/target/restheart.jar`.

### Build a fat JAR (all dependencies bundled)

```bash
./mvnw clean package -P shade
```

### Run all tests (unit + integration)

```bash
./mvnw clean verify
```

The integration tests need a MongoDB instance and a running RESTHeart, and `verify` provides both automatically — two profiles activate whenever `-DskipTests` is *not* passed:

- **`mongodb`** starts a `mongo:${mongodb.version}` container (fabric8 `docker-maven-plugin`) in `pre-integration-test` and stops it afterwards. Docker must be running. If you already have MongoDB on `localhost:27017`, disable it with `-P-mongodb`.
- **`start-server`** builds and starts RESTHeart before the tests and stops it after.

All integration tests live in the `core` module.

### Skip integration tests

```bash
./mvnw clean verify -DskipITs
```

### Skip unit tests

```bash
./mvnw clean verify -DskipUTs
```

### Skip all tests

```bash
./mvnw clean verify -DskipTests
```

### Run a single unit test class

```bash
./mvnw test -Dtest=MyClassName
```

### Run a single integration test class

```bash
./mvnw verify -Dit.test=MyClassIT
```

Alternatively, select them by file pattern with `it.includes` (default `**/*IT.java`):

```bash
./mvnw verify -Dit.includes=**/RunnerIT.java
```

### Run only the Karate suite

The BDD tests under `core/src/test/java/karate/` are all driven by a single JUnit class, `RunnerIT`. Excluding the other integration test classes cuts the cycle down considerably while working on a feature file:

```bash
./mvnw clean verify -Dit.includes=**/RunnerIT.java
```

### Run specific Karate features

`RunnerIT` runs `classpath:karate` by default. Point it somewhere narrower with `karate.path`, which accepts a comma-separated list of feature files or directories:

```bash
# a single feature
./mvnw clean verify -Dit.includes=**/RunnerIT.java \
  -Dkarate.path=classpath:karate/stripe/subscription-acl-variable.feature

# a whole directory, or several
./mvnw clean verify -Dit.includes=**/RunnerIT.java \
  -Dkarate.path=classpath:karate/stripe,classpath:karate/accounts
```

Note that features are not fully independent: some rely on data created by others, and helpers under `karate/accounts/helpers/` are called explicitly by the features that need them. A feature that passes in the full suite can fail when run alone — that is usually a missing fixture, not a regression.

Karate writes an HTML report to `core/target/karate-reports/karate-summary.html`, and the server log for the run is `core/restheart.log` (rotated at 5 MB into `core/restheart.log-N.log.zip`, so a long run's earlier output ends up in those archives).

### Re-run tests without rebuilding

Each `verify` rebuilds, then starts MongoDB and RESTHeart, runs the tests, and shuts both down. When iterating on a feature file that whole cycle is mostly waste.

First, drop `clean` — the build is incremental, so unchanged sources are not recompiled:

```bash
./mvnw verify -o -DskipUTs -DskipUpdateLicense=true -Dit.includes=**/RunnerIT.java
```

To skip the rebuild entirely, keep MongoDB and RESTHeart running between runs and tell Maven not to manage them with `-DskipTestEnv=true`.

Start the environment once:

```bash
docker run -d --rm --name rh-mongo -p 27017:27017 mongo:8.3 --bind_ip_all --replSet rs0
docker exec rh-mongo mongosh --eval 'rs.initiate()'

cd core && bin/start.sh -o src/test/resources/etc/conf-overrides.yml --fork
```

Then re-run the tests as many times as needed, invoking failsafe directly so nothing before `integration-test` executes:

```bash
./mvnw -pl core failsafe:integration-test failsafe:verify \
  -DskipTestEnv=true \
  -Dit.includes=**/RunnerIT.java \
  -Dkarate.path=classpath:karate/stripe/subscription-acl-variable.feature
```

Two caveats. Feature files and `conf-overrides.yml` are test *resources*, so after editing them run `./mvnw -pl core process-test-resources` to copy them into `target/test-classes` — failsafe reads them from there, not from `src`. And changes to Java under `commons/`, `security/` or any plugin module do **not** reach the running server, which is executing the already-built `core/target/restheart.jar`: those need a real build and a server restart.

Stop the environment with `core/bin/stop.sh` and `docker stop rh-mongo`.

### Test against a specific MongoDB version

```bash
./mvnw clean verify -Dmongodb.version="7.0"
```

Any published `mongo` image tag works; the default is set by the `mongodb.version` property in the root POM.

### Skip updating license headers (faster iteration)

```bash
./mvnw clean package -DskipUpdateLicense=true
```

---

## Adding a New Module

### 1. Register the module in the root POM

Add your module to the default `<modules>` section in the root `pom.xml`:

```xml
<modules>
    <module>test-plugins</module>
    <module>commons</module>
    <module>mongoclient</module>
    <module>security</module>
    <module>mongodb</module>
    <module>graphql</module>
    <module>polyglot</module>
    <module>metrics</module>
    <module>your-module</module>   <!-- ← add here -->
    <module>core</module>          <!-- core must remain last -->
</modules>
```

### 2. Declare the parent POM in your module

```xml
<parent>
    <groupId>org.restheart</groupId>
    <artifactId>restheart-parent</artifactId>
    <version>${revision}</version>
    <relativePath>../pom.xml</relativePath>
</parent>
```

### 3. Decide whether the module is bundled or optional

| Scenario | Action |
|----------|--------|
| **Bundled** — always loaded at runtime | Add it as a dependency of `core/pom.xml` |
| **Optional plugin** — loaded only when the JAR is placed in `plugins/` | Do **not** add it to `core/pom.xml`; document the manual installation step in the module's `README.md` |

### 4. Native-image profile (if applicable)

If your module must be included in the GraalVM native binary, add it to the `native` profile inside `core/pom.xml`:

```xml
<profile>
    <id>native</id>
    <dependencies>
        <dependency>
            <groupId>org.restheart</groupId>
            <artifactId>restheart-your-module</artifactId>
        </dependency>
    </dependencies>
</profile>
```

Also provide the necessary GraalVM reflection/resource configuration files under:

```
your-module/src/main/resources/META-INF/native-image/org.restheart/your-module/
```

---

## Documenting a New Module or Feature

### Module README

Every module **must** have a `README.md` at its root. Follow the style of `core/README.md` or `mongodb/README.md`:

- One-paragraph description of what the module does
- Any non-obvious configuration or deployment steps
- Links to the relevant section of the official docs at `restheart.org/docs`

### Plugin examples

Working end-to-end examples belong in the `examples/` directory. Use `examples/sse-clock/` as a template — it shows the minimal structure:

```
examples/your-example/
├── README.md        # build, run, test, and "how it works" sections
├── pom.xml
└── src/
```

### Official documentation site

Full feature documentation must be submitted as a pull request to the separate
[restheart.org](https://github.com/SoftInstigate/restheart.org) repository.
The `docs/` directory in *this* repository is for architecture diagrams and
supplementary in-repo references only.

### Release notes

Any user-visible change (new feature, breaking change, deprecation) requires a
release-notes entry. Add a brief summary to the GitHub release draft before
merging your PR.

---

## Code Style

### License headers

All Java source files must carry the appropriate license header. To add missing
headers automatically, run:

```bash
./mvnw process-sources
```

or equivalently:

```bash
./mvnw process-sources -DskipUpdateLicense=false
```

The `commons` module is licensed under **Apache 2.0**; all other modules are
licensed under **AGPL v3**. The license plugin reads the correct header from
`src/license` inside each module — do not change the header manually.

### Java version and virtual threads

The project targets **Java 25**. Use modern language features freely:

- Prefer **virtual threads** for blocking or I/O-bound work:
  ```java
  Thread.ofVirtual().start(() -> { /* blocking work */ });
  ```
- Use records, sealed classes, pattern matching, and text blocks where they
  improve readability.

### Javadoc `@author` tag

Add an `@author` tag to every new public class you introduce:

```java
/**
 * Brief description of the class.
 *
 * @author Your Name {@literal <you@example.com>}
 */
```

---

## Pull Request Process

### Branch naming

Use a short, descriptive branch name prefixed by the type of change:

| Prefix | Use for |
|--------|---------|
| `feat/` | New features |
| `fix/` | Bug fixes |
| `docs/` | Documentation-only changes |
| `refactor/` | Refactoring without behaviour change |
| `chore/` | Build, CI, or tooling changes |

Example: `feat/websocket-compression`

### Commit messages

Follow the [Conventional Commits](https://www.conventionalcommits.org/) style:

```
<type>(<scope>): <short summary>

[optional body]

[optional footer: Closes #<issue>]
```

Examples:

```
feat(mongodb): add $lookup aggregation support
fix(security): correct JWT expiry validation
docs(contributing): add release process section
```

### PR description

Your pull request description should include:

1. **What** — a clear summary of the change
2. **Why** — the motivation or the issue it addresses
3. **How** — any non-obvious implementation decisions
4. **Testing** — how you verified the change
5. **Documentation** — links to any docs-site PR or in-repo README updates

If the change adds a user-visible feature, include (or link to) the documentation
content that should be published on `restheart.org/docs`.

Reference the related issue with `Closes #<number>` in the PR body so GitHub
closes it automatically on merge.
