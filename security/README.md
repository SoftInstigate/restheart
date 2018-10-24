# &#181;IAM

&#181;IAM (micro-IAM) is a micro-gateway for **Identity and Access Management** designed for micro-services architectures.

It acts as a reverse proxy in front of resources to be protected providing Authentication and Authorization services.

 &#181;IAM enables developers to configure security policies in standardized micro-gateway instances that are external to APIs and micro-services implementation avoiding coding security functions and a centralized gateway where scalability is a key concern.

> Think about &#181;IAM as the brick that you put in front of your APIs and micro-services to protect them. 

# Main features

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
