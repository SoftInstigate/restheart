# Node.js Example plugin

RESTHeart can be executed on GraalVM's implementation of Node.js

This allows to develop plugins in JavaScript leveraging the Node runtime. For instance
it is possible to use the `http` Node module.

RESTHeart is designed to be fully compatible with GraalVM, particularly its implementation of Node.js. This compatibility offers significant advantages, especially for developers looking to write plugins in JavaScript. By leveraging the Node.js runtime environment provided by GraalVM, developers can:

- Utilize Node.js Modules: You can easily integrate Node.js modules into your plugins. For example, the http module from Node.js can be seamlessly used within RESTHeart. This opens up a range of possibilities for HTTP networking and server-side functionalities in your plugins.

- Leverage JavaScript Ecosystem: Developers can take advantage of the vast JavaScript ecosystem, including numerous libraries and tools, to build more powerful and feature-rich plugins for RESTHeart.

NOTE: executing RESTHeart on GraalVM Node is experimental and not suggested for production.

## Install RESTHeart

We assume that the latest version of RESTHeart is installed in the directory <RH_HOME>

We also assume that the RESTHeart repository is available in the directory <RH_REPO>

```bash
$ cd <RH_REPO>
$ git clone --depth 1 git@github.com:SoftInstigate/restheart.git
```

## Install GraalVM

(here we use the brilliant sdkman)

```bash
The easiest way to install GraalVM is using [sdkman](https://sdkman.io/)

```bash
$ sdk install java 24.0.0-graalce
$ sdk use java 24.0.0-graalce
```
```

## Get the script `restheart.js`

```
$ cd <RH_HOME>
$ curl https://raw.githubusercontent.com/SoftInstigate/restheart/polyglot/src/js/restheart.js > restheart.js
```

Where <RH_HOME> is the RESTHeart installation directory.

## Install GraalVM Node

Check [Getting Started with Node.js](https://www.graalvm.org/latest/reference-manual/js/NodeJS/#getting-started-with-nodejs) in GraalVM documentation.

## Run RESTHeart on Node

```bash
$ <graalvm_install_dir>/bin/node --jvm --vm.cp=restheart.jar restheart.js
```

## Deploy the example Node Plugin

Just copy the directory `node-plugin` into `<RH_HOME>/plugins`:

```bash
$ cp -r <RH_REPO>/examples/node-plugin <RH_HOME>/plugins
```

This plugin includes two services `/hello` and `/promise`

```log
INFO  o.r.polyglot.PolyglotDeployer - URI /hello bound to service helloWorldService, description: just another Hello World, secured: false, uri match EXACT
INFO  o.r.polyglot.PolyglotDeployer - URI /promise bound to service nodePromiseSrv, description: just an example node service that requires http and returns a promise, secured: false, uri match PREFIX
```

```bash
$ http -b :8080/hello

Hello World!
```

```bash
$ http -b :8080/promise
{
    "anything": {
        "args": {},
        "data": "",
        "files": {},
        "form": {},
        "headers": {
            "Host": "httpbin.org",
            "X-Amzn-Trace-Id": "Root=1-6388b49c-48fd21523e1545e339d44fee"
        },
        "json": null,
        "method": "GET",
        "origin": "93.42.100.18",
        "url": "http://httpbin.org/anything"
    },
    "msg": "Hello World"
}
```