# Credit Card Interceptor

A JavaScript Interceptor that hides credit card numbers

You need to run RESTHeart with the GraalVM to execute this interceptor.

Check the java version to be **GraalVM CE 22.0.0.2**

```
$ java  -version
openjdk version "17.0.2" 2022-01-18
OpenJDK Runtime Environment GraalVM CE 22.0.0.2 (build 17.0.2+8-jvmci-22.0-b05)
OpenJDK 64-Bit Server VM GraalVM CE 22.0.0.2 (build 17.0.2+8-jvmci-22.0-b05, mixed mode, sharing)
```

## Deploy

```
$ cp -r credit-card-hider <RH_HOME>/plugins && touch <RH_HOME>/plugins/credit-card-hider
```

Note that `package.json` defines `cc-hider.js` as an interceptor

```json
"rh:interceptors": [
    "cc-hider.js"
  ]
```

For a service use the following declaration:

```json
"rh:services": [
    "my-service.js"
  ],
```

In the RESTHeart logs you'll see something like:

```
 11:24:31.009 [Thread-1] INFO  o.r.polyglot.PolyglotDeployer - Added interceptor ccHider, description: hides credit card numbers
```

## Create test data

We use httpie

```bash
$ http -a admin:secret PUT :8080/credicards
$ http -a admin:secret POST :8080/credicards cc=1234-0000-5555-0001
$ http -a admin:secret POST :8080/credicards cc=1234-0000-5555-0002
```

## See it in action

```bash
$ http -b -a admin:secret :8080/creditcards
[
    {
        "_etag": {
            "$oid": "60dae4b8a16b227e471d96f1"
        },
        "_id": {
            "$oid": "60dae4b8a16b227e471d96f2"
        },
        "cc": "****-****-****-0002"
    },
    {
        "_etag": {
            "$oid": "60dae4b6a16b227e471d96ef"
        },
        "_id": {
            "$oid": "60dae4b6a16b227e471d96f0"
        },
        "cc": "****-****-****-0001"
    }
]
```
