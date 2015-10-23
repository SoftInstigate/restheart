RESTHeart 
=========

The leading REST API Server for MongoDB.

[![Build Status](https://travis-ci.org/SoftInstigate/restheart.svg?branch=develop)](https://travis-ci.org/SoftInstigate/restheart)
[![Issue Stats](http://issuestats.com/github/SoftInstigate/restheart/badge/pr)](http://issuestats.com/github/SoftInstigate/restheart)
[![Issue Stats](http://issuestats.com/github/SoftInstigate/restheart/badge/issue)](http://issuestats.com/github/SoftInstigate/restheart)

**RESTHeart** connects to **MongoDB** and opens data to the Web: Mobile and JavaScript applications can use the database via **RESTful HTTP API** calls.

For an example, check our **AngularJs** [Notes Example Application](https://github.com/softinstigate/restheart-notes-example).

Built on strong foundations
----
* The API strictly follows the **RESTful** paradigm.
* Resources are represented with the [HAL+json](https://softinstigate.atlassian.net/wiki/x/UICM) format.
* Built on top of [Undertow](http://undertow.io) web server.
* Makes use of few, best of breed libraries, check the [pom.xml](https://github.com/SoftInstigate/restheart/blob/master/pom.xml)!

Rapid Development
----
* **No server side development is required** in most of the cases for your web and mobile applications.
* The **Setup** is simple with convention over configuration approach; **Docker Container** and **Vagrant Box** are available.
* **Access Control** and **Schema Check** are provided out of the box.

Production ready
----
* High quality **Documentation** and active development **community**.
* Severe **Unit** and **Integration** test suite, **Code Check** and **Continuous Integration** process.
* **Commercial Support** available from [SoftInstigate](http://www.softinstigate.com), the company behind RESTHeart.

Fast & Light
----

* **High throughput** check the [performance tests](https://softinstigate.atlassian.net/wiki/x/gICM).
* **Lightweight** ~7Mb footprint, low RAM usage, starts in ~1 sec.
* **Horizontally Scalable** with **Stateless Architecture** and full support for MongoDB **replica sets** and **shards**.
* **µService**: it does one think and it does it well.


Documentation
----
* Web site: [http://restheart.org](http://restheart.org)

* Issues: [https://softinstigate.atlassian.net/projects/RH](https://softinstigate.atlassian.net/projects/RH)

* Documentation: [https://softinstigate.atlassian.net/wiki/display/RH/Documentation](https://softinstigate.atlassian.net/wiki/x/l4CM)

An Example
----

> RESTHeart enables clients to access MongoDB via a HTTP RESTful API


In the following example, a web client sends an HTTP GET request to the /blog/posts URI and gets back the list of blog posts documents.


![what restheart does](http://restheart.org/images/what%20restheart%20does.png)

> For more examples, check the [API tutorial](https://softinstigate.atlassian.net/wiki/x/GICM) 

Installation
---

RESTHeart can be installed on any OS supporting Java.

Complete instruction at [Installation and Setup](https://softinstigate.atlassian.net/wiki/x/FICM) documentation section.

[Docker container](https://hub.docker.com/r/softinstigate/restheart/) and [Vagrant box](https://github.com/SoftInstigate/restheart-vagrant) are also available.


How to run it
---

> Running RESTHeart requires [Java 8](http://www.oracle.com/technetwork/java/javase/downloads/index.html)

Download the latest release from [github releases page](https://github.com/SoftInstigate/restheart/releases/latest), unpack the archive and just run the jar.

	$ java -server -jar restheart.jar

You might also want to specify a configuration file:

	$ java -server -jar restheart.jar etc/restheart.yml

> the restheart.yml configuration enables authentication: users, roles and permission are defined in etc/security.yml

* Configuration file [documentation](https://softinstigate.atlassian.net/wiki/x/JYCM)
* Example configuration file [restheart.yml](https://softinstigate.atlassian.net/wiki/x/VQC9)
* Security [documentation](https://softinstigate.atlassian.net/wiki/x/W4CM)

How to build it
---

> Building RESTHeart requires [Maven](http://www.oracle.com/technetwork/java/javase/downloads/index.html).

Clone the repository and update the git submodules (the __HAL browser__ is included in restheart as a submodule):

    $ git submodule update --init --recursive

Build the project with Maven:

    $ mvn clean package

Integration tests
---

Optionally you can run the integration test suite. Make sure __mongod is running__ on localhost on default port 27017 without authentication enabled, i.e. no `--auth` option is specified.

    $ mvn verify -DskipITs=false

<hr></hr>

_Made with :heart: by [The SoftInstigate Team](http://www.softinstigate.com/). Follow us on [Twitter](https://twitter.com/softinstigate)_.
