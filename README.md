# RESTHeart - The Runtime for Microservices with Declarative Security and Instant API on MongoDB

[![GitHub last commit](https://img.shields.io/github/last-commit/softinstigate/restheart)](https://github.com/SoftInstigate/restheart/commits/master)
[![Github stars](https://img.shields.io/github/stars/SoftInstigate/restheart?label=Github%20Stars)](https://github.com/SoftInstigate/restheart)
[![Maven Central](https://img.shields.io/maven-central/v/org.restheart/restheart.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22org.restheart%22%20AND%20a:%22restheart%22)
[![Docker Pulls](https://img.shields.io/docker/pulls/softinstigate/restheart.svg?maxAge=2592000)](https://hub.docker.com/r/softinstigate/restheart/)
[![Join the chat at https://gitter.im/SoftInstigate/restheart](https://badges.gitter.im/SoftInstigate/restheart.svg)](https://gitter.im/SoftInstigate/restheart?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

## Table of Contents

-   [Introduction](#introduction)
-   [Build](#build)
-   [Documentation](#documentation)
-   [License and support options](#license-and-support-options)
-   [Sponsors](#sponsors)

## Introduction

RESTHeart is a modern Runtime for Microservices, designed to radically simplify server-side development and deployment.

RESTHeart provides out-of-the-box:

1. Polyglot Development Framework supporting Java, Kotlin, JavaScript and TypeScript
2. Data persistence on MongoDB with REST, GraphQL and WebSocket APIs
3. Secure Identity and Access Management

## Download

Download prebuilt packages from [releases](https://github.com/SoftInstigate/restheart/releases)

Find setup instructions at [Setup](https://restheart.org/docs/setup/) documentation page.

## Build

> Building RESTHeart 6.0 requires JDK 16!

```bash
$ mvn clean package
```

You can then run it with (make sure to have mongod running):

```bash
$ java -jar core/target/restheart.jar core/etc/restheart.yml -e core/etc/default.properties
```

## Documentation

The full documentation is available [here](https://restheart.org/docs/).

You can also have a look at our [introductory video](https://youtu.be/9KroH-RvjS0) on Youtube:

[![Watch the video!](https://img.youtube.com/vi/9KroH-RvjS0/hqdefault.jpg)](https://youtu.be/9KroH-RvjS0)

## License and support options

RESTHeart is __dually licensed__ under the open source [GNU Affero General Public License v3 (AGPL-3.0)](https://www.tldrlegal.com/l/agpl3) and the [RESTHeart Enterprise License](https://github.com/SoftInstigate/restheart/blob/master/COMM-LICENSE.txt).

When a company is not comfortable with the AGPL open source license or need __dedicated technical support__, then it could purchase a RESTHeart Subscription, which comes with:

- Perpetual, business-friendly Enterprise license.
- 12 months of e-mail technical support.
- 12 months of supported upgrades to any new release of the product.
- Training (optional).
- Professional Services (optional).

The Enterprise license is perpetual and overcomes some limitations of the AGPL v3. Specifically, it grants the following rights:

- Use RESTHeart in closed source applications.
- Distribute RESTHeart embedded in closed source products.
- Get coverage against some legal risks, like third-parties IP claims.

Please read more about the available [Free and Commercial Support Options](https://restheart.org/support).

You can also have a look at our [RESTHeart's presentation on Slideshare](https://www.slideshare.net/mkjsix/restheart-modern-runtime-for-microservices-with-instant-data-api-on-mongodb).

## Sponsors

<table>
  <tbody>
    <tr>
      <td align="center" valign="middle">
        <a href="https://www.softinstigate.com" target="_blank">
          <img width="222px" src="https://www.softinstigate.com/images/logo.png">
        </a>
      </td>
    </tr>
  </tbody>
</table>

---

_Made with :heart: by [SoftInstigate](http://www.softinstigate.com/). Follow us on [Twitter](https://twitter.com/softinstigate)_.
