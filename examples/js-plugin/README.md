# JavaScript Example plugin

RESTHeart can execute JavaScript plugins when running on the GraalVM

## Install RESTHeart

We assume that the latest version of RESTHeart is installed in the directory <RH_HOME>

We also assume that the RESTHeart repository is available in the directory <RH_REPO>. You can download RESTHeart repo with:

```bash
$ git clone --depth 1 git@github.com:SoftInstigate/restheart.git
```

## Install GraalVM

(here we use the brilliant sdkman)

```bash
$ sdk install java 22.3.r17-grl
$ sdk use java 22.3.r17-grl
$ gu install graalvm.mjs
```

## Run RESTHeart

```bash
$ java -jar restheart.jar
```

## Deploy the example JavaScript Plugin

Just copy the directory `js-plugin` into `<RH_HOME>/plugins`:

```bash
$ cp -r <RH_REPO>/examples/js-plugin <RH_HOME>/plugins
```

This plugin includes 6 services and 3 interceptors:

```log
INFO  o.r.polyglot.PolyglotDeployer - URI /hello bound to service helloWorldService, description: just another Hello World, secured: false, uri match EXACT
INFO  o.r.polyglot.PolyglotDeployer - URI /sub/hello bound to service anotherHelloWorldService, description: yet another Hello World, secured: false, uri match EXACT
ERROR o.r.polyglot.PolyglotDeployer - Error deploying plugin /Users/uji/development/restheart/core/target/plugins/js-plugin/require-module-service.mjs
java.lang.IllegalArgumentException: wrong js service /Users/uji/development/restheart/core/target/plugins/js-plugin/require-module-service.mjs: ReferenceError: require is not defined, the plugin module must export the object 'options', example:
export const options = {
    name: "hello"
    description: "a fancy description"
    uri: "/hello"
    secured: false
    matchPolicy: "PREFIX"
}

	at org.restheart.polyglot.JavaScriptService.<init>(JavaScriptService.java:126)
INFO  o.r.polyglot.PolyglotDeployer - URI /mclientService bound to service mclientService, description: just an example JavaScript service that uses the MongoClient, secured: true, uri match EXACT
INFO  o.r.polyglot.PolyglotDeployer - URI /httpClient bound to service httpClientService, description: a service that uses java.net.http.HttpClient to execute a GET request, secured: false, uri match EXACT
INFO  o.r.polyglot.PolyglotDeployer - URI /helloTS bound to service helloWorldTS, description: Test typescript, secured: false, uri match EXACT
INFO  o.r.polyglot.PolyglotDeployer - Added interceptor helloWorldInterceptor, description: modifies the response of helloWorldService
INFO  o.r.polyglot.PolyglotDeployer - Added interceptor mongoGetDocInteceptor, description: a js interceptor that modified the response of GET /coll/<docid>
INFO  o.r.polyglot.PolyglotDeployer - Added interceptor mongoPostCollInterceptor, description: modifies the content of POST requests adding a timestamp
```

You might notice the ERROR log. This is about the service `require-module-service.mjs` that uses `require`. To fix it, just run `npm install`:

```bash
$ cd <RH_HOME>/plugins/js-plugin
$ $(sdk home java 22.3.r17-grl)/bin/npm install
```

(we use `sdk home` to make sure to use the GraalVM's node implementation)


Invoke service `/hello` (whose execution involves an interceptors):

```bash
$ http -b :8080/hello
{
    "msg": "Hello World! from Italy with Love",
    "note": "'from Italy with Love' was added by 'helloWorldInterceptor' that modifies the response of 'helloWorldService'"
}
```

## Why `.mjs` extension

You can use the usual `.js` extension for JavaScript plugins. However, if one of the plugins use `require` than GraalVM imposes to use the `.mjx` extension. In this example, the plugin `require-module-service.mjs` uses `require`, than we use the `.mjs` extension.