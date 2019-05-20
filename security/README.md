# restheart-security

[![Build Status](https://travis-ci.org/SoftInstigate/uiam.svg?branch=master)](https://travis-ci.org/SoftInstigate/uiam)
[![Docker Stars](https://img.shields.io/docker/stars/softinstigate/uiam.svg?maxAge=2592000&logo=docker)](https://hub.docker.com/r/softinstigate/uiam/)
[![Docker Pulls](https://img.shields.io/docker/pulls/softinstigate/uiam.svg?maxAge=2592000&logo=docker)](https://hub.docker.com/r/softinstigate/uiam/)

**restheart-security** is the security service of   [RESTHeart](https://restheart.org), the Web API for MongoDB. It acts as a reverse proxy for HTTP resources, providing __Authentication__ and __Authorization__ services. 

**restheart-security** enables developers to configure security policies in standardized micro-gateway instances that are external to API and microservices implementations, avoiding coding security functions and a centralized gateway where scalability is a key concern.

**restheart-security** can also be used as a micro-gateway for **Identity and Access Management**  in any HTTP-based microservices architecture. 

> Think about &#181;IAM as the "brick" that you put in front of your API and microservices to protect them. 

**restheart-security** is built around a __pluggable architecture__. It comes with a strong security implementation but you can easily extend it by implementing plugins. 

> Building a plugin is as easy as implementing a simple interface and edit a configuration file. Plugins also allow to quickly implement and deploy secure Web Services.

# Main features

- Identity and Access Management at __HTTP protocol level__.
- Placement within __Docker containers__, on the network layer and embeddable in Java applications.
- Can be extended via easy-to-implement plugins.
- Allows to quickly implement secured Web Services.
- __Basic__, __Digest__ and __Token Authentication__. Other authentication methods can be added with plugins.
- __Roles__ based Authorization with a powerful permission definition language. Other authorization methods can be added with plugins.
- Solid multi-threading, non-blocking architecture.
- High performance.
- Small memory footprint.
- Straightforward configuration.

# Use cases

## **restheart-security** on the network layer

The following diagram shows a single instance of **restheart-security** placed on the network layer, in front of the resources to be protected. It acts as a centralized __security policy enforcer__.

![restheart-security on the network layer](readme-assets/uiam-on-network-layer.png?raw=true "restheart-security on the network layer")

## **restheart-security** within containers

The following diagram shows **restheart-security** used as a sidecar proxy within each container pod. Each microservice is protected by an instance of **restheart-security** with its own dedicated security policy.

![restheart-security within containers](readme-assets/uiam-within-containers.png?raw=true "restheart-security within containers")

## **restheart-security** embedded

The following diagram shows **restheart-security** used to implement a simple microservice using service extensions.

![restheart-security embedded](readme-assets/uiam-embedded.png?raw=true "restheart-security embedded")

# How it works

The `restheart-security.yml` configuration file allows defining listeners and proxied resources in the first place.

As an example, we are going to securely expose the resources of a RESTHeart server and Web Server running on a private network.

The following options set a HTTPS listener bound to the public ip of `domain.io`.

```yml
https-listener: true
https-host: domain.io
https-port: 443
```

The two hosts in private network `10.0.1.0/24` are:
- the RESTHeart server running on host `10.0.1.1` that exposes the collection `/db/coll`
- the web server running on host `10.0.1.2` bound to URI `/web`

We proxy them as follows:

```yml
proxies:
    - location: /api
      proxy-pass: https://10.0.0.1/db/coll
    - location: /
      proxy-pass: https://10.0.0.2/web
```

As a result, the URLs `https://domain.io` and `https://domain.io/api` are proxied to the resources specified by the `proxy-pass` URLs. All requests from the external network pass through **restheart-security** that enforces authentication and authorization.

```http
GET https://domain.io/index.html
HTTP/1.1 401 Unauthorized

GET https://domain.io/api
HTTP/1.1 401 Unauthorized

```

With the default configuration **restheart-secuirty** uses the Basic Authentication with credentials and permission defined in `users.yml` and `acl.yml` configuration files respectively:

### users.yml

```yml
users:
    - userid: user
      password: secret
      roles: [web,api]
```

### acl.yml

```
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

GET https://domain.io/api Authorization:"Basic dXNlcjpzZWNyZXQ="
HTTP/1.1 200 OK
...
```

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
$ java -jar target/uirestheart-securityam.jar etc/restheart-security.yml
```

## With Docker

> work in progress

# Tutorial

To follow this tutorial you need [httpie](https://httpie.org), a modern command line HTTP client made in Python which is easy to use and produces a colorized and indented output.

Run **restheart-security** with the [default configuration file](etc/restheart-security.yml). It is bound to port `8080` and proxies two example resources:

- https://restheart.org web site at URI `/restheart`
- the service `/echo` implemented by **restheart-security** itself and bound to URI `/secho`. It just echoes back the request (URL, query parameters, body and headers).

Below the mentioned configuration's fragment:

```yaml
proxies:
    - location: /secho
      proxy-pass: 
        - http://127.0.0.1:8080/echo
        - http://localhost:8080/echo
      connections-per-thread: 20
    - location: /restheart
      proxy-pass: https://restheart.org
```

Let's fist invoke the `/echo` service directly. This is defined in the [configuration file](etc/restheart-security.yml) as follows:

```yaml
services:
    - implementation-class: org.restheart.plugins.service.EchoService
      uri: /echo
      secured: false
```

Note that `/echo` is not secured and can be invoked without restrictions.

```bash
$ http -f 127.0.0.1:8080/echo?qparam=value header:value a=1 b=2
HTTP/1.1 200 OK
Access-Control-Allow-Credentials: true
Access-Control-Allow-Origin: *
Access-Control-Expose-Headers: Location
Access-Control-Expose-Headers: Location, ETag, Auth-Token, Auth-Token-Valid-Until, Auth-Token-Location, X-Powered-By
Connection: keep-alive
Content-Encoding: gzip
Content-Length: 341
Content-Type: application/json
Date: Mon, 18 Feb 2019 17:25:19 GMT
X-Powered-By: restheart.org

{
    "URL": "http://127.0.0.1:8080/echo",
    "content": "a=1&b=2",
    "headers": {
        "Accept": [
            "*/*"
        ],
        "Accept-Encoding": [
            "gzip, deflate"
        ],
        "Connection": [
            "keep-alive"
        ],
        "Content-Length": [
            "7"
        ],
        "Content-Type": [
            "application/x-www-form-urlencoded; charset=utf-8"
        ],
        "Host": [
            "127.0.0.1:8080"
        ],
        "User-Agent": [
            "HTTPie/1.0.2"
        ],
        "header": [
            "value"
        ]
    },
    "method": "POST",
    "note": "showing up to 20 bytes of the request content",
    "prop2": "property added by example response interceptor",
    "qparams": {
        "pagesize": [
            "0"
        ],
        "qparam": [
            "value"
        ]
    }
}
```

Let's try now to invoke `/secho` (please note the leading 's') without passing authentication credentials. This will fail with `401 Unauthorized` HTTP response.

```bash
$ http -f 127.0.0.1:8080/secho?qparam=value header:value a=1 b=2
HTTP/1.1 401 Unauthorized
Connection: keep-alive
Content-Length: 0
Date: Mon, 18 Feb 2019 17:26:04 GMT
WWW-Authenticate: Basic realm="RESTHeart Realm"
WWW-Authenticate: Digest realm="RESTHeart Realm",domain="localhost",nonce="Z+fsw9eFwPgNMTU1MDUxMDc2NDA2NmFWzLOw/aaHdtjyi0jm5uE=",opaque="00000000000000000000000000000000",algorithm=MD5,qop="auth"
```

Let's try now to pass credentials via basic authentication. The user `admin` is defined in the `users.yml` file.

```bash
$ http -a admin:changeit -f 127.0.0.1:8080/secho?qparam=value header:value a=1 b=2
HTTP/1.1 200 OK
Access-Control-Allow-Credentials: true
Access-Control-Allow-Origin: *
Access-Control-Expose-Headers: Location
Access-Control-Expose-Headers: Location, ETag, Auth-Token, Auth-Token-Valid-Until, Auth-Token-Location, X-Powered-By
Auth-Token: 5iojkf21pdul7layo3qultyes7qyt8obdm1u67hkmnw6l39ckm
Auth-Token-Location: /tokens/admin
Auth-Token-Valid-Until: 2019-02-18T17:41:25.142209Z
Connection: keep-alive
Content-Encoding: gzip
Content-Length: 427
Content-Type: application/json
Date: Mon, 18 Feb 2019 17:26:25 GMT
X-Powered-By: restheart.org

{
    "URL": "http://localhost:8080/echo",
    "content": "a=1&b=2",
    "headers": {
        "Accept": [
            "*/*"
        ],
        "Accept-Encoding": [
            "gzip, deflate"
        ],
        "Connection": [
            "keep-alive"
        ],
        "Content-Length": [
            "7"
        ],
        "Content-Type": [
            "application/x-www-form-urlencoded; charset=utf-8"
        ],
        "Host": [
            "localhost:8080"
        ],
        "User-Agent": [
            "HTTPie/1.0.2"
        ],
        "X-Forwarded-Account-Id": [
            "admin"
        ],
        "X-Forwarded-Account-Roles": [
            "user,admin"
        ],
        "X-Forwarded-For": [
            "127.0.0.1"
        ],
        "X-Forwarded-Host": [
            "127.0.0.1:8080"
        ],
        "X-Forwarded-Port": [
            "8080"
        ],
        "X-Forwarded-Proto": [
            "http"
        ],
        "X-Forwarded-Server": [
            "127.0.0.1"
        ],
        "header": [
            "value"
        ]
    },
    "method": "POST",
    "note": "showing up to 20 bytes of the request content",
    "prop2": "property added by example response interceptor",
    "qparams": {
        "pagesize": [
            "0"
        ],
        "qparam": [
            "value"
        ]
    }
}
  ```

We can note that **restheart-security**:

- has checked the credential specified in `users.yml` passed via Basic Authentication and proxied the request
- has determined the account roles. The proxied request includes the headers `X-Forwarded-Account-Id` and `X-Forwarded-Account-Roles`.
- has checked the permission specified in `acl.yml` for the account roles and determined that the request could be executed.
- the response headers include the header `Auth-Token`. Its value can be used in place of the actual password in the Basic Authentication until its expiration. This is useful in Web Applications, for storing in the browser the less sensitive auth token instead of full username and password.

# Understanding **restheart-security**

In **restheart-security** everything is a plugin including Authentication Mechanisms, Authenticators, Authorizers, Token Managers and Services.

![restheart-security explained](readme-assets/uiam-explained.png?raw=true "restheart-security explained")

Different **Authentication Mechanism** manage different authentication schemes. 
An example is *BasicAuthMechanism* that handles the Basic Authentication scheme. It extracts the credentials from a request header and passes them to the an Authenticator for verification.

A different example is the *IdentityAuthMechanism* that binds the request to a single identity specified by configuration. This Authentication Mechanism does not require an Authenticator to build the account.

 **restheart-security** allows defining several mechanism. As an in-bound request is received, the `authenticate()` method is called on each mechanism in turn until one of the following occurs: 
 - A mechanism successfully authenticates the incoming request &#8594; the request proceeds to Authorization phase;
 - The list of mechanisms is exhausted &#8594; the request fails with code `401 Unauthorized`.

The **Authenticator** verifies the credentials extracted from the request by Authentication Mechanism. For instance, the *BasicAuthMechanism* extracts the credentials from the request in the form of id and password. The Authenticator can check these credentials against a database or a LDAP server. Note that some Authentication Mechanisms don't actually rely on a Authenticator to build the Account.

The **Authorizer** is responsible of checking if the user can actually perform the request against an Access Control List. For instance the *RequestPredicatesAuthorizer* checks if the request is allowed by looking at the role based permissions defined using the undertow predicate definition language.

The **Token Manager** is responsible of generating and validating an auth-token. When a client successfully authenticates, the Token Manager generates an auth-token that is returned in the `Auth-Token` response header. It can be used to authenticate further requests. This requires an Authentication Manager to handle it using the Token Manager for token validation.

A **Service** is a quick way of implementing Web Services to expose additional custom logic.

## Available Plugins

### Authentication Mechanisms

- **BasicAuthMechanism** manages the Basic Authentication method, where the client credentials are sent via the `Authorization` request header using the format `Authorization: Basic base64(id:pwd)`. The configuration allows specifying the Authenticator that will be used to verify the credentials.

```yml
    - name: basicAuthMechanism
      class: org.restheart.security.plugins.mechanisms.BasicAuthMechanism
      args:
        realm: RESTHeart Realm
        authenticator: simpleFileAuthenticator
```

#### How to avoid the browser to open the login popup window

The Basic and Digest Authentication protocols requires responding with a challenge when the request cannot be authenticated as follows:

```
WWW-Authenticate: Basic realm="RESTHeart Realm"
WWW-Authenticate: Digest realm="RESTHeart Realm",domain="localhost",nonce="Toez71bBUPoNMTU0NDAwNDMzNjEwMXBY+Jp7YX/GVMcxAd61FpY=",opaque="00000000000000000000000000000000",algorithm=MD5,qop="auth"
```

In browsers this leads to the login popup windows. In our web applications we might want to redirect to a fancy login page when the 401 Unauthorized response code. 

To avoid the popup window just add to the request the `noauthchallenge` query parameter or the header `No-Auth-Challenge`. This will skip the challenge response.


- **DigestAuthMechanism** manages the Digest Authentication method. The configuration allows specifying the Authenticator that will be used to verify the credentials.

```yml
    - name: digestAuthMechanism
      class: org.restheart.security.plugins.mechanisms.DigestAuthMechanism
      args: 
        realm: RESTHeart Realm
        domain: localhost
        authenticator: simpleFileAuthenticator
```

- **TokenBasicAuthMechanism** manages the Basic Authentication method with the actual password replaced by the auth token generated by **restheart-security**, i.e. the client credentials are sent via the `Authorization` request header using the format `Authorization: Basic base64(id:auth-token)`. It requires a Token Manager to be configured (eg. RndTokenManager).

```yml
    - name: tokenBasicAuthMechanism
      class: org.restheart.security.plugins.mechanisms.TokenBasicAuthMechanism
      args: 
        realm: RESTHeart Realm
```

- **IdentityAuthMechanism** just authenticates any request building an [BaseAccount](https://github.com/SoftInstigate/restheart-security/blob/master/src/main/java/io/restheart-security/plugins/authentication/impl/BaseAccount.java) with the *username* and *roles* specified in the configuration. Useful for testing purposes. Note that enabling this causes the *DigestAuthMechanism* to fail, you cannot use both.

```yml
    - name: identityAuthMechanism
      class: org.restheart.security.plugins.mechanisms.IdentityAuthMechanism
      args:
        username: admin
        roles:
            - admin
            - user
```

### Authenticators

- **simpleFileAuthenticator** allows defining users credentials and roles in a simple yml configuration file. See the example [users.yml](https://github.com/SoftInstigate/restheart-security/blob/master/etc/users.yml).

### Authorizers

- **RequestPredicatesAuthorizer** allows defining roles permissions in a yml configuration file using the [Undertows predicate language](http://undertow.io/undertow-docs/undertow-docs-2.0.0/index.html#textual-representation). See [acl.yml](https://github.com/SoftInstigate/restheart-security/blob/master/etc/acl.yml) for some examples.

### Token Managers

- **RndTokenManager** generates an auth token using a random number generator. It has one argument, `ttl`, which is the tokens Time To Live in minutes.

```yml
token-manager:
    name: rndTokenManager
    class: org.restheart.security.plugins.tokens.RndTokenManager
    args:
      ttl: 15
```

### Services

- **PingService** a simple ping service that responds with a greetings message.
- **GetRoleService** allows to get the roles of the authenticated user. Useful as the endpoint to check the credentials of the user. Note that in case of success the auth token is included in the response; the browser can store it and use for the subsequent requests.
- **EchoService** responds with an echo of the request. Useful for testing purposes.
- **RndTokenService** allows to GET and DELETE (i.e. invalidate) the client auth token.

# Configuration

**restheart-security** is configured via the yml configuration file. See the [default configuration file](https://github.com/SoftInstigate/restheart-security/blob/master/etc/restheart-security.yml) for inline help.

# Plugin development

## Develop an Authentication Mechanism

The Authentication Mechanism class must implement the `org.restheart.security.plugins.AuthMechanism` interface. 

```java
public interface AuthMechanism implements AuthenticationMechanism {
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
    - name: <name>
      class: <full-class-name>
      args:
        number: 10
        string: a string
```

### Constructor

The Authentication Mechanism implementation class must have the following constructor:

If the property `args` is specified in configuration:

```java
public MyAuthMechanism(final String name, final Map<String, Object> args) throws ConfigurationException {

  // use argValue() helper method to get the arguments specified in the configuration file
  Integer _number = argValue(args, "number");
  String _string = argValue(args, "string");
}
```

If the property `args` is not specified in configuration:

```java
public MyAuthMechanism(final String name) throws ConfigurationException {
}
```

### authenticate()

The method `authenticate()` must return:

- NOT_ATTEMPTED: the request cannot be authenticated because it doesn't fulfill the authentication mechanism requirements. An example is *BasicAutMechanism* when the request does not include the header `Authotization` or its value does not start by `Basic `
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

An example is *BasicAuthMechanism* that sends the `401 Not Authenticated` response with the following challenge header:

```
WWW-Authenticate: Basic realm="RESTHeart Realm"
```

### Build the Account

To build the account, the Authentication Mechanism can use a configurable Authenticator. This allows to extends the Authentication Mechanism with different Authenticator implementations. For instance the *BasicAuthMechanism* can use different Authenticator implementations that hold accounts information in a DB or in a LDAP server. 

Tip: Use the `PluginsRegistry` to get the instance of the Authenticator from its name.

```java
// get the name of the authenticator from the arguments
String authenticatorName = argValue(args, "authenticator");

Authenticator authenticator = PluginsRegistry
                                .getInstance()
                                .getAuthenticator(authenticatorName);

// get the client id and credential from the request
String id;
Credential credential;


Account account = authenticator.verify(id, credential);
```

## Develop an Authenticator

The Authenticator class must implement the `org.restheart.security.plugins.Authenticator` interface. 

```java
public interface Authenticator extends IdentityManager {
  @Override
  public Account verify(Account account);
  
  @Override
  public Account verify(String id, Credential credential);

  @Override
  public Account verify(Credential credential);
}
```

### Configuration

The Authenticator must be declared in the yml configuration file. 
Of course the implementation class must be in the java classpath.

```yml
authenticators:
    - name: <name>
      class: <full-class-name>
      args:
        number: 10
        string: a string
```

### Constructor

The Authenticator implementation class must have the following constructor:

If the property `args` is specified in configuration:

```java
public MyAuthenticator(final String name, final Map<String, Object> args) throws ConfigurationException {

  // use argValue() helper method to get the arguments specified in the configuration file
  Integer _number = argValue(args, "number");
  String _string = argValue(args, "string");
}
```

If the property `args` is not specified in configuration:

```java
public MyAuthenticator(final String name) throws ConfigurationException {
}
```

## Develop an Authorizer

The Authorizer implementation class must implement the `org.restheart.security.Authorizer` interface. 

```java
public interface Authorizer {
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

The Authorizer must be declared in the yml configuration file. 
Of course the implementation class must be in the java classpath.

```yml
authorizers:
      name: <name>
      class: <full-class-name>
      args:
        number: 10
        string: a string
```

### Constructor

The Authorizer implementation class must have the following constructor:

If the property `args` is specified in configuration:

```java
public MyAuthorizer(final String name, final Map<String, Object> args) throws ConfigurationException {

  // use argValue() helper method to get the arguments specified in the configuration file
  Integer _number = argValue(args, "number");
  String _string = argValue(args, "string");
}
```

If the property `args` is not specified in configuration:

```java
public MyAuthorizer(final String name) throws ConfigurationException {
}
```

## Develop a Token Manager

The Token Manager implementation class must implement the `org.restheart.security.plugins.TokenManager` interface. 

Note that TokenManager extends Authenticator for token verification methods.

```java
public interface PluggablTokenManager extends Authenticator {
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
   * updates the account bound to a token
   * @param account
   */
  public void update(Account account);

  /**
   * invalidates the token bound to the account
   * @param account
   * @param token 
   */
  public void invalidate(Account account);

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
    name: <name>
    class: <full-class-name>
    args:
      number: 10
      string: a string
```

### Constructor

The Token Manager implementation class must have the following constructor:

If the property `args` is specified in configuration:

```java
public MyTM(final String name, final Map<String, Object> args) throws ConfigurationException {

  // use argValue() helper method to get the arguments specified in the configuration file
  Integer _number = argValue(args, "number");
  String _string = argValue(args, "string");
}
```

If the property `args` is not specified in configuration:

```java
public MyTM(final String name) throws ConfigurationException {
}
```

## Develop a Service

The Service implementation class must extend the `org.restheart.security.plugins.Service` abstract class, implementing the following method


```java
public abstract class Service extends PipedHttpHandler implements ConfigurablePlugin {
  /**
   *
   * @param exchange
   * @throws Exception
   */
  public abstract void handleRequest(HttpServerExchange exchange) throws Exception;
  }
}
```

An example service implementation follows. It sends the usual `Hello World` message, however if the request specifies `?name=Bob` it responds with `Hello Bob`.

```java
public void handleRequest(HttpServerExchange exchange) throws Exception {
  var msg = new StringBuffer("Hello ");
  
  var _name = exchange.getQueryParameters().get("name");
  
  if (_name == null || _name.isEmpty()) {
      msg.append("World");
  } else {
      msg.append(_name.getFirst());
  }

  var response = ByteArrayResponse.wrap(exchange);

  response.setStatusCode(HttpStatus.SC_OK);
  response.setContentType("text/plain");
  response.writeContent(msg.getBytes());
}
```

### Configuration

The *Service* must be declared in the yml configuration file. 
Of course the implementation class must be in the java classpath.

```yml
services:
    - name: <name>
      class: <full-class-name>
      uri: <the-service-uri>
      secured: <boolean>
      args:
        number: 10
        string: a string
```

The *uri* property allows to bind the service under the specified path. E.g., with `uri: /mysrv` the service responds at URL `https://domain.io/mysrv`


With `secured: true` the service request goes thought the  authentication and authorization phases. With `secured: false` the service is fully open. 

### Constructor

The Service abstract class implements the following constructor:

```java
public MyService(PipedHttpHandler next,
          String name,
          String uri,
          Boolean secured,
          Map<String, Object> args);
```

<hr>

## Develop an Initializer

An *Initializer* allows executing custom logic at startup time. 

Notably it allows to define *Interceptors* and *Global Permission Predicates*.

The Initializer implementation class must extend the `org.restheart.security.plugins.Initializer` interface, implementing the following method:

```java
public interface Initializer {
  public void init();
}
```

An example Initializer is `org.restheart.security.plugins.initializers.ExampleInitializer`.

### Configuration

The Initializer must be declared in the yml configuration file. 
Of course the implementation class must be in the java classpath.

```yml
initializer-class: org.restheart.security.plugins.initializers.ExampleInitializer
```

### Defining Interceptors

The `PluginsRegistry` class allows to define Interceptors.

```java
RequestInterceptor requestInterceptor = ...;
ResponseInterceptor responseIterceptor = ...;

PluginsRegistry.getRequestInterceptors().add(requestInterceptor);

PluginsRegistry.getResponseInterceptors().add(responseIterceptor);
```

### Defining Global Permission Predicates

The `GlobalSecuirtyPredicatesAuthorizer` class allows to define Global Predicates. Requests must resolve all of the predicates to be allowed.

> You can think about a Global Predicate a way to black list request matching a given condition.

The following example predicate denies `GET /foo/bar` requests:

```java
// add a global security predicate
GlobalSecuirtyPredicatesAuthorizer.getGlobalSecurityPredicates().add(new Predicate() {
    @Override
    public boolean resolve(HttpServerExchange exchange) {
        var request = Request.wrap(exchange);

        // return false to deny the request
        return !(request.isGet() 
                        && "/secho/foo".equals(URLUtils.removeTrailingSlashes(
                                        exchange.getRequestPath())));
    }
});
```

<hr>

## Develop an Interceptor

Interceptors allows to snoop and modify request and responses.

A Request Interceptor applies before the request is proxied or handled by a *Service* thus allowing to modify the request. Its implementation class must implement the interface `org.restheart.security.plugins.RequestInterceptor` .

A Response Interceptor applies after the request has been proxied or handled by a *Service* thus allowing to modify the response. Its implementation class must implement the interface `org.restheart.security.plugins.ResponseInterceptor`.

Those interfaces both extend the base interface `org.restheart.security.plugins.Interceptor`


```java
public interface Interceptor {
  /**
   * implements the interceptor logic
   * 
   * @param exchange
   * @throws Exception 
   */
  public void handleRequest(final HttpServerExchange exchange) throws Exception;
  
  /**
   * 
   * @param exchange
   * @return true if the interceptor must handle the request
   */
  public boolean resolve(final HttpServerExchange exchange);
}
```

The `handleRequest()` method is invoked only if the `resolve()` method returns true.

Example interceptor implementations can be found in the package``org.restheart.security.plugins.interceptors`.

### Accessing the Content in Request Interceptors

In some cases, you need to access the request content. For example you want to modify request content with a `RequestInterceptor` or to implement an `Authorizer` that checks the content to authorize the request.

 Accessing the content from the *HttpServerExchange* object using the exchange *InputStream* in proxied requests leads to an error because Undertow allows reading the content just once. 

 In order to simplify accessing the content, the `ByteArrayRequest.wrap(exchange).readContent()` and `JsonRequest.wrap(exchange).readContent()` helper methods are available. They are very efficient since they use the non blocking `RequestBufferingHandler` under to hood.
 However, since accessing the request content might lead to significant performance overhead, a *RequestInterceptor* that resolves the request and overrides the `requiresContent()` to return true must be implemented to make data available.

 `RequestInterceptor` defines the following method with a default implementation that returns false:

```java
public interfaceRequestInterceptor extends Interceptor {
  /**
   *
   * @return true if the Interceptor requires to access the request content
   */
  default boolean requiresContent() {
      return false;
  }
}
```

Please note that, in order to mitigate DoS attacks, the size of the Request content available with `readContent()` is limited to 16 Mbytes.

### Accessing the Content in Response Interceptors

In some cases, you need to access the response content. For example you want the modify the response from a proxied resource before sending it to the client.

 In order to simplify accessing the content, the `ByteArrayRequest.wrap(exchange).readContent()` and `JsonResponse.wrap(exchange).readContent()` helper methods are available. Since accessing the response content might lead to significant performance overhead because the full response must be read by **restheart-security**, a *ResponseInterceptor* that resolves the request and overrides the `requiresResponseContent()` to return true must be implemented to make data available.

 `ResponseInterceptor` defines the following method with a default implementation that returns false:

```java
public interface ResponseInterceptor extends Interceptor {
  /**
   *
   * @return true if the Interceptor requires to access the response content
   */
  default boolean requiresResponseContent() {
      return false;
  }
}
```

Please note that, in order to mitigate DoS attacks, the size of the response content available with `readContent()` is limited to 16 Mbytes.

### Configuration

Interceptors are configured programmatically with *Initializers*. See `Develop an Initializer` section for more information.

<hr>

## Best practices

### Interacting with the *HttpServerExchange* object

The helper classes `ByteArrayRequest`, `JsonRequest`, `ByteArrayResponse` and `JsonResponse` are available to make easy interacting the `HttpServerExchange` object. As a general rule, always prefer using the helper classes if the functionality you need is available.

For instance the following code snipped retrieves the request JSON content from the `HttpServerExchange`  

```java
HttpServerExchange exchange = ...;

Request request = Request.wrap(exchange);

if (request.isContentTypeJson()) {
  JsonElement content = JsonRequest.wrap(exchange).readContent();
}
```

### How to send the response

You just set the status code and the response content using helper classes `ByteArrayResponse` or `JsonResponse`. You don't need to send the response explicitly using low level `HttpServerExchange` methods, since the `ResponseSenderHandler` is in the processing chain and will do it for you.

```java
@Override
public void handleRequest(HttpServerExchange exchange) throws Exception {

  JsonResponse response = JsonResponse.wrap(exchange);

  JsonObject resp = new JsonObject();
  resp.appProperty("message", "OK")

  response.writeContent(resp);
  response.setStatusCode(HttpStatus.SC_OK);
}
```

<hr>

_Made with :heart: by [SoftInstigate](http://www.softinstigate.com/). Follow us on [Twitter](https://twitter.com/softinstigate)_.