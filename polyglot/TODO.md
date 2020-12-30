# TODO

## Interceptors

allow implementing interceptors with JavaScript

```javascript
{ name: 'fooInterceptor',
  interceptPoint: 'REQUEST_AFTER_AUTH', // optional
  secured: true, // optional
  serviceClass: 'org.restheart.plugins.JsonService'
}
```

## Karate tests

define test cases

## simplify dealing with RESTHeart Java classes

functions to simplify using Gson and Bson classes

```javascript
const rh = require('restheart-js');

var docs = rh.collect(mclient.getDb('restheart').getCollection('foo').find());
```

### option one: create an npm installable module

```bash
$ npm install restheart-js
```

### option two: use an initialization script

```java
// initialization script to pre-define globals.
options.put("js.commonjs-global-properties", "./globals.js");
```

See [Global symbols pre-initialization](https://github.com/oracle/graaljs/blob/master/docs/user/NodeJSVSJavaScriptContext.md#global-symbols-pre-initialization)

## allow mockup node modules

Some JavaScript applications or NPM modules might need functionalities that are available in Node.js' built-in modules (e.g., 'fs' and 'buffer', etc.). Such modules are not available in the Context API. Thankfully, the Node.js community has developed high-quality JavaScript implementations for many Node.js core modules (e.g., the 'buffer' module for the browser). Such alternative module implementations can be exposed to a JavaScript Context using the `js.commonjs-core-modules-replacements` option

See [Node.js core modules mockups](https://github.com/oracle/graaljs/blob/master/docs/user/NodeJSVSJavaScriptContext.md#nodejs-core-modules-mockups)

## Support Python

allow implementing interceptors and services with Python