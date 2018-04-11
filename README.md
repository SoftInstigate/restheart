## RESTHeart

The leading REST API Server for MongoDB, created by [SoftInstigate](http://www.softinstigate.com).

[![Build Status](https://travis-ci.org/SoftInstigate/restheart.svg?branch=master)](https://travis-ci.org/SoftInstigate/restheart)
[![Docker Stars](https://img.shields.io/docker/stars/softinstigate/restheart.svg?maxAge=2592000)](https://hub.docker.com/r/softinstigate/restheart/)
[![Docker Pulls](https://img.shields.io/docker/pulls/softinstigate/restheart.svg?maxAge=2592000)](https://hub.docker.com/r/softinstigate/restheart/)
[![Join the chat at https://gitter.im/SoftInstigate/restheart](https://badges.gitter.im/SoftInstigate/restheart.svg)](https://gitter.im/SoftInstigate/restheart?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

Table of Contents
--
- [Summary](#summary)
- [Test with Docker](#test-with-docker)
- [Starter Guide](http://restheart.org/quick-start.html)
- [An Example](#an-example)
- [Manual Installation](#manual-installation)
- [How to Run RESTHeart](#how-to-run-restheart)
- [How to Build It](#how-to-build-it)
- [Integration Tests](#integration-tests)
- [Maven Dependencies](#maven-dependencies)
- [Snapshot Builds](#snapshot-builds)

Summary
--

**RESTHeart** connects to **MongoDB** and opens data to the Web. Mobile and JavaScript applications can use the database via **RESTful HTTP API** calls.

For an example of how RESTHeart works, check our sample **AngularJS** [notes application](https://github.com/softinstigate/restheart-notes-example).

**Note**: RESTHeart works with MongoDB v. **2.6** and later. We now test it mainly with **3.6** and **3.4**.

For more detailed information, visit RESTHeart's [website](http://restheart.org) and [documentation](http://restheart.org/learn/).

### Built on a Solid Foundation

* The API strictly follows the **RESTful** paradigm.
* Resources are represented with the [HAL+json](http://restheart.org/learn/representation-format/) format.
* Built on top of the [Undertow](http://undertow.io) web server.
* Makes use of few, best-of-breed libraries. Check the [pom.xml](https://github.com/SoftInstigate/restheart/blob/master/pom.xml)!

### Rapid Development

* RESTHeart typically requires **no server side development** for your web and mobile applications.
* The **setup** is simple, with a convention over configuration approach. We've provided a **[Docker container](https://hub.docker.com/r/softinstigate/restheart/)** and **[Vagrant box](https://github.com/SoftInstigate/restheart-vagrant)**.
* **Access Control** and **Schema Check** are provided out of the box.

### Production-Ready

* Comes with high-quality, updated **documentation** and an active development **community**.
* Includes a severe **unit** and **integration** test suite, **code check** and **continuous integration** process.
* [SoftInstigate](http://www.softinstigate.com) provides **commercial support**.

### Fast & Light

* **High throughput**: Check the [performance tests](http://restheart.org/learn/performances/).
* **Lightweight**: ~10Mb footprint, low RAM usage, and starts in ~1 sec.
* **Horizontally scalable** with a **stateless architecture** and full support for MongoDB **replica sets** and **shards**.
* **µService**: It does one thing, and it does it well.

### Quickstart with Docker

If you're running a Docker service locally, you can be on your way with RESTHeart and MongoDB in just few minutes. We've included a `Docker` folder with `Dockerfile` and `docker-compose.yml` files, plus a specific `restheart.yml` configuration in the `Docker/etc` folder. The `build.sh` bash script compiles the source code and builds the Docker image.

Steps:
```
$ cd Docker
$ ./build.sh
$ docker-compose up -d && docker-compose logs -f
```
Finally, point your browser to [http://localhost:8080/browser/](http://localhost:8080/browser/) using the id `admin` and password `changeit` when prompted for authentication.

> By default, the MongoDB instance started by [docker-compose](https://docs.docker.com/compose/) does not use any named storage, so if you remove the container you might lose all your data. For more info, please read the comments in the `docker-compose.yml` file to enable a named data volume, and the section on [managing data in containers](https://docs.docker.com/engine/tutorials/dockervolumes/).

If you have cloned from master branch, you should notice from the logs that RESTHeart's running version is the same as the POM's version (e.g. `3.1.0-SNAPSHOT`).

**Properties**
```javascript
{
  "_size": 0,
  "_total_pages": 0,
  "_returned": 0,
  "_restheart_version": "3.1.0-SNAPSHOT",
  "_type": "ROOT"
}
```
This permits a quick test cycle of new releases. Remember to clean up things and remove all containers before exiting:

```
$ docker-compose stop
$ docker-compose rm
```

An Example
--

> RESTHeart enables clients to access MongoDB via a HTTP RESTful API.

In the following example, a web client sends an HTTP GET request to the /blog/posts URI and retriees the list of blog-post documents:

![what RESTHeart does](http://restheart.org/images/what%20restheart%20does.png)

> For more examples, check the [API tutorial](https://softinstigate.atlassian.net/wiki/x/GICM).

Manual Installation
--

You can install RESTHeart on any OS that supports [Java 8](http://www.oracle.com/technetwork/java/javase/downloads/index.html). Visit the [Installation and Setup](https://softinstigate.atlassian.net/wiki/x/FICM) documentation section for instructions.

How to Run RESTHeart
--

Download the latest release from the [GitHub releases page](https://github.com/SoftInstigate/restheart/releases/latest), unpack the archive, and run the jar:

	$ java -server -jar restheart.jar

You might also want to specify a configuration file:

	$ java -server -jar restheart.jar etc/restheart.yml

> the restheart.yml configuration enables authentication: users, roles and permission are defined in etc/security.yml

* Configuration file [documentation](https://softinstigate.atlassian.net/wiki/x/JYCM)
* Example configuration file [restheart.yml](https://softinstigate.atlassian.net/wiki/x/VQC9)
* Security [documentation](https://softinstigate.atlassian.net/wiki/x/W4CM)

How to Build It
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

Alternatively, if you have Docker running, it's even simpler to execute the following script:

    $ ./bin/integration-tests.sh 
    
It will first start an empty Docker container running MongoDB and then execute the integration tests with Maven. It will clean-up the container at the end. As each time it starts a new, empty MongoDB instance, each tests execution is independent.

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
        <version>3.2.2</version>
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
        <version>3.3.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

We continually deploy development releases to Maven Central with [Travis-CI](https://travis-ci.org/SoftInstigate/restheart).

<hr></hr>

_Made with :heart: by [the SoftInstigate Team](http://www.softinstigate.com/). Follow us on [Twitter](https://twitter.com/softinstigate)_.
