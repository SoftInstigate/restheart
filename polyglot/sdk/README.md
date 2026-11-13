# @restheart/sdk

Type declarations of the `restheart:sdk` module, the SDK of [RESTHeart](https://restheart.org) plugins written in JavaScript or TypeScript.

This package contains types only. The module itself is provided by RESTHeart to the plugins it deploys, so there is nothing to bundle and nothing that can drift between the types and the runtime: `@restheart/sdk` 9.14.0 declares what RESTHeart 9.14.0 provides.

## Install

```bash
npm install --save-dev @restheart/sdk typescript
```

In `tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "es2022",
    "module": "es2022",
    "moduleResolution": "bundler",
    "strict": true,
    "types": ["@restheart/sdk"],
    "outDir": "."
  },
  "include": ["src/**/*.mts"],
  "exclude": ["node_modules"]
}
```

A `.mts` source compiles to the `.mjs` file RESTHeart deploys. Compile with `tsc` before deploying: RESTHeart does not transpile.

## A service

```ts
import { defineService, mongo, logger, BadRequestError } from "restheart:sdk";

export default defineService({
  name: "users",
  description: "lists users",
  uri: "/users",
  contentKind: "json",
  secured: true,

  handle(req, res) {
    const limit = Number(req.query.limit ?? 100);
    if (Number.isNaN(limit)) throw new BadRequestError("wrong limit qparam");

    logger.debug("listing users for {}", req.account?.name);

    res.json(mongo.db("restheart").collection("users").find({}, { limit, sort: { _id: -1 } }).toArray());
  },
});
```

`package.json` of the plugin:

```json
{
  "name": "my-plugin",
  "rh:spi": 1,
  "rh:services": ["users.mjs"],
  "rh:interceptors": []
}
```

Copy the plugin directory into the `plugins` directory of RESTHeart, running on GraalVM or as a native image.

## What the module exports

- `defineService`, `defineInterceptor`: the declaration, the default export of a plugin module
- `Request`, `Response`: inferred from `contentKind` (`string`, `json`, `bson`, `bytes`, and `mongo`, `csv` for interceptors)
- `logger`, `crypto`, `encode`, `resource`, `bson`: utilities, always present
- `http`, `mongo`, `config`, `mail`, `providers`: capabilities, each available when the deployment has its provider; importing one that is not available fails at load time
- `HttpError`, `BadRequestError`, `UnauthorizedError`, `ForbiddenError`, `NotFoundError`, `ConflictError`: thrown from `handle`, answered with their status

The documentation is at [restheart.org/docs/framework/javascript-plugins](https://restheart.org/docs/framework/javascript-plugins).

## License

Apache License 2.0, like every RESTHeart plugin SDK artifact.
