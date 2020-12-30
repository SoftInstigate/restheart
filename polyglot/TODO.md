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

## Support Python

allow implementing interceptors and services with Python