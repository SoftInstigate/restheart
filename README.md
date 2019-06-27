<p align="center">
    <a href="https://restheart.org">
        <img src="https://restheart.org/images/restheart.png" width="60%" height="auto" class="image-center img-responsive" alt="RESTHeart - Web API Server for MongoDB"/>
    </a>
</p>

<p align="center">
RESTHeart - Web API Server for MongoDB.
</p>

[![Build Status](https://travis-ci.org/SoftInstigate/restheart.svg?branch=master)](https://travis-ci.org/SoftInstigate/restheart)
[![Maven Central](https://img.shields.io/maven-central/v/org.restheart/restheart.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22org.restheart%22%20AND%20a:%22restheart%22)
[![Docker Stars](https://img.shields.io/docker/stars/softinstigate/restheart.svg?maxAge=2592000)](https://hub.docker.com/r/softinstigate/restheart/)
[![Docker Pulls](https://img.shields.io/docker/pulls/softinstigate/restheart.svg?maxAge=2592000)](https://hub.docker.com/r/softinstigate/restheart/)
[![Join the chat at https://gitter.im/SoftInstigate/restheart](https://badges.gitter.im/SoftInstigate/restheart.svg)](https://gitter.im/SoftInstigate/restheart?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

## Table of Contents

- [Table of Contents](#Table-of-Contents)
- [Summary](#Summary)
- [Setup](#Setup)
- [Configuration](#Configuration)
- [Security](#Security)
- [How to Build](#How-to-Build)
- [Integration Tests](#Integration-Tests)
- [Maven Dependencies](#Maven-Dependencies)
  - [Snapshot Builds](#Snapshot-Builds)
  - [Maven Site](#Maven-Site)
- [Continuous Integration](#Continuous-Integration)

<p align="center">
   <a href="https://restheart.org">
       <img src="https://restheart.org/images/restheart-what-is-it.svg" width="80%" height="auto" class="image-center img-responsive" />
   </a>
</p>

## Summary

**RESTHeart** connects to **MongoDB** and opens data to the Web. Clients such as mobile and javascript apps can use the database via a simple **RESTful API**.

> For more information, visit RESTHeart's [website](http://restheart.org) and [documentation](http://restheart.org/docs/).

## Setup

Download the latest release, then [install](https://docs.mongodb.com/manual/installation/#mongodb-community-edition-installation-tutorials) and [run](https://docs.mongodb.com/manual/tutorial/manage-mongodb-processes/) MongoDB

Assuming that MongoDB is running on `localhost` on port `27017`, then run RESTHeart as follows:

```
$ git clone git@github.com:SoftInstigate/restheart.git
$ cd restheart
$ java -jar restheart.jar etc/restheart.yml -e etc/dev.properties
```

RESTHeart will be up and running in few seconds, on HTTP port `8080`. Then go to the [tutorial](https://restheart.org/docs/tutorial/), which uses REST Ninja as a client.

> __NOTE__: for security reasons RESTHeart by default binds only on `localhost`, so it won't be reacheable from external systems unless you edit the configuration. To accept connections from everywhere, you must set at least the http listener in the `etc/dev.properties` file to bind to `0.0.0.0` like this:

```
http-listener=0.0.0.0
```

Beware that you must stop and run RESTHeart again to reload a new configuration.

Alternatively, you can run RESTHeart from with docker compose, which also starts a MongoDB container:

```
$ git clone git@github.com:SoftInstigate/restheart.git
$ cd restheart
$ docker-compose up -d
```

> __WARNING__: by default the `docker-compose.yml` binds RESTHeart to port `8080` on address `0.0.0.0`, thus your instance can be potentually reachable by external clients.

Again, point your browser to the [tutorial](https://restheart.org/docs/tutorial/) for more.

## Configuration

Refer to the [configuration file](https://github.com/SoftInstigate/restheart/blob/master/etc/restheart.yml) for inline documentation.

## Security

Starting from RESTHeart v4, security is handled by [restheart-security](https://github.com/SoftInstigate/restheart-security).

## How to Build

> Building RESTHeart requires [Maven](http://www.oracle.com/technetwork/java/javase/downloads/index.html) and Java 11 or later.

Build the project with Maven:

    $ mvn clean package

## Integration Tests

To run the integration test suite, first make sure that __mongod is running__ on `localhost`, on default port `27017` and without authentication enabled — i.e. no `--auth` option is specified.

    $ mvn verify -DskipITs=false

Alternatively, if you have Docker, execute the following script:

    $ ./bin/integration-tests.sh 
    
The script starts a Docker container running MongoDB and then execute the integration tests with Maven. It will clean-up the container at the end.

## Maven Dependencies

RESTHeart's releases are available on [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.restheart%22).

Stable releases are available at:

https://oss.sonatype.org/content/repositories/releases/org/restheart/restheart/

If you want to embed RESTHeart in your project, add the dependency to your POM file:

```
<dependencies>
    <dependency>
        <groupId>org.restheart</groupId>
        <artifactId>restheart</artifactId>
        <version>4.0.0</version>
    </dependency>
</dependencies>
```

### Snapshot Builds

Snapshots are available at [Sonatype](https://oss.sonatype.org/content/repositories/snapshots/org/restheart/restheart/). If you want to build your project against a development release, first add the SNAPSHOT repository:

```
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

```
<dependencies>
    <dependency>
        <groupId>org.restheart</groupId>
        <artifactId>restheart</artifactId>
        <version>4.0.1-SNAPSHOT</version>
    </dependency>
</dependencies>
```

### Maven Site

An automatically generated Maven Site for each build of the `master` branch is available at: http://softinstigate.github.io/restheart/

## Continuous Integration

We continually integrate and deploy development releases to Maven Central with [Travis-CI](https://travis-ci.org/SoftInstigate/restheart).

RESTHeart's public Docker images are also automatically built and pushed to [Docker Hub](https://hub.docker.com/r/softinstigate/restheart/). The `latest` tag for Docker images refers to the most recent SNAPSHOT release on the `master` branch.

<hr></hr>

_Made with :heart: by [SoftInstigate](http://www.softinstigate.com/). Follow us on [Twitter](https://twitter.com/softinstigate)_.
