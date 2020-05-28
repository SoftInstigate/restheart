# How to Build

Building RESTHeart requires [Maven](http://www.oracle.com/technetwork/java/javase/downloads/index.html) and **Java 11** or later.

```bash
$ mvn clean package
```

After building `cd core/target` where, among other files, you'll have the structure below

```
.
├── restheart.jar
└──  plugins/
    ├── restheart-mongodb.jar
    └── restheart-security.jar
```

You can copy these files somewhere else and the [run the executable restheart.jar](#run-restheart) passing to it the path of the YAML configuration file.

Have a look at [`core/etc/restheart.yml`](core/etc/restheart.yml) and [`core/etc/default.properties`](core/etc/default.properties) for more.

### Integration Tests

To run the integration test suite, first make sure that Docker is running. Maven starts a MongoDB volatile instance with Docker, so it is mandatory.

```bash
$ mvn verify
```

## Maven Dependencies

RESTHeart's releases are available on [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.restheart%22).

Stable releases are available at: https://oss.sonatype.org/content/repositories/releases/org/restheart/restheart/

---

The main difference with the past is that in RESTHeart v5 for a developer is just enough to compile against the `restheart-commons` library to create a plugin, which is a JAR file to be copied into the `plugins/` folder and class-loaded during startup.

---

To compile new plugins, add the `restheart-commons` dependency to your POM file:

```xml
<dependencies>
    <dependency>
        <groupId>org.restheart</groupId>
        <artifactId>restheart-commons</artifactId>
        <version>5.1.0</version>
    </dependency>
</dependencies>
```

**IMPORTANT**: The `restheart-commons` artifact in the `commons` module has been released using the Apache v2 license instead of the AGPL v3. This is much like MongoDB is doing with the Java driver. It implies **your projects does not incur in the AGPL restrictions when extending RESTHeart with plugins**.

## Continuous Integration

We continuously integrate and deploy development releases to Maven Central. RESTHeart's public Docker images are automatically built and pushed to [Docker Hub](https://hub.docker.com/r/softinstigate/restheart/). The `latest` tag for Docker images refers to the most recent stable release on the `master` branch, **we don't publish SNAPSHOTs as Docker images**.

## Project structure

Starting from RESTHeart v5 we have merged all sub-projects into a single [Maven multi module project](https://maven.apache.org/guides/mini/guide-multiple-modules.html) and a single Git repository (this one).

> The v4 architecture, in fact, was split into two separate Java processes: one for managing security, identity and access management (restheart-security) and one to access the database layer (restheart). The new v5 architecture is monolithic, like it was RESTHeart v3. This decision was due to the excessive complexity of building and deploying two distinct processes and the little gains we have observed in real applications.

Then `core` module now is just [Undertow](http://undertow.io) plus a _bootstrapper_ which reads the configuration and starts the HTTP server. The `security` module provides **Authentication** and **Authorization** services, while the `mongodb` module interacts with MongoDB and exposes all of its services via a REST API, as usual. Besides, we added a new `commons` module which is a shared library, including all interfaces and implementations in common among the other modules.

```
.
├── commons
├── core
├── mongodb
└── security
```

## Plugins

Except for the `core` services, everything else is a plugin. The `security` and `mongodb` modules are just JAR files which are copied into the `plugins/` folder within the root folder, where the `restheart.jar` core and `etc/` folder are.

```
.
├── etc/
│   ├── acl.yml
│   ├── default.properties
│   ├── restheart.yml
│   └── users.yml
├── plugins/
│   ├── restheart-mongodb.jar
│   └── restheart-security.jar
└── restheart.jar
```

---

Plugin examples are collected [here](https://github.com/SoftInstigate/restheart-examples).

---

When the core module starts, it scans the Java classpath within the `plugins/` folder and loads all the JAR files there.

Plugins are annotated with the [`@RegisterPlugin`](commons/src/main/java/org/restheart/plugins/RegisterPlugin.java) and implement an Interface. 

Several types of Plugin exist to extends RESTHeart. For more information refer to [Plugins overview](https://restheart.org/docs/plugins/overview/) in the documentation.

For example, below the [`MongoService`](mongodb/src/main/java/org/restheart/mongodb/MongoService.java) class implementing the [`Service`](commons/src/main/java/org/restheart/plugins/Service.java) interface, which provides all of MongoDB's capabilities to the `core` module:

```java
@RegisterPlugin(name = "mongo",
        description = "handles requests to mongodb resources",
        enabledByDefault = true,
        defaultURI = "/",
        dontIntercept = {InterceptPoint.REQUEST_AFTER_AUTH},
        priority = Integer.MIN_VALUE)
public class MongoService implements Service<BsonRequest, BsonResponse> {
    ...
}
```