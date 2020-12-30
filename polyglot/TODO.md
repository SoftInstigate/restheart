# TODO

## service options

```javascript
{ name: 'fooSrv',
  uri: '/foo',
  secured: true, // optional
}
```

## URI

Currently it is only possible to bind a service under the PolyglotDeployer URI (default `/graal`). E.g. `/graal/test`

Is it possible to bind it to any URI, e.g. `/test`?

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

## develop npm module to simplify dealing with RESTHeart Java classes

```bash
$ npm install restheart-js
```

```javascript
const rh = require('restheart-js');

var docs = rh.collect(mclient.getDb('restheart').getCollection('foo').find());
```

## Support Python

allow implementing interceptors and services with Python