# &#181;IAM

&#181;IAM (micro-IAM) is a micro-gateway for **Identity and Access Management** designed for micro-services architectures.

It acts as a reverse proxy in front of resources to be protected providing Authentication and Authorization services.

 &#181;IAM enables developers to configure security policies in standardized micro-gateway instances that are external to APIs and micro-services implementation avoiding coding security functions and a centralized gateway where scalability is a key concern.

> Think about &#181;IAM as the brick that you put in front of your APIs and micro-services to protect them. 

# Main features

- Identity and Access Management at HTTP protocol level
- Placement within the container AND on the network layer
- Basic and Token Authentication
- Roles based Authorization
- Extensible and Configurable
- Solid multi-threaded, no blocking architecture
- High performance
- Small memory footprint
- Straightforward configuration

# Use cases

## &#181;IAM on the network layer

The following diagram shows a single instance of &#181;IAM placed on the network layer in front of the resources to be protected. It acts as a centralized security policy enforcer.

![Alt text](readme-assets/uiam-on-network-layer.png?raw=true "uIAM on the network layer")

## &#181;IAM within containers

The following diagram shows &#181;IAM used as a sidecar proxy within each container pod. Each micro-service is protected by an instance of &#181;IAM with its own security policy.

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
proxy-mounts:
    - internal-uri: /api
      external-url: https://10.0.0.1/api
    - internal-uri: /
      external-url: https://10.0.0.2/website
```

As a result, the URLs `https://domain.io` and `https://domain.io/api` are proxied to the internal resources. All requests from the external network pass through &#181;IAM that enforces authentication and authorization.

```
GET https://domain.io/index.html
HTTP/1.1 401 Unauthorized

GET https://domain.io/api/entities/1233
HTTP/1.1 401 Unauthorized

```

With the default configuration &#181;IAM uses the Basic Authentication scheme with credentials and permission defined in `security.yml` configuration file:

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
      predicate: path-prefix[path=/api] and (method[GET] or  method[POST])
```

```bash
GET https://domain.io/index.html Authorization:"Basic dXNlcjpzZWNyZXQ="
HTTP/1.1 200 OK
...

GET https://domain.io/api/entities/1233 Authorization:" Basic dXNlcjpzZWNyZXQ="
HTTP/1.1 200 OK
...
```

# Setup

## With Docker

> work in progress

## Using stable binaries

> work in progress

## Building from source

> work in progress

# Understanding &#181;IAM

## Identity Manager

> work in progress

## Access Manager

> work in progress

## Authentication Manager

> work in progress

## Application Logic

> work in progress

# Configuration

> work in progress

# Available Modules Implementations

## SimpleFileIdentityManager

> work in progress

## SimpleAccessManager

> work in progress

## BasicAuthenticationManager

> work in progress

# The auth token

> work in progress

# Extensions 

## Package code

> work in progress

## Develop an Identity Manager

> work in progress

## Develop an Access Manager

> work in progress

## Develop an Authentication Manager

> work in progress

## Develop Application Logic

> work in progress

<hr>

_Made with :heart: by [SoftInstigate](http://www.softinstigate.com/). Follow us on [Twitter](https://twitter.com/softinstigate)_.
