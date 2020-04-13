<p>
    <a href="https://restheart.org">
        <img src="https://restheart.org/images/rh.png" width="400px" height="auto" class="image-center img-responsive" alt="RESTHeart - REST API Microservice for MongoDB"/>
    </a>
</p>

# RESTHeart - REST API Microservice for MongoDB.

[![GitHub last commit](https://img.shields.io/github/last-commit/softinstigate/restheart)](https://github.com/SoftInstigate/restheart/commits/master)
[![Build](https://github.com/SoftInstigate/restheart/workflows/Build/badge.svg)](https://github.com/SoftInstigate/restheart/actions?query=workflow%3A%22Build%22)
[![Github stars](https://img.shields.io/github/stars/SoftInstigate/restheart?label=Github%20Stars)](https://github.com/SoftInstigate/restheart)
[![Maven Central](https://img.shields.io/maven-central/v/org.restheart/restheart.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22org.restheart%22%20AND%20a:%22restheart%22)
[![Docker Pulls](https://img.shields.io/docker/pulls/softinstigate/restheart.svg?maxAge=2592000)](https://hub.docker.com/r/softinstigate/restheart/)
[![Join the chat at https://gitter.im/SoftInstigate/restheart](https://badges.gitter.im/SoftInstigate/restheart.svg)](https://gitter.im/SoftInstigate/restheart?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
<!-- [![Docker Stars](https://img.shields.io/docker/stars/softinstigate/restheart.svg?maxAge=2592000)](https://hub.docker.com/r/softinstigate/restheart/) -->

## Table of Contents

-   [Summary](#summary)
-   [Run with Docker](#run-with-docker)
-   [Run manually](#run-manually)
-   [How to Build](#how-to-Build)
    -   [Integration Tests](#integration-tests)
-   [Maven Dependencies](#maven-dependencies)
    -   [Snapshot Builds](#snapshot-builds)
    -   [Maven Site](#maven-Site)
-   [Continuous Integration](#continuous-integration)
-   [Project structure](#project-structure)
-   [Plugins](#plugins)
-   [Full documentation](#full-documentation)
-   [Book a chat](#book-a-chat)
-   [Commercial Editions](#commercial-editions)

<p align="center">
   <a href="https://restheart.org">
       <img src="https://restheart.org/images/restheart-what-is-it.svg" width="800px" height="auto" class="image-center img-responsive" />
   </a>
</p>

## Summary

There are some features every developer in the world always need to implement:

1. Data persistence.
1. Authentication and Authorization.
1. API.

RESTHeart provides out-of-the-box:

1. Data persistence via MongoDB.
1. Secure Identity and Access Management.
1. REST API with JSON messages.

RESTHeart is a __REST API Microservice for MongoDB__ which provides server-side Data, Identity and Access Management for Web and Mobile applications.

RESTHeart is:

1. A Stateless Microservice.
1. Designed to be distributed as a Docker container.
1. Easily deployable both on cloud and on-premises.

With RESTHeart teams can focus on building Angular, React, Vue, iOS or Android applications, because most of the server-side logic necessary to manage database operations, authentication / authorization and related APIs is automatically handled, __without the need to write any server-side code__ except for the UX/UI.

> For example, to insert data into MongoDB a developer has to just create client-side JSON documents and then execute POST operations via HTTP to RESTHeart: no more need to deal with complicated server-side coding and database drivers in Java, JavaScript, PHP, Ruby, Python, etc.

For these reasons, RESTHeart is widely used by freelancers, Web agencies and System Integrators with deadlines, because it allows them to focus on the most important and creative part of their work: the User Experience.

For more ideas have a look at the list of [features](https://restheart.org/features) and the collection of common [use cases](https://restheart.org/use-cases/).

## Run with Docker

### Prerequisites

You need Docker v1.13 and later.

Can't use Docker? Check [Run without Docker](#run-manually)

### Run the full stack

This runs both RESTHeart and MongoDB using `docker-compose`

```bash
$ curl https://raw.githubusercontent.com/SoftInstigate/restheart/master/docker-compose.yml --output docker-compose.yml

$ docker-compose up -d --no-build
```

### Default users and ACL

The default `users.yml` defines the following users:

-   id: 'admin', password: 'secret', role: 'admin'
-   id: 'user', password: 'secret', role: 'user'

The default `acl.yml` defines the following permission:

-   _admin_ role can execute any request
-   _user_ role can execute any request on collection `/{username}`

> **WARNING** You must update the passwords in production! See [Configuration](#configuration) for more information on how to override the default `users.yml` configuration file.

### Check that everything works

```bash
# create database 'restheart'
$ curl --user admin:secret -I -X PUT :8080/
HTTP/1.1 201 OK

# create collection 'restheart.collection'
$ curl --user admin:secret -I -X PUT :8080/collection
HTTP/1.1 201 OK

# create a couple of documents
$ curl --user admin:secret -X POST :8080/collection -d '{"a":1}' -H "Content-Type: application/json"
$ curl --user admin:secret -X POST :8080/collection -d '{"a":2}' -H "Content-Type: application/json"

# get documents
$ curl --user admin:secret :8080/collection
[{"_id":{"$oid":"5dd3cfb2fe3c18a7834121d3"},"a":1,"_etag":{"$oid":"5dd3cfb2439f805aea9d5130"}},{"_id":{"$oid":"5dd3cfb0fe3c18a7834121d1"},"a":2,"_etag":{"$oid":"5dd3cfb0439f805aea9d512f"}}]%
```

## Run manually

### Prerequisites

You need:

-   Java v11+;
-   MongoDB running on `localhost` on port `27017`. Both MongoDB 3.x and 4.x work.

For more information on how to install and run MongoDB check [install tutorial](https://docs.mongodb.com/manual/installation/#mongodb-community-edition-installation-tutorials) and [manage mongodb](https://docs.mongodb.com/manual/tutorial/manage-mongodb-processes/) on MongoDB documentation.

### Get the latest releases of RESTHeart

Two options:

1. [Build the source code](#how-to-Build)
1. [Download the latest release](https://github.com/SoftInstigate/restheart/releases/tag/5.0.0-RC2);

If you choose to download either the zip or tar archive:

Un-zip

```bash
$ unzip restheart.zip
```

Or un-tar

```bash
$ tar -xzf restheart.tar.gz
```

Configuration files are under the `etc/` folder.

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

#### Run RESTHeart

```bash
$ cd restheart

$ java -jar restheart.jar etc/restheart.yml -e etc/default.properties
```

By default RESTHeart only mounts the database `restheart`. This is controlled by the `root-mongo-resource` in the `restheart/etc/default.properties` file

```properties
# The MongoDB resource to bind to the root URI /
# The format is /db[/coll[/docid]] or '*' to expose all dbs
root-mongo-resource = /restheart
```

> It means that the root resource `/` is bound to the `/restheart` database. This database doesn't actually exist until you explicitly create it by issuing a `PUT /` HTTP command.

RESTHeart will start bound on HTTP port `8080`.

### Configuration

The main file is [`restheart.yml`](core/etc/restheart.yml) which is parametrized using [Mustache.java](https://github.com/spullara/mustache.java). The [`default.properties`](core/etc/default.properties) contains actual values for parameters defined into the YAML file. You pass these properties at startup, using the `-e` or `--envFile` parameter, like this:

```bash
$ java -jar restheart.jar etc/restheart.yml -e etc/default.properties
```

Beware that you must restart the core `restheart.jar` process to reload a new configuration (of course, how to stop and start the process depends on how it was distributed: either in a docker container or a native Java process).

You can edit the YAML configuration file or create distinct properties file. Usually one set of properties for each deployment environment is a common practice.

#### Environment variables

Is is possible to override any primitive type parameter in `restheart.yml` with an environment variable. Primitive types are:

-   String
-   Integer
-   Long
-   Boolean

For example, the parameter `mongo-uri` in the YAML file can be overridden by exporting a `MONGO_URI` environment variable:

```bash
$ export MONGO_URI="mongodb://127.0.0.1"
```

> Have a look at the [docker-compose.yml](docker-compose.yml) file for an example of how to export an environment variable if using Docker.

The following log entry appears at the very beginning of logs during the startup process:

```
[main] WARN  org.restheart.Configuration - >>> Overriding parameter 'mongo-uri' with environment value 'MONGO_URI=mongodb://127.0.0.1'
```

A shell environment variable is equivalent to a YAML parameter in `restheart.yml`, but it’s all uppercase and '-' (dash) are replaced with '\_' (underscore).

> Environment variables replacement works only with primitive types: it doesn’t work with YAML structured data in configuration files, like arrays or maps. It's mandatory to use properties files and mustache syntax for that.

To know the available CLI parameters, run RESTHeart with `--help`:

```bash
$ java -jar restheart.jar --help

Usage: java -Dfile.encoding=UTF-8 -jar -server restheart.jar [options]
      <Configuration file>
  Options:
    --envFile, --envfile, -e
      Environment file name
    --fork
      Fork the process
      Default: false
    --help, -?
      This help message
```

## How to Build

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

> INFO: the main difference with the past is that in RESTHeart v5 a developer doesn't build a custom version of `restheart.jar` to create extensions anymore (it was usually done with the maven-shade-plugin), instead it's enough to compile against the `restheart-commons` library to create a plugin, which is a JAR file to be copied into the `plugins/` folder and class-loaded during startup.

To compile new plugins, add the `restheart-commons` dependency to your POM file:

```xml
<dependencies>
    <dependency>
        <groupId>org.restheart</groupId>
        <artifactId>restheart-commons</artifactId>
        <version>5.0.0-RC3</version>
    </dependency>
</dependencies>
```

**IMPORTANT**: The `restheart-commons` artifact in the `commons` module has been released using the Apache v2 license instead of the AGPL v3. This is much like MongoDB is doing with the Java driver. It implies **your projects does not incur in the AGPL restrictions when extending RESTHeart with plugins**.

### Snapshot Builds

Snapshot builds are available at [Sonatype](https://oss.sonatype.org/content/repositories/snapshots/org/restheart/restheart/). If you want to build your project against a development release, first add the SNAPSHOT repository:

```xml
<repositories>
    <repository>
        <id>restheart-mvn-repo</id>
        <url>https://oss.sonatype.org/content/repositories/snapshots</url>
        <snapshots>
            <enabled>true</enabled>
            <updatePolicy>always</updatePolicy>
        </snapshots>
    </repository>
</repositories>
```

Then include the desired SNAPSHOT dependency version in your POM, for example:

```xml
<dependencies>
    <dependency>
        <groupId>org.restheart</groupId>
        <artifactId>restheart-commons</artifactId>
        <version>5.0.1-SNAPSHOT</version>
    </dependency>
</dependencies>
```

However, as you should know how Maven works, the recommended way do deal with development snapshots is to build and install the code by yourself.

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

> Plugin examples are collected [here](https://github.com/SoftInstigate/restheart-examples).

When the core module starts, it scans the Java classpath within the `plugins/` folder and loads all the JAR files there.

Plugins are annotated with the [`@RegisterPlugin`](commons/src/main/java/org/restheart/plugins/RegisterPlugin.java) and implement an Interface. 

Several types of Plugin exist to extends RESTHeart. For more information refer to [Plugins overview](https://restheart.org/docs/plugins/overview/) in the documentation.

For example, below the [`MongoService`](mongodb/src/main/java/org/restheart/mongodb/MongoService.java) class implementing the [`Service`](commons/src/main/java/org/restheart/plugins/Service.java) interface, which provides all of MongoDB's capabilities to the `core` module:

```java
@RegisterPlugin(name = "mongo",
        description = "handles request to mongodb resources",
        enabledByDefault = true,
        defaultURI = "/",
        priority = Integer.MIN_VALUE)
public class MongoService implements Service {
    ...
}
```

## Full documentation

For more information, read the [documentation](http://restheart.org/docs/).

> **WARNING**: that documentation has not yet been updated for v5, work is in progress. Please open a issue [here](https://github.com/SoftInstigate/restheart/issues) if you have any question, or send an email to <a href="mailto:ask@restheart.org">ask@restheart.org</a> and we'll be happy to clarify anything.

## Book a chat

If you have any question about RESTHeart and want to talk directly with the core development team, you can also [book a free video chat](https://calendly.com/restheart/restheart-free-chat) with us.

## Commercial Editions

RESTHeart v5 is a open source project distributed under a [open-core model](https://en.wikipedia.org/wiki/Open-core_model). It means that its main features are free to use under the OSI-approved open source licenses, but some enterprise-level features are distributed with a business-friendly commercial license. We hope this model will allow us to finance the continuous development and improvement of this product, which has become too large to be handled like a side project.

This is a list of commercial-only features:

-   [Transactions](https://restheart.org/docs/transactions/)
-   [Change Streams](https://restheart.org/docs/change-streams)
-   [JWT Authentication](https://restheart.org/docs/security/authentication/#jwt-authentication)
-   [RESTHeart Authenticator](https://restheart.org/docs/security/authentication/#restheart-authenticator) with users defined in the database
-   [RESTHeart Authorizer](https://restheart.org/docs/security/authorization/#restheart-authorizer) with ACL defined in the database and role-based data filter capabilities

Check the [editions matrix](https://restheart.org/editions) for more information.

<hr></hr>

_Made with :heart: by [SoftInstigate](http://www.softinstigate.com/). Follow us on [Twitter](https://twitter.com/softinstigate)_.
