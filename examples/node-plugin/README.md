# Node.js Example plugin

RESTHeart can be executed on GraalVM's implementation of Node.js

This allows to develop plugins in JavaScript leveraging the Node runtime. For instance
it is possible to use the `http` Node module.

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
$ sdk install java 22.3.r17-grl
$ sdk use java 22.3.r17-grl
$ gu install nodejs
```

## Get the script `restheart.js`

```
$ cd <RH_HOME>
$ curl https://raw.githubusercontent.com/SoftInstigate/restheart/polyglot/src/js/restheart.js > restheart.js
```

Where <RH_HOME> is the RESTHeart installation directory.

## Run RESTHeart on Node

```bash
$ $(sdk home java 22.3.r17-grl)/bin/node --jvm --vm.cp=restheart.jar restheart.js
```

(we use `sdk home` to make sure to use the GraalVM's node implementation)

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