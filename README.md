<p>
    <a href="https://restheart.org">
        <img src="https://restheart.org/images/rh.png" width="400px" height="auto" class="image-center img-responsive" alt="RESTHeart - REST API Microservice for MongoDB"/>
    </a>
</p>


# RESTHeart - REST API Microservice for MongoDB. #


[![Build Status](https://travis-ci.org/SoftInstigate/restheart.svg?branch=master)](https://travis-ci.org/SoftInstigate/restheart)
[![Maven Central](https://img.shields.io/maven-central/v/org.restheart/restheart.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22org.restheart%22%20AND%20a:%22restheart%22)
[![Docker Stars](https://img.shields.io/docker/stars/softinstigate/restheart.svg?maxAge=2592000)](https://hub.docker.com/r/softinstigate/restheart/)
[![Docker Pulls](https://img.shields.io/docker/pulls/softinstigate/restheart.svg?maxAge=2592000)](https://hub.docker.com/r/softinstigate/restheart/)
[![Join the chat at https://gitter.im/SoftInstigate/restheart](https://badges.gitter.im/SoftInstigate/restheart.svg)](https://gitter.im/SoftInstigate/restheart?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

## Table of Contents

- [Summary](#summary)
- [Run](#run)
- [Run without Docker](#run-without-docker)
- [Tutorial](#tutorial)
- [How to Build](#how-to-Build)
- [Integration Tests](#integration-tests)
- [Maven Dependencies](#maven-dependencies)
  - [Snapshot Builds](#snapshot-builds)
  - [Maven Site](#maven-Site)
- [Continuous Integration](#continuous-integration)
- [Full documentation](#full-documentation)

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

For more ideas have a look at the collection of common [use cases](https://restheart.org/use-cases/).

Starting from RESTHeart v4, security is handled by [restheart-security](https://github.com/SoftInstigate/restheart-security), which is a __reverse proxy microservice__ for HTTP resources, providing __Authentication__ and __Authorization__ services.

For more information on the security layer refer to [Security Overview](https://restheart.org/docs/security/overview/).

Alternatively, you can put any HTTP reverse proxy in front of RESTHeart and delegate security to it. For an example of using NGINX as a reverse proxy on top of RESTHeart, have a look at [this repository](https://github.com/SoftInstigate/nginx-restheart). Then it is possible to configure NGINX to restrict access with [HTTP Basic Authentication](https://docs.nginx.com/nginx/admin-guide/security-controls/configuring-http-basic-authentication/). 

## Run

### Prerequisites

You need Docker v1.13 and later.

Can't use Docker? Check [Run without Docker](#run-without-docker)

### Get the latest images of restheart and restheart-security

Pull images:

```bash
$ docker pull softinstigate/restheart
$ docker pull softinstigate/restheart-security
```

### Run the full stack

This runs a full stack comprising restheart-security, restheart and MongoDb using `docker-compose`

```bash
$ curl https://raw.githubusercontent.com/SoftInstigate/restheart/master/docker-compose.yml --output docker-compose.yml

$ docker-compose up -d
```

### Run only the restheart container

Refer to [restheart docker documentation](https://hub.docker.com/r/softinstigate/restheart) on docker hub for more information on how to run the restheart container. 

### Configuration

The following configuration files can be overwritten, by uncommenting the volumes sections in `docker-compose.yml`:

- [restheart.yml](https://raw.githubusercontent.com/SoftInstigate/restheart/master/etc/restheart.yml)
- [config.properties](https://raw.githubusercontent.com/SoftInstigate/restheart/master/Docker/etc/config.properties)
- [restheart-security.yml](https://raw.githubusercontent.com/SoftInstigate/restheart-security/master/etc/restheart-security.yml)
- [config-security.properties](https://raw.githubusercontent.com/SoftInstigate/restheart-security/master/Docker/etc/default-security.properties)
- [users.yml](https://raw.githubusercontent.com/SoftInstigate/restheart-security/master/etc/users.yml)
- [acl.yml](https://raw.githubusercontent.com/SoftInstigate/restheart-security/master/etc/acl.yml)

For instance, in order to overwrite the `users.yml` file, create the file `etc/users.yml` and uncomment the the volumes property of the restheart-security service:

```properties
    # uncomment to overwrite the configuration files   
    volumes:
    #    - ./etc/restheart-security.yml:/opt/restheart/etc/restheart-security.yml:ro
    #    - ./etc/default-security.properties:/opt/restheart/etc/default-security.properties:ro
    - ./etc/users.yml:/opt/restheart/etc/users.yml:ro
    #    - ./etc/acl.yml:/opt/restheart/etc/acl.yml:ro
```

## Run without Docker

### Prerequisites

You need:
- Java v11+;
- MongoDB running on `localhost` on port `27017`.

For more information on how to install and run MongoDB check [install tutorial](https://docs.mongodb.com/manual/installation/#mongodb-community-edition-installation-tutorials) and [manage mongodb](https://docs.mongodb.com/manual/tutorial/manage-mongodb-processes/) on MongoDB documentation.

### Get the latest releases of restheart and restheart-security

Download the latest releases of *restheart* and *restheart-security* from the following links:

- [download restheart](https://github.com/SoftInstigate/restheart/releases/latest)
- [download restheart-security](https://github.com/SoftInstigate/restheart-security/releases/latest)

Depending on the packages you downloaded, unzip or untar them: 

```bash
$ unzip restheart-<version>.zip
$ unzip restheart-security-<version>.zip
```

or:

```bash
$ tar -xzf restheart-<version>.tar.gz
$ tar -xzf restheart-security-<version>.tar.gz
```

### Start restheart with security

You need to run both *restheart* and *restheart-security* processes with *default* configuration.

#### Run *restheart*

```bash
$ cd restheart-<version>

$ java -jar restheart.jar etc/restheart.yml -e etc/default.properties
```

By default RESTHeart only mounts the database `restheart`. This is controlled by the `root-mongo-resource` in the `restheart/etc/default.properties` file

```properties
# The MongoDb resource to bind to the root URI / 
# The format is /db[/coll[/docid]] or '*' to expose all dbs
root-mongo-resource = /restheart
```

> It means that the root resource `/` is bound to the `/restheart` database. This database doesn't actually exist until you explicitly create it by issuing a `PUT /` HTTP command.

#### Run *restheart-security*

```bash
$ cd restheart-security-<version>

$ java -jar restheart-security.jar etc/restheart-security.yml -e etc/default.properties
```
 
__NOTE__: for security reasons RESTHeart Security by default only binds on `localhost`, so it won't be reachable from external systems unless you edit the configuration. To accept connections from everywhere, you must set the http listener in the `restheart-security/etc/default.properties` file to bind to `0.0.0.0` like this:

```properties
http-listener = 0.0.0.0
```

### Start restheart without security (standalone mode)

```bash
$ cd restheart

$ java -jar restheart.jar etc/restheart.yml -e etc/standalone.properties
```

`restheart` will start bound on HTTP port `8080`. 

### Configuration

The configuration and properties files are in the `etc` directory  of `restheart` and `restheart-security`.

Refer to the configuration files for inline documentation:

- [restheart.yml](https://github.com/SoftInstigate/restheart/blob/master/etc/restheart.yml)
- [restheart-security.yml](https://github.com/SoftInstigate/restheart-security/blob/master/etc/restheart-security.yml)

Beware that you must restart `restheart` to reload the new configuration.

## Tutorial

For a quick introduction refer to the [tutorial](https://restheart.org/docs/tutorial/) on the RESTHeart Platform documentation.

## How to Build

> Building RESTHeart requires [Maven](http://www.oracle.com/technetwork/java/javase/downloads/index.html) and Java 11 or later.

Build the project with Maven:

```bash
$ mvn clean package
```

## Integration Tests

To run the integration test suite, first make sure that __mongod is running__ on `localhost`, on default port `27017` and without authentication enabled — i.e. no `--auth` option is specified.

```bash
$ mvn verify -DskipITs=false
```

Alternatively, if you have Docker, execute the following script:

```bash
$ ./bin/integration-tests.sh 
```
    
The script starts a Docker container running MongoDB and then execute the integration tests with Maven. It will clean-up the container at the end.

## Maven Dependencies

RESTHeart's releases are available on [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.restheart%22).

Stable releases are available at:

https://oss.sonatype.org/content/repositories/releases/org/restheart/restheart/

If you want to embed RESTHeart in your project, add the dependency to your POM file:

```xml
<dependencies>
    <dependency>
        <groupId>org.restheart</groupId>
        <artifactId>restheart</artifactId>
        <version>4.1.3</version>
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
        <artifactId>restheart</artifactId>
        <version>4.1.4-SNAPSHOT</version>
    </dependency>
</dependencies>
```

### Maven Site

An automatically generated Maven Site for each build of the `master` branch is available at: http://softinstigate.github.io/restheart/

## Continuous Integration

We continually integrate and deploy development releases to Maven Central with [Travis-CI](https://travis-ci.org/SoftInstigate/restheart).

RESTHeart's public Docker images are also automatically built and pushed to [Docker Hub](https://hub.docker.com/r/softinstigate/restheart/). The `latest` tag for Docker images refers to the most recent SNAPSHOT release on the `master` branch.

## Full documentation

For more information, read the [documentation](http://restheart.org/docs/).

<hr></hr>

_Made with :heart: by [SoftInstigate](http://www.softinstigate.com/). Follow us on [Twitter](https://twitter.com/softinstigate)_.
