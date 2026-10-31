# JavaScript and TypeScript example plugins

RESTHeart runs plugins written in JavaScript when it runs on GraalVM, or as a native image. These examples are written in TypeScript on the `restheart:sdk` module, with the types of [`@restheart/sdk`](https://www.npmjs.com/package/@restheart/sdk).

The sources are in `src/*.mts`. `tsc` compiles each one to the `.mjs` file next to `package.json`, which lists the plugins in `rh:services` and `rh:interceptors`. RESTHeart deploys the `.mjs` files: it does not transpile.

| source | what it shows |
|---|---|
| `src/hello.mts` | a json service, `req.query`, `res.json()`, `res.headers`, `res.statusCode` |
| `src/sub/hello.mts` | a plugin in a subdirectory |
| `src/hello-interceptor.mts` | a json interceptor on the response of a service, `res.content` as native data |
| `src/users.mts` | the `mongo` capability, `config`, `logger`, a typed error |
| `src/http-client.mts` | the `http` capability, blocking, `ok === false` on a 4xx |
| `src/webhook.mts` | `crypto.hmacSha256` and `crypto.timingSafeEqual` on a signed payload |
| `src/mongo-get-doc-interceptor.mts` | a `mongo` interceptor on the response of `GET /coll/<docid>`, `req.mongo` |
| `src/mongo-post-coll-interceptor.mts` | a `mongo` interceptor on the request of `POST /coll`, a native `Date` written as a BSON date |
| `src/require-module-service.mts` | a pure JavaScript npm package from `node_modules` |

## Build

```bash
npm install
npm run build
```

`npm run watch` recompiles on every change; RESTHeart redeploys a plugin when its `.mjs` file changes.

## Run RESTHeart with the plugins

Start MongoDB as a single node replica set, once:

```bash
docker run -d --name mongodb -p 27017:27017 mongo --replSet=rs0
docker exec mongodb mongosh --quiet --eval "rs.initiate()"
```

Then RESTHeart, with this directory mounted in its `plugins` directory. The native image supports JavaScript plugins; the GraalVM image (`softinstigate/restheart:latest-graalvm`) does too.

```bash
docker run --name restheart --rm -p "8080:8080" -v .:/opt/restheart/plugins/custom softinstigate/restheart:latest-native
```

The `users` and `webhook` services read their configuration from `restheart.yml`, by plugin name:

```yaml
usersService:
  db: restheart
  collection: users

webhookService:
  secret: the-signing-secret-of-the-provider
```

## Try it

```bash
http -b :8080/hello
{
    "msg": "Hello World! from Italy with Love",
    "note": "'from Italy with Love' was added by 'helloWorldInterceptor' that modifies the response of 'helloWorldService'"
}

http -b -a admin:secret :8080/users limit==2
```

The documentation of the SDK is at [restheart.org/docs/framework/javascript-plugins](https://restheart.org/docs/framework/javascript-plugins).
