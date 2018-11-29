# &#181;IAM

[![Build Status](https://travis-ci.org/SoftInstigate/uiam.svg?branch=master)](https://travis-ci.org/SoftInstigate/uiam)

&#181;IAM (micro-IAM) is a micro-gateway for **Identity and Access Management** designed for microservices architectures.

It acts as a reverse proxy for resources to be protected providing Authentication and Authorization services.

 &#181;IAM enables developers to configure security policies in standardized micro-gateway instances that are external to APIs and microservices implementation avoiding coding security functions and a centralized gateway where scalability is a key concern.

> Think about &#181;IAM as the brick that you put in front of your APIs and microservices to protect them. 

&#181;IAM is built around a pluggable architecture. It comes with a strong security implementation but you can easily extend it by implementing plugins. 

> Implement a plugin is as easy as implementing a simple interface and set it up the configuration file. Plugins also allow to quickly implement and deploy secured Web Services.

# Main features

- Identity and Access Management at HTTP protocol level.
- Placement within the container, on the network layer and embeddable in java applications.
- Extensible via easy-to-implement plugins.
- Allows to quickly implement secured Web Services.
- Basic, Digest and Token Authentication. Other authentication methods can be added with plugins.
- Roles based Authorization with a powerful permission definition language. Other authorization methods can be added with plugins.
- Solid multi-threaded, no blocking architecture.
- High performance.
- Small memory footprint.
- Straightforward configuration.

# Use cases

## &#181;IAM on the network layer

The following diagram shows a single instance of &#181;IAM placed on the network layer in front of the resources to be protected. It acts as a centralized security policy enforcer.

![Alt text](readme-assets/uiam-on-network-layer.png?raw=true "uIAM on the network layer")

## &#181;IAM within containers

The following diagram shows &#181;IAM used as a sidecar proxy within each container pod. Each microservice is protected by an instance of &#181;IAM with its own security policy.

![Alt text](readme-assets/uiam-within-containers.png?raw=true "uIAM within containers")

## &#181;IAM embedded

The following diagram shows &#181;IAM used to implement a simple microservice using application logic extensions.

![Alt text](readme-assets/uiam-embedded.png?raw=true "uIAM embedded")

# How it works

The `uiam.yml` configuration file allows defining listeners and proxied resources.

As an example we will securely expose the web resources of two hosts running on a private network.

The following options set a HTTPS listener bound to the public ip of domain.io.

```yml
https-listener: true
https-host: domain.io
https-port: 443
```

The two hosts in private network `10.0.1.0/24` are:
- an API server running on host `10.0.1.1` bound to URI `/api`
- a web server running on host `10.0.1.2` bound to URI `/web`

We proxy them as follows:

```yml
proxies:
    - internal-uri: /api
      external-url: https://10.0.0.1/api
    - internal-uri: /
      external-url: https://10.0.0.2/web
```

As a result, the URLs `https://domain.io` and `https://domain.io/api` are proxied to the internal resources. All requests from the external network pass through &#181;IAM that enforces authentication and authorization.

```http
GET https://domain.io/index.html
HTTP/1.1 401 Unauthorized

GET https://domain.io/api/entities/1233
HTTP/1.1 401 Unauthorized

```

With the default configuration &#181;IAM uses the Basic Authentication with credentials and permission defined in `security.yml` configuration file:

```yml
users:
    - userid: user
      password: secret
      roles: [web,api]

permissions:
    # Users with role 'web' can GET web resources 
    - role: web
      predicate: path-prefix[path=/] and not path-prefix[path=/api] and method[GET]

    # Users with role 'api' can GET and POST /api resources 
    - role: api
      predicate: path-prefix[path=/api] and (method[GET] or method[POST])
```

```http
GET https://domain.io/index.html Authorization:"Basic dXNlcjpzZWNyZXQ="
HTTP/1.1 200 OK
...

GET https://domain.io/api/entities/1233 Authorization:"Basic dXNlcjpzZWNyZXQ="
HTTP/1.1 200 OK
...
```

# Setup

## From releases

You need java 11 and to download the latest available release from [releases page](https://github.com/SoftInstigate/uiam/releases).

```
$ tar -xzf uiam-XX.tar.gz
$ cd uiam
$ java -jar uiam.jar etc/uiam-dev.yml
```

## From source

You need git, java 11 and maven.

```
$ git clone git@github.com:SoftInstigate/uiam.git
$ cd uiam
$ mvn package
$ java -jar target/uiam.jar etc/uiam-dev.yml
```

## With Docker

> work in progress

# Tutorial

You need [httpie](https://httpie.org) a modern command line HTTP client.

Run &#181;IAM with the default configuration file, this way it is bound to port 8080 and proxies two example resources:

- https://restheart.org web site at URI /restheart
- the service /dump implemented by &#181;IAM itself at URI /pdump. This service just send back the request URL, query parameters, body and headers.

Let's fist invoke the /dump service directly. This is defined in the configuration file as follows:

```
services:
    - implementation-class: io.uiam.plugins.service.impl.RequestDumperService
      uri: /dump
      secured: false
```

Note that /dump is not secured and can be invoked without restrictions.

```bash
$ http -f 127.0.0.1:8080/dump?qparam=value header:value a=1 b=2
HTTP/1.1 200 OK
(other headers omitted)
X-Powered-By: uIAM.io

Method: POST
URL: http://127.0.0.1:8080/dump

Body
a=1&b=2

Query Parameters
	qparam: value

Headers
	Accept: */*
	Connection: keep-alive
	Accept-Encoding: gzip, deflate
	header: value
	Content-Type: application/x-www-form-urlencoded; charset=utf-8
	Content-Length: 7
	User-Agent: HTTPie/0.9.9
	Host: 127.0.0.1:8080
```

Let's try now to invoke /pdump without passing authentication credentials. This will fail with 401 Unauthorized response HTTP status.

```bash
$ http -f 127.0.0.1:8080/pdump?qparam=value header:value a=1 b=2
HTTP/1.1 401 Unauthorized
Connection: keep-alive
Content-Length: 0
Date: Wed, 28 Nov 2018 14:42:48 GMT
WWW-Authenticate: Basic realm="uIAM Realm
```

Let's try now to pass credentials via basic authentication. The user `admin` is defined in the `security.yml` file.

```bash
$ http -a admin:changeit -f 127.0.0.1:8080/pdump?qparam=value header:value a=1 b=2
HTTP/1.1 200 OK
Access-Control-Allow-Credentials: true
Access-Control-Allow-Origin: *
Access-Control-Expose-Headers: Location
Access-Control-Expose-Headers: Location, ETag, Auth-Token, Auth-Token-Valid-Until, Auth-Token-Location, X-Powered-By
Auth-Token: ceemz9jjcjxx4puat0gzksioehp5olxivbn1edo3hj63af3z
Auth-Token-Location: /_authtokens/admin
Auth-Token-Valid-Until: 2018-11-28T14:59:44.081609Z
(other headers omitted)
X-Powered-By: uIAM.io

Method: POST
URL: http://127.0.0.1:8080/dump

Body
a=1&b=2

Query Parameters
	qparam: value

Headers
	Accept: */*
	Accept-Encoding: gzip, deflate
	X-Forwarded-Server: 127.0.0.1
	User-Agent: HTTPie/0.9.9
	X-Forwarded-Account-Roles: admin,user
	Connection: keep-alive
	X-Forwarded-Proto: http
	X-Forwarded-Port: 8080
	X-Forwarded-Account-Id: admin
	X-Forwarded-For: 127.0.0.1
	header: value
	Content-Type: application/x-www-form-urlencoded; charset=utf-8
	Content-Length: 7
	Host: 127.0.0.1:8080
	X-Forwarded-Host: 127.0.0.1:8080
  ```

We can note that &#181;IAM:

- has checked the credential passed via Basic Authentication and proxied the request
- has determined the account roles. The proxied request includes the headers `X-Forwarded-Account-Id` and `X-Forwarded-Account-Roles`.
- has checked the permission specified in `security.yml` for the account roles and determined that the request could be executed.
- the response headers include the header `Auth-Token`. Its value can be used in place of the actual password in the Basic Authentication until its expiration. This is useful in web application to store in the browser the less sensitive auth token.

# Understanding &#181;IAM

In &#181;IAM everything is a plugin including Authentication Mechanisms,  Identity Managers, Authentication Managers and Services.

Different **Authentication Mechanism** manage different authentication schemes. 
An example is the provided BasicAuthenticationMechanism that handles the Basic Authentication scheme. In this case it extracts the credentials from a request header and passes them to the Identity Manager for verification.

A different example would be an Authentication Mechanism that handles the JWT (JSON Web Token) scheme. In this case user id is defined in a digitally signed JSON object in a request header. The Authentication Mechanism would then just verify the token withour requiring an Identity Manager.

The **Identity Manager** verifies the credentials extracted from the request by Authentication Mechanism. For instance, the BasicAuthenticationMechanism extracts the credentials from the request in the form of id and password. The IDM can check these credentials against a database or and LDAP server. Note that some Authentication Mechanisms don't actually rely on the IDM to build the Account.

The **Access Manager** is responsible of checking if the user can actually perform the request against an Access Control List. For instance a simple AM might check if the request is allowed checking it against a set of whitelisted URIs.

A **Service** is a quick way of implementing a Web Service. This allows &#181;IAM to expose additional custom logic.

## Available Plugin Implementations

### Authentication Mechanisms

- **BasicAuthenticationMechanism** manages the Basic Authentication method, where the client credentials are sent via the `Authorization` request header using the format `Authorization: Basic base64(id:pwd)` 
- **DigestAuthenticationMechanism** manages the Digest Authentication method.
- **AuthTokenBasicAuthenticationMechanism** manages the Basic Authentication method when the actual password is replaced by the auth token generated by &#181;IAM, i.e. the client credentials are sent via the `Authorization` request header using the format `Authorization: Basic base64(id:auth-token)` 

### Identity Managers

- **SimpleFileIdentityManager** allows defining users credentials and roles in a simple yml configuration file. See the example [security.yml](https://github.com/SoftInstigate/uiam/blob/master/etc/security.yml).

### Access Managers

- **SimpleAccessManager** allows defining roles permissions in a simple yml configuration file. See the example [security.yml](https://github.com/SoftInstigate/uiam/blob/master/etc/security.yml).

### Services

- **PingService** a simple ping service that responds with a greetings message.
- **GetRoleService** allows to get the roles of the authenticated user. Useful as the endpoint to check the credentials of the user. Note that in case of success the auth token is included in the response; the browser can store it and use for the subsequent requests.
- **RequestDumperService** responds with a dump of the request. Useful for testing purposes.

# Configuration

&#181;IAM is configured via the yml configuration file. See the [default configuration file](https://github.com/SoftInstigate/uiam/blob/master/etc/uiam-dev.yml) for inline help.

# Plugin development

## Package code

> work in progress

## Develop an Identity Manager

> work in progress

## Develop an Access Manager

> work in progress

## Develop an Authentication Manager

> work in progress

## Develop a Service

> work in progress

<hr>

_Made with :heart: by [SoftInstigate](http://www.softinstigate.com/). Follow us on [Twitter](https://twitter.com/softinstigate)_.
