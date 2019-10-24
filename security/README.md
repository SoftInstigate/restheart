# RESTHeart Security

[![Build Status](https://travis-ci.org/SoftInstigate/restheart-security.svg?branch=master)](https://travis-ci.org/SoftInstigate/restheart-security)
[![](https://jitpack.io/v/SoftInstigate/restheart-security.svg)](https://jitpack.io/#SoftInstigate/restheart-security)
[![Docker Stars](https://img.shields.io/docker/stars/softinstigate/restheart-security.svg?maxAge=2592000&logo=docker)](https://hub.docker.com/r/softinstigate/restheart-security/)
[![Docker Pulls](https://img.shields.io/docker/pulls/softinstigate/restheart-security.svg?maxAge=2592000&logo=docker)](https://hub.docker.com/r/softinstigate/restheart-security/)

**restheart-security** is a security microservice for [RESTHeart v4](https://restheart.org), the Web API for MongoDB. It acts as a __reverse proxy__ for HTTP resources, providing __Authentication__ and __Authorization__ capabilities. 

**restheart-security** enables developers to configure security policies in standardized micro-gateway instances that are external to API and microservices implementations, avoiding coding security functions and a centralized gateway where scalability is a key concern.

**restheart-security** can also be used as a micro-gateway for **Identity and Access Management**  in any HTTP-based microservices architecture. 

> Think about restheart-security as the "brick" that you put in front of your API and microservices to protect them. 

# Plugins

**restheart-security** is built around a __pluggable architecture__. It comes with a strong security implementation but you can easily extend it by implementing plugins. 

> Building a plugin is as easy as implementing a simple interface and edit a configuration file. Plugins also allow to quickly implement and deploy secure Web Services.

## Maven artifacts

You can find pre-built Maven artifacts on Jitpack.io. That allows to add RESTHeart Security as a dependency on you own POM and build new plugins.

https://jitpack.io/#SoftInstigate/restheart-security

# Documentation

Find the documentation at [https://restheart.org/docs/security/overview](https://restheart.org/docs/security/overview/)


# Setup

You need __Java 11__ and must download the latest release from [releases page](https://github.com/SoftInstigate/restheart-security/releases).

```
$ tar -xzf restheart-security-XX.tar.gz
$ cd restheart-security
$ java -jar restheart-security.jar etc/restheart-security.yml
```

## Building from source

You need Git, Java 11 and Maven.

```
$ git clone git@github.com:SoftInstigate/restheart-security.git
$ cd restheart-security
$ mvn package
$ java -jar target/restheart-security.jar etc/restheart-security.yml
```

## With Docker

```
$ docker pull softinstigate/restheart-security
```

<hr>

_Made with :heart: by [SoftInstigate](http://www.softinstigate.com/). Follow us on [Twitter](https://twitter.com/softinstigate)_.
