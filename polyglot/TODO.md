# TODO

## service options

```javascript
{ name: 'fooSrv',
  uri: '/foo',
  secured: true, // optional
  type: 'JsonService',  // optional
}
```

## Allow using different Service implementations

- ByteArrayService
- JsonService
- BsonService
- MongoService
- custom service

## URI

Currently it is only possible to bind a service under the PolyglotDeployer URI (default `/graal`). E.g. `/graal/test`

Is it possible to bind it to any URI, e.g. `/test`?

## Interceptors

allow implementing interceptors with JavaScript

```javascript
{ name: 'fooSrv',
  uri: '/foo',
  secured: true, // optional
  type: 'JsonIntercepto',  // optional
}
```

## MongoClient

Add the MongoClient to the js bindings

## Karate tests

define test cases

## develop npm module to simplify dealing with RESTHeart Java classes

```bash
$ npm install restheart-js
```

```javascript
const rh = require('restheart-js');

const request = rh.toObject(request.getContent());

mclient.getDb('restheart').getCollection('foo').find();

response.setContent(rh.toJsonElement({ msg: `Hello ${request.name}` }));
```

## Support Python

allow implementing interceptors and services with Python