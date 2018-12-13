# &#181;IAM

[![Build Status](https://travis-ci.org/SoftInstigate/uiam.svg?branch=master)](https://travis-ci.org/SoftInstigate/uiam)

&#181;IAM (micro-IAM) is a micro-gateway for **Identity and Access Management** designed for microservices architectures.

It acts as a reverse proxy for resources to be protected, providing __Authentication__ and __Authorization__ services.

 &#181;IAM enables developers to configure security policies in standardized micro-gateway instances that are external to API and microservices implementations, avoiding coding security functions and a centralized gateway where scalability is a key concern.

> Think about &#181;IAM as the "brick" that you put in front of your API and microservices to protect them. 

&#181;IAM is built around a __pluggable architecture__. It comes with a strong security implementation but you can easily extend it by implementing plugins. 

> Building a plugin is as easy as implementing a simple interface and edit a configuration file. Plugins also allow to quickly implement and deploy secure Web Services.

# Main features

- Identity and Access Management at __HTTP protocol level__.
- Placement within __Docker containers__, on the network layer and embeddable in Java applications.
- Extensible via easy-to-implement plugins.
- Allows to quickly implement secured Web Services.
- __Basic__, __Digest__ and __Token Authentication__. Other authentication methods can be added with plugins.
- __Roles__ based Authorization with a powerful permission definition language. Other authorization methods can be added with plugins.
- Solid multi-threading, non-blocking architecture.
- High performance.
- Small memory footprint.
- Straightforward configuration.

# Use cases

## &#181;IAM on the network layer

The following diagram shows a single instance of &#181;IAM placed on the network layer, in front of the resources to be protected. It acts as a centralized __security policy enforcer__.

![uIAM on the network layer](readme-assets/uiam-on-network-layer.png?raw=true "uIAM on the network layer")

## &#181;IAM within containers

The following diagram shows &#181;IAM used as a sidecar proxy within each container pod. Each microservice is protected by an instance of &#181;IAM with its own dedicated security policy.

![uIAM within containers](readme-assets/uiam-within-containers.png?raw=true "uIAM within containers")

## &#181;IAM embedded

The following diagram shows &#181;IAM used to implement a simple microservice using service extensions.

![uIAM embedded](readme-assets/uiam-embedded.png?raw=true "uIAM embedded")

# How it works

The `uiam.yml` configuration file allows defining listeners and proxied resources.

As an example, we securely expose the web resources of two hosts running on a private network.

The following options set a HTTPS listener bound to the public ip of `domain.io`.

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

You need __Java 11__ and must download the latest release from [releases page](https://github.com/SoftInstigate/uiam/releases).

```
$ tar -xzf uiam-XX.tar.gz
$ cd uiam
$ java -jar uiam.jar etc/uiam-dev.yml
```

## Building from source

You need Git, Java 11 and Maven.

```
$ git clone git@github.com:SoftInstigate/uiam.git
$ cd uiam
$ mvn package
$ java -jar target/uiam.jar etc/uiam-dev.yml
```

## With Docker

> work in progress

# Tutorial

To follow this tutorial you need [httpie](https://httpie.org), a modern command line HTTP client made in Python which is easy to use and produces a colorized and indented output.

Run &#181;IAM with the default configuration file, this way it is bound to port `8080` and proxies two example resources:

- https://restheart.org web site at URI `/restheart`
- the service `/echo` implemented by &#181;IAM itself at URI `/secho`. This service just echoes back the request (URL, query parameters, body and headers).

Let's fist invoke the `/echo` service directly. This is defined in the configuration file as follows:

```
services:
    - implementation-class: io.uiam.plugins.service.impl.EchoService
      uri: /echo
      secured: false
```

Note that `/echo` is not secured and can be invoked without restrictions.

```bash
$ http -f 127.0.0.1:8080/echo?qparam=value header:value a=1 b=2
HTTP/1.1 200 OK
(other headers omitted)
X-Powered-By: uIAM.io

Method: POST
URL: http://127.0.0.1:8080/echo

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

Let's try now to invoke `/secho` (please note the leading 's') without passing authentication credentials. This will fail with `401 Unauthorized` HTTP response.

```bash
$ http -f 127.0.0.1:8080/secho?qparam=value header:value a=1 b=2
HTTP/1.1 401 Unauthorized
Connection: keep-alive
Content-Length: 0
Date: Wed, 28 Nov 2018 14:42:48 GMT
WWW-Authenticate: Basic realm="uIAM Realm
```

Let's try now to pass credentials via basic authentication. The user `admin` is defined in the `security.yml` file.

```bash
$ http -a admin:changeit -f 127.0.0.1:8080/secho?qparam=value header:value a=1 b=2
HTTP/1.1 200 OK
Access-Control-Allow-Credentials: true
Access-Control-Allow-Origin: *
Access-Control-Expose-Headers: Location
Access-Control-Expose-Headers: Location, ETag, Auth-Token, Auth-Token-Valid-Until, X-Powered-By
Auth-Token: ceemz9jjcjxx4puat0gzksioehp5olxivbn1edo3hj63af3z
Auth-Token-Valid-Until: 2018-11-28T14:59:44.081609Z
(other headers omitted)
X-Powered-By: uIAM.io

Method: POST
URL: http://127.0.0.1:8080/secho

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
- the response headers include the header `Auth-Token`. Its value can be used in place of the actual password in the Basic Authentication until its expiration. This is useful in Web Applications, for storing in the browser the less sensitive auth token instead of full username and password.

# Understanding &#181;IAM

In &#181;IAM everything is a plugin including Authentication Mechanisms, Identity Managers, Access Managers, Token Managers and Services.

![uIAM explained](readme-assets/uiam-explained.png?raw=true "uIAM explained")

Different **Authentication Mechanism** manage different authentication schemes. 
An example is *BasicAuthenticationMechanism* that handles the Basic Authentication scheme. It extracts the credentials from a request header and passes them to the an Identity Manager for verification.

A different example is the *IdentityAuthenticationMechanism* the binds the request to a configured identity. This Authentication Mechanism does not require an Identity Manage to build the account.

 &#181;IAM allows defining several mechanism. As an in-bound request is received the `authenticate()` method is called on each mechanism in turn until one of the following occurs: 
 - A mechanism successfully authenticates the incoming request &#8594; the request proceeds to Authorization phase;
 - The list of mechanisms is exhausted &#8594; the request fails with code `401 Unauthorized`.

The **Identity Manager** verifies the credentials extracted from the request by Authentication Mechanism. For instance, the *BasicAuthenticationMechanism* extracts the credentials from the request in the form of id and password. The IDM can check these credentials against a database or a LDAP server. Note that some Authentication Mechanisms don't actually rely on the IDM to build the Account.

The **Access Manager** is responsible of checking if the user can actually perform the request against an Access Control List. For instance the *RequestPredicatesAccessManager* checks if the request is allowed by looking at the role based permissions defined using the undertow predicate definition language.

The **Token Manager** is responsible of generating and validating an auth-token. When a client successfully authenticates, the Token Manager generates an auth-token that is returned in the `Auth-Token` response header. It can be used to authenticate further requests. This requires an Authentication Manager able to handle it using the Token Manager for token validation.

A **Service** is a quick way of implementing Web Services to expose additional custom logic.

## Available Plugin Implementations

### Authentication Mechanisms

- **BasicAuthenticationMechanism** manages the Basic Authentication method, where the client credentials are sent via the `Authorization` request header using the format `Authorization: Basic base64(id:pwd)`. The configuration allows specifying the Identity Manager that will be used to verify the credentials.

```yml
    - name: basicAuthenticationMechanism
      class: io.uiam.plugins.authentication.impl.BasicAuthenticationMechanism
      args:
        realm: uIAM Realm
        idm: simpleFileIdentityManager
```

#### How to avoid the browser to open the login popup window

The Basic and Digest Authentication protocols requires responding with a challenge when the request cannot be authenticated as follows:

```
WWW-Authenticate: Basic realm="uIAM Realm"
WWW-Authenticate: Digest realm="uIAM Realm",domain="localhost",nonce="Toez71bBUPoNMTU0NDAwNDMzNjEwMXBY+Jp7YX/GVMcxAd61FpY=",opaque="00000000000000000000000000000000",algorithm=MD5,qop="auth"
```

In browsers this leads to the login popup windows. In our web applications we might want to redirect to a fancy login page when 401 Unauthorized response code. 

To avoid the popup window just add to the request the `noauthchallenge` query parameter or the header `No-Auth-Challenge`. This will skip the challenge response.


- **DigestAuthenticationMechanism** manages the Digest Authentication method. The configuration allows specifying the Identity Manager that will be used to verify the credentials.

```yml
    - name: digestAuthenticationMechanism
      class: io.uiam.plugins.authentication.impl.DigestAuthenticationMechanism
      args: 
        realm: uIAM Realm
        domain: localhost
        idm: simpleFileIdentityManager
```

- **AuthTokenBasicAuthenticationMechanism** manages the Basic Authentication method with the actual password replaced by the auth token generated by &#181;IAM, i.e. the client credentials are sent via the `Authorization` request header using the format `Authorization: Basic base64(id:auth-token)`. It requires a Token Manager to be configured (eg. RndTokenManager).

```yml
    - name: authTokenBasicAuthenticationMechanism
      class: io.uiam.plugins.authentication.impl.AuthTokenBasicAuthenticationMechanism
      args: 
        realm: uIAM Realm
```

- **IdentityAuthenticationMechanism** just authenticates any request building an [BaseAccount](https://github.com/SoftInstigate/uiam/blob/master/src/main/java/io/uiam/plugins/authentication/impl/BaseAccount.java) with the *username* and *roles* specified in the configuration. Useful for testing purposes. Note that enabling this causes the *DigestAuthenticationMechanism* to fail, you cannot use both.

```yml
    - name: identityAuthenticationMechanism
      class: io.uiam.plugins.authentication.impl.IdentityAuthenticationMechanism
      args:
        username: admin
        roles:
            - admin
            - user
```

### Identity Managers

- **SimpleFileIdentityManager** allows defining users credentials and roles in a simple yml configuration file. See the example [security.yml](https://github.com/SoftInstigate/uiam/blob/master/etc/security.yml).

### Access Managers

- **RequestPredicatesAccessManager** allows defining roles permissions in a yml configuration file using the [Undertows predicate language](http://undertow.io/undertow-docs/undertow-docs-2.0.0/index.html#textual-representation). See [security.yml](https://github.com/SoftInstigate/uiam/blob/master/etc/security.yml) for some examples.

### Token Managers

- **RndTokenManager** generates an auth token using a random number generator. It has one argument, `ttl`, which is the tokens Time To Live in minutes.

```yml
token-manager:
    name: rndTokenManager
    class: io.uiam.plugins.authentication.impl.RndTokenManager
    args:
      ttl: 15
```

### Services

- **PingService** a simple ping service that responds with a greetings message.
- **GetRoleService** allows to get the roles of the authenticated user. Useful as the endpoint to check the credentials of the user. Note that in case of success the auth token is included in the response; the browser can store it and use for the subsequent requests.
- **EchoService** responds with an echo of the request. Useful for testing purposes.
- **RndTokenService** allows to GET and DELETE (i.e. invalidate) the client auth token.

# Configuration

&#181;IAM is configured via the yml configuration file. See the [default configuration file](https://github.com/SoftInstigate/uiam/blob/master/etc/uiam-dev.yml) for inline help.

# Plugin development

## Develop an Authentication Mechanism

The Authentication Mechanism class must implement the `io.uiam.plugins.authentication.PluggableAuthenticationMechanism` interface. 

```java
public interface PluggableAuthenticationMechanism extends AuthenticationMechanism {
    @Override
    public AuthenticationMechanismOutcome authenticate(
            final HttpServerExchange exchange,
            final SecurityContext securityContext);

    @Override
    public ChallengeResult sendChallenge(final HttpServerExchange exchange,
            final SecurityContext securityContext);

    public String getMechanismName();
```

### Configuration

The Authentication Mechanism must be declared in the yml configuration file. 
Of course the implementation class must be in the java classpath.

```yml
auth-mechanisms:
    - name: <name-of-mechansim>
      class: <full-class-name>
      args:
        number: 10
        string: a string
```

### Constructor

The Authentication Mechanism implementation class must have the following constructor:

If the property `args` is specified in configuration:

```java
public MyAuthenticationMechanism(final String mechanismName,
            final Map<String, Object> args) throws PluginConfigurationException {

  // use argValue() helper method to get the arguments specified in the configuration file
  Integer _number = argValue(args, "number");
  String _string = argValue(args, "string");
}
```

If the property `args` is not specified in configuration:

```java
public MyAuthenticationMechanism(final String mechanismName) throws PluginConfigurationException {
}
```

### authenticate()

The method `authenticate()` must return:

- NOT_ATTEMPTED: the request cannot be authenticated because it doesn't fulfill the authentication mechanism requirements. An example is *BasicAuthenticationMechanism* when the request does not include the header `Authotization` or its value does not start by `Basic `
- NOT_AUTHENTICATED: the Authentication Mechanism handled the request but could not authenticate the client, for instance because of wrong credentials.
- AUTHENTICATED: the Authentication Mechanism successfully authenticated the request. In this case the fo

To mark the authentication as failed in `authenticate()`:

```java
securityContext.authenticationFailed("authentication failed", getMechanismName());
return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
```

To mark the authentication as successful in `authenticate()`:

```java
// build the account
final Account account;

securityContext.authenticationComplete(account, getMechanismName(), false);
        return AuthenticationMechanismOutcome.AUTHENTICATED;
```

### sendChallenge()

`sendChallenge()` is executed when the authentication fails.

An example is *BasicAuthenticationMechanism* that sends the `401 Not Authenticated` response with the following challenge header:

```
WWW-Authenticate: Basic realm="uIAM Realm"
```

### Build the Account

To build the account, the Authentication Mechanism can use a configurable Identity Manager. This allows to extends the Authentication Mechanism with different IDM implementations. For instance the *BasicAuthenticationMechanism* can use different IDM implementations that hold accounts information in a DB or in a LDAP server. 

Tip: Pass the idm name as an argument and use the `IDMCacheSingleton` class to instantiate the IDM.

```java
// get the name of the idm form the arguments
String idmName = argValue(args,"idm");

PluggableIdentityManager idm = IDMCacheSingleton
                                .getInstance()
                                .getIdentityManager(idmName);

// get the client id and credential from the request
String id;
Credential credential;


Account account = idm.verify(id, credential);
```

## Develop an Identity Manager

The Identity Manager class must implement the `io.uiam.plugins.authentication.PluggableIdentityManager` interface. 

```java
public interface PluggableIdentityManager extends IdentityManager {
    @Override
    public Account verify(Account account);
    
    @Override
    public Account verify(String id, Credential credential);

    @Override
    public Account verify(Credential credential);
}
```

### Configuration

The Identity Manager must be declared in the yml configuration file. 
Of course the implementation class must be in the java classpath.

```yml
idms:
    - name: <name-of-idm>
      class: <full-class-name>
      args:
        number: 10
        string: a string
```

### Constructor

The Identity Manager implementation class must have the following constructor:

If the property `args` is specified in configuration:

```java
public MyIdm(final String idmName,
            final Map<String, Object> args) throws PluginConfigurationException {

  // use argValue() helper method to get the arguments specified in the configuration file
  Integer _number = argValue(args, "number");
  String _string = argValue(args, "string");
}
```

If the property `args` is not specified in configuration:

```java
public MyIdm(final String idmName) throws PluginConfigurationException {
}
```

## Develop an Access Manager

The Access Manager implementation class must implement the `io.uiam.plugins.authorization.PluggableAccessManager` interface. 

```java
public interface PluggableAccessManager {

    /**
     *
     * @param exchange
     * @param context
     * @return true if request is allowed
     */
    boolean isAllowed(HttpServerExchange exchange);

    /**
     *
     * @param exchange
     * @return true if not authenticated user won't be allowed
     */
    boolean isAuthenticationRequired(final HttpServerExchange exchange);
}
```

### Configuration

The Access Manager must be declared in the yml configuration file. 
Of course the implementation class must be in the java classpath.

```yml
access-manager:
      name: <name-of-idm>
      class: <full-class-name>
      args:
        number: 10
        string: a string
```

### Constructor

The Access Manager implementation class must have the following constructor:

If the property `args` is specified in configuration:

```java
public MyAM(final String amName,
            final Map<String, Object> args) throws PluginConfigurationException {

  // use argValue() helper method to get the arguments specified in the configuration file
  Integer _number = argValue(args, "number");
  String _string = argValue(args, "string");
}
```

If the property `args` is not specified in configuration:

```java
public MyAM(final String amName) throws PluginConfigurationException {
}
```

## Develop a Token Manager

The Token Manager implementation class must implement the `io.uiam.plugins.authentication.PluggableTokenManager` interface. 

Note that PluggableTokenManager extends PluggableIdentityManager for token verification methods.

```java
public interface PluggableTokenManager extends PluggableIdentityManager {
    static final HttpString AUTH_TOKEN_HEADER = HttpString.tryFromString("Auth-Token");
    static final HttpString AUTH_TOKEN_VALID_HEADER = HttpString.tryFromString("Auth-Token-Valid-Until");
    static final HttpString AUTH_TOKEN_LOCATION_HEADER = HttpString.tryFromString("Auth-Token-Location");

    /**
     * retrieves of generate a token valid for the account
     * @param account
     * @return the token for the account
     */
    public PasswordCredential get(Account account);

    /**
     * invalidates a token
     * @param account
     * @param token 
     */
    public void invalidate(Account account, PasswordCredential token);

    /**
     * injects the token headers in the response
     * 
     * @param exchange
     * @param token 
     */
    public void injectTokenHeaders(HttpServerExchange exchange, PasswordCredential token);
}
```

### Configuration

The Token Manager must be declared in the yml configuration file. 
Of course the implementation class must be in the java classpath.

```yml
token-manager:
    name: <name-of-idm>
    class: <full-class-name>
    args:
      number: 10
      string: a string
```

### Constructor

The Access Manager implementation class must have the following constructor:

If the property `args` is specified in configuration:

```java
public MyTM(final String tmName,
            final Map<String, Object> args) throws PluginConfigurationException {

  // use argValue() helper method to get the arguments specified in the configuration file
  Integer _number = argValue(args, "number");
  String _string = argValue(args, "string");
}
```

If the property `args` is not specified in configuration:

```java
public MyTM(final String tmName) throws PluginConfigurationException {
}
```

## Develop a Service

The Service implementation class must extend the `io.uiam.plugins.service.PluggableService` abstract class, implementing the following method


```java
    /**
     *
     * @param exchange
     * @throws Exception
     */
    public abstract void handleRequest(HttpServerExchange exchange) throws Exception;
}
```

An example service implementation is. It sends the usual `Hello World` message, however if the request specifies `?name=Bob` it responds with `Hello Bob`.

```java
    public void handleRequest(HttpServerExchange exchange) throws Exception {
      var msg = new StringBuffer("Hello ");
      
      var _name = exchange.getQueryParameters().get("name");
      
      if (_name == null || _name.isEmpty()) {
          msg.append("World");
      } else {
          msg.append(_name.getFirst());
      }
      
      exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
      exchange.getResponseSender().send(msg.toString());
      exchange.endExchange();
    }
```

### Configuration

The Token Manager must be declared in the yml configuration file. 
Of course the implementation class must be in the java classpath.

```yml
services:
    - name: <name-of-idm>
      class: <full-class-name>
      uri: <the-service-uri>
      secured: <boolean>
      args:
        number: 10
        string: a string
```

The *uri* property allows to bind the service under the specified path. E.g., with `uri: /mysrv` the service responds at URL `https://domain.io/mysrv`


With `secured: true` the service request goes thought the uIAM authentication and authorization phases. With `secured: false` the service is fully open. 

### Constructor

The Service abstract class implements the following constructor Manager

```java
public PluggableService(PipedHttpHandler next,
            String name,
            String uri,
            Boolean secured,
            Map<String, Object> args);
```

<hr>

_Made with :heart: by [SoftInstigate](http://www.softinstigate.com/). Follow us on [Twitter](https://twitter.com/softinstigate)_.
