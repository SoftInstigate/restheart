## RESTHeart

The Web API for MongoDB _made with :heart: by [SoftInstigate](http://www.softinstigate.com/)_

[![Build Status](https://travis-ci.org/SoftInstigate/restheart.svg?branch=master)](https://travis-ci.org/SoftInstigate/restheart)
[![Docker Stars](https://img.shields.io/docker/stars/softinstigate/restheart.svg?maxAge=2592000)](https://hub.docker.com/r/softinstigate/restheart/)
[![Docker Pulls](https://img.shields.io/docker/pulls/softinstigate/restheart.svg?maxAge=2592000)](https://hub.docker.com/r/softinstigate/restheart/)
[![Join the chat at https://gitter.im/SoftInstigate/restheart](https://badges.gitter.im/SoftInstigate/restheart.svg)](https://gitter.im/SoftInstigate/restheart?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

Table of Contents
--

- [Setup](#setup)
- [How to Build](#how-to-build)
- [Integration Tests](#integration-tests)
- [Maven Dependencies](#maven-dependencies)
- [Snapshot Builds](#snapshot-builds)

Summary
--

**RESTHeart** connects to **MongoDB** and opens data to the Web. Clients such as mobile and javascript apps can use the database via a simple **RESTful API**.

> For more information, visit RESTHeart's [website](http://restheart.org) and [documentation](http://restheart.org/learn/).

## Setup

Refer to [restheart.org/learn/setup](http://restheart.org/learn/setup) for detailed information on how to setup RESTHeart.

How to Build
--

> Building RESTHeart requires [Maven](http://www.oracle.com/technetwork/java/javase/downloads/index.html).

Clone the repository and update the git submodules. RESTHeart includes the __HAL browser__ as a submodule:

    $ git submodule update --init --recursive

Build the project with Maven:

    $ mvn clean package

Integration Tests
--

To run the integration test suite, first make sure that __mongod is running__ on localhost, on default port 27017 and without authentication enabled — i.e. no `--auth` option is specified.

    $ mvn verify -DskipITs=false

Alternatively, if you have Docker, execute the following script:

    $ ./bin/integration-tests.sh 
    
The script starts a Docker container running MongoDB and then execute the integration tests with Maven. It will clean-up the container at the end.

Maven Dependencies
--

RESTHeart's releases are available on [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.restheart%22).

Stable releases are available at:

https://oss.sonatype.org/content/repositories/releases/org/restheart/restheart/

If you want to embed RESTHeart in your project, add the dependency to your POM file:

```
<dependencies>
    <dependency>
        <groupId>org.restheart</groupId>
        <artifactId>restheart</artifactId>
        <version>3.3.0</version>
    </dependency>
</dependencies>
```

Snapshot Builds
---

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
        <version>3.4.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

We continually deploy development releases to Maven Central with [Travis-CI](https://travis-ci.org/SoftInstigate/restheart).

<hr></hr>

_Made with :heart: by [SoftInstigate](http://www.softinstigate.com/). Follow us on [Twitter](https://twitter.com/softinstigate)_.
