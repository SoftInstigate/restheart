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
-   [Download and Run](#download-and-run)
-   [Run with Docker](docs/docker.md)
-   [Build it yourself](docs/build.md)
-   [User guide](#user-guide)
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

## Download and Run

Preliminarily you need:

-   At least Java v11;
-   MongoDB v3 or v4 running on `localhost` on port `27017`.
-   A command line HTTP client like [curl](https://curl.haxx.se) and [httpie](https://httpie.org) or a API client like [Postman](https://www.postman.com). 

For more information on how to install and run MongoDB check the [Installation Tutorial](https://docs.mongodb.com/manual/installation/#mongodb-community-edition-installation-tutorials) and [Manage MongoDB](https://docs.mongodb.com/manual/tutorial/manage-mongodb-processes/) on MongoDB's documentation.

### Download the latest release

Two options:

1. [Build the source code](#how-to-Build)
1. [Download the latest release](https://github.com/SoftInstigate/restheart/releases/download/5.0.0-RC4/restheart.zip);

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

#### Run with Java

```bash
$ cd restheart

$ java -jar restheart.jar etc/restheart.yml -e etc/default.properties
```

To check that RESTHeart is up and running, open the URL [http://localhost:8080/ping](http://localhost:8080/ping), you should see the message: "Greetings from RESTHeart!".

---

By default RESTHeart only mounts the database `restheart`. This is controlled by the `root-mongo-resource` in the `restheart/etc/default.properties` file.

---

```properties
# The MongoDB resource to bind to the root URI /
# The format is /db[/coll[/docid]] or '*' to expose all dbs
root-mongo-resource = /restheart
```

It means that the root resource `/` is bound to the `/restheart` database. This database __doesn't actually exist__ until you explicitly create it by issuing a `PUT /` HTTP command. 

Example for localhost:

```bash
$ curl --user admin:secret -I -X PUT :8080/
HTTP/1.1 201 OK
```

RESTHeart will start bound on HTTP port `8080`.

### Default users and ACL

The default `users.yml` defines the following users:

-   id: `admin`, password: `secret`, role: `admin`
-   id: `user`, password: `secret`, role: `user`

The default `acl.yml` defines the following permission:

-   _admin_ role can execute any request
-   _user_ role can execute any request on collection `/{username}`

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

### Configuration

The main file is [`restheart.yml`](core/etc/restheart.yml) which is parametrized using [Mustache.java](https://github.com/spullara/mustache.java). The [`default.properties`](core/etc/default.properties) contains actual values for parameters defined into the YAML file. You pass these properties at startup, using the `-e` or `--envFile` parameter, like this:

```bash
$ java -jar restheart.jar etc/restheart.yml -e etc/default.properties
```

Beware that you must restart the core `restheart.jar` process to reload a new configuration (how to stop and start the process depends on how it was distributed: either in a docker container or a native Java process).

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

---

Environment variables replacement works only with primitive types: it doesn’t work with YAML structured data in configuration files, like arrays or maps. It's mandatory to use properties files and mustache syntax for that.

---

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

## User guide

For more information, read the [Use guide](http://restheart.org/docs/).

---

**WARNING**: the user guide has not yet been fully updated for v5, work is in progress. However, the fundamental public APIs have not changed since previous versions. RESTHeart v5 is more a refactoring of the internal architecture than anything else.

---

Please open a issue [here](https://github.com/SoftInstigate/restheart/issues) if you have any question, or send an email to <a href="mailto:ask@restheart.org">ask@restheart.org</a> and we'll be happy to clarify anything.

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

---

_Made with :heart: by [SoftInstigate](http://www.softinstigate.com/). Follow us on [Twitter](https://twitter.com/softinstigate)_.
