<p>
    <a href="https://restheart.org">
        <img src="https://restheart.org/images/rh.png" width="400px" height="auto" class="image-center img-responsive" alt="RESTHeart - REST API Microservice for MongoDB"/>
    </a>
</p>

# RESTHeart - REST API Microservice for MongoDB. #

[![Java CI with Maven](https://github.com/SoftInstigate/restheart/workflows/Java%20CI%20with%20Maven/badge.svg)](https://travis-ci.org/github/SoftInstigate/restheart)
[![Maven Central](https://img.shields.io/maven-central/v/org.restheart/restheart.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22org.restheart%22%20AND%20a:%22restheart%22)
[![Docker Stars](https://img.shields.io/docker/stars/softinstigate/restheart.svg?maxAge=2592000)](https://hub.docker.com/r/softinstigate/restheart/)
[![Docker Pulls](https://img.shields.io/docker/pulls/softinstigate/restheart.svg?maxAge=2592000)](https://hub.docker.com/r/softinstigate/restheart/)
[![Join the chat at https://gitter.im/SoftInstigate/restheart](https://badges.gitter.im/SoftInstigate/restheart.svg)](https://gitter.im/SoftInstigate/restheart?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

## Table of Contents

- [Summary](#summary)
- [Project structure](#project-structure)
- [Plugins](#plugins)
- [Run with Docker](#run-with-docker)
- [Run manually](#run-manually)
- [How to Build](#how-to-Build)
    - [Integration Tests](#integration-tests)
- [Maven Dependencies](#maven-dependencies)
    - [Snapshot Builds](#snapshot-builds)
    - [Maven Site](#maven-Site)
- [Continuous Integration](#continuous-integration)
- [Full documentation](#full-documentation)
- [Book a chat](#book-a-chat)
- [Commercial Editions](#commercial-editions)

<p align="center">
   <a href="https://restheart.org">
       <img src="https://restheart.org/images/restheart-what-is-it.svg" width="800px" height="auto" class="image-center img-responsive" />
   </a>
</p>

## Summary

RESTHeart is a REST API Microservice for MongoDB.

RESTHeart connects to __MongoDB__ and opens its data to the Web. Clients, such as mobile and JavaScript apps, can access the database via a simple __API__ based on __JSON__ messages.

With RESTHeart teams can focus on building Angular, React, Vue, iOS or Android applications, because most of the server-side logic usually necessary for CRUD (Create, Read, Update, Delete) operations is automatically handled, without the need to write any code except for the client logic.

For example, to insert data in MongoDB developers model client-side JSON documents and then execute POST operations via HTTP to RESTHeart: no more need to deal with complicated server-side code and database drivers in Java, JavaScript, PHP, Ruby, Python, etc.

For these reasons, RESTHeart is widely used by freelancers, Web agencies and System Integrators with deadlines, because it allows them to focus on the most creative parts of their work.

For more ideas have a look at the list of [features](https://restheart.org/features) and the collection of common [use cases](https://restheart.org/use-cases/).

## Project structure

Starting from RESTHeart v5 we have merged all sub-projects into a single [Maven multi module project](https://maven.apache.org/guides/mini/guide-multiple-modules.html) and a single Git repository (this one).

> The v4 architecture, in fact, was split into two separate Java processes: one for managing security, identity and access management (restheart-security) and one to access the database layer (restheart). The new v5 architecture is monolithic, like it was RESTHeart v3. This decision was due to the excessive complexity of building and deploying two distinct processes and the little gains we have observed in real applications. 

Then `core` module now is just [Undertow](http://undertow.io) plus a _bootstrapper_ which reads the configuration and starts the HTTP server. The `security` module provides __Authentication__ and __Authorization__ services, while the `mongodb` module interacts with MongoDB and exposes all of its services via a REST API, as usual. Besides, we added a new `commons` module which is a shared library, including all interfaces and implementations in common among the other modules.

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

When the core module starts, it scans the Java classpath within the `plugins/` folder and loads all the JAR files there.

Plugins are annotated with the [`@RegisterPlugin`](commons/src/main/java/org/restheart/plugins/RegisterPlugin.java) Java annotation and implement the [`Service`](commons/src/main/java/org/restheart/plugins/Service.java) interface.

For example, below the [`MongoService`](mongodb/src/main/java/org/restheart/mongodb/MongoService.java) class, which provides all of MongoDB's capabilities to the `core` module.


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

- id: 'admin', password: 'secret', role: 'admin'
- id: 'user', password: 'secret', role: 'user'

The default `acl.yml` defines the following permission:

- *admin* role can execute any request
- *user* role can execute any request on collection `/{username}`

> __WARNING__ You must update the passwords! See [Configuration](#configuration) for more information on how to override the default `users.yml` configuration file.

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
- Java v11+;
- MongoDB running on `localhost` on port `27017`. Both MongoDB 3.x and 4.x will work.

For more information on how to install and run MongoDB check [install tutorial](https://docs.mongodb.com/manual/installation/#mongodb-community-edition-installation-tutorials) and [manage mongodb](https://docs.mongodb.com/manual/tutorial/manage-mongodb-processes/) on MongoDB documentation.

### Get the latest releases of restheart

Download the latest distribution from:

- [download restheart](https://github.com/SoftInstigate/restheart/releases/latest)

Un-zip or un-tar:

```bash
$ unzip restheart-<version>.zip
```

or:

```bash
$ tar -xzf restheart-<version>.tar.gz
```

Configuration files are under the folder `etc/`

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

#### Run *restheart*

```bash
$ cd restheart-<version>

$ java -jar restheart.jar etc/restheart.yml -e etc/default.properties
```

By default RESTHeart only mounts the database `restheart`. This is controlled by the `root-mongo-resource` in the `restheart/etc/default.properties` file

```properties
# The MongoDB resource to bind to the root URI /
# The format is /db[/coll[/docid]] or '*' to expose all dbs
root-mongo-resource = /restheart
```

> It means that the root resource `/` is bound to the `/restheart` database. This database doesn't actually exist until you explicitly create it by issuing a `PUT /` HTTP command.

`restheart` will start bound on HTTP port `8080`.

### Configuration

The main file is [`restheart.yml`](core/etc/restheart.yml) which is parametrized using [Mustache.java](https://github.com/spullara/mustache.java). The [`default.properties`](core/etc/default.properties) contains actual values for parameters defined into the YAML file. You pass these properties at startup, using the `-e` or `--envFile` parameter, like this:

```bash
$ java -jar restheart.jar etc/restheart.yml -e etc/default.properties
```

Beware that you must restart the core `restheart.jar` process to reload a new configuration.

Of course, you can edit the YAML configuration file or create distinct properties file, for example one for each deployment environment.

#### Environment variables

Is is possible to override any primitive type parameter in `restheart.yml` with an environment variable. Primitive types are:

 - String
 - Integer
 - Long
 - Boolean

For example, the parameter `mongo-uri` in the YAML file can be overridden by exporting a `MONGO_URI` environment variable:

```bash
$ export MONGO_URI="mongodb://127.0.0.1"
```

The following log entry appears at the very beginning of logs during the startup process:

```
[main] WARN  org.restheart.Configuration - >>> Overriding parameter 'mongo-uri' with environment value 'MONGO_URI=mongodb://127.0.0.1'
```

A shell environment variable is equivalent to a YAML parameter in restheart.yml, but it’s all uppercase and '-' (dash) are replaced with '_' (underscore).

> environment variables replacement doesn’t work with YAML structured data in configuration files, like arrays or maps. You must use properties files and mustache syntax for that.

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

> Building RESTHeart requires [Maven](http://www.oracle.com/technetwork/java/javase/downloads/index.html) and __Java 11__ or later.

Build the project with Maven:

```bash
$ mvn clean package
```

### Integration Tests

To run the integration test suite, first make sure that Docker is running. Maven starts a MongoDB volatile instance with Docker, so it is mandatory.

```bash
$ mvn verify
```

## Maven Dependencies

RESTHeart's releases are available on [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.restheart%22).

Stable releases are available at:

https://oss.sonatype.org/content/repositories/releases/org/restheart/restheart/

If you want to embed RESTHeart in your project, to compile new plugins, just add the `restheart-commons` dependency to your POM file:

```xml
<dependencies>
    <dependency>
        <groupId>org.restheart</groupId>
        <artifactId>restheart-commons</artifactId>
        <version>5.0.0-RC1</version>
    </dependency>
</dependencies>
```

### Snapshot Builds

Snapshots are available at [Sonatype](https://oss.sonatype.org/content/repositories/snapshots/org/restheart/restheart/). If you want to build your project against a development release, first add the SNAPSHOT repository:


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

Then include the SNAPSHOT dependency in your POM:

```xml
<dependencies>
    <dependency>
        <groupId>org.restheart</groupId>
        <artifactId>restheart-commons</artifactId>
        <version>5.0.0-RC1</version>
    </dependency>
</dependencies>
```

## Continuous Integration

We continually integrate and deploy development releases to Maven Central.

RESTHeart's public Docker images are automatically built and pushed to [Docker Hub](https://hub.docker.com/r/softinstigate/restheart/). The `latest` tag for Docker images refers to the most recent stable release on the `master` branch, we do not publish SNAPSHOTs as Docker images.

## Full documentation

For more information, read the [documentation](http://restheart.org/docs/).

> __WARNING__: that documentation has not yet been updated for v5, work is in progress. Please open a issue [here](https://github.com/SoftInstigate/restheart/issues) if you have any question, or send an email to <a href="mailto:ask@restheart.org">ask@restheart.org</a> and we'll be happy to clarify anything.

## Book a chat

If you have any question about RESTHeart and want to talk directly with the core development team, you can also [book a free video chat](https://calendly.com/restheart/restheart-free-chat) with us.

## Commercial Editions

RESTHeart v5 is a open source project distributed under a [open-core model](https://en.wikipedia.org/wiki/Open-core_model). It means that its main features are free to use under the OSI-approved open source licenses, but some enterprise-level features are distributed with a more business-friendly commercial license.

This is a list of commercial-only features: 

- [Transactions](https://restheart.org/docs/transactions/)
- [Change Streams](https://restheart.org/docs/change-streams)
- [JWT Authentication](https://restheart.org/docs/security/authentication/#jwt-authentication)
- [RESTHeart Authenticator](https://restheart.org/docs/security/authentication/#restheart-authenticator) with users defined in the database
- [RESTHeart Authorizer](https://restheart.org/docs/security/authorization/#restheart-authorizer) with ACL defined in the database and role-based data filter capabilities

Check the [editions matrix](https://restheart.org/editions) for more information.

> This GitHub repository will always contain open source features, we are not going to mix OSS and commercial software in the same place.

<hr></hr>

_Made with :heart: by [SoftInstigate](http://www.softinstigate.com/). Follow us on [Twitter](https://twitter.com/softinstigate)_.
