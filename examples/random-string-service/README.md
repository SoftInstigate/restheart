# randomString service

This example shows how to add an external dependency to the classpath so that a plugin can use it.

> By *external dependency* we mean a dependency that is not included in restheart.jar, thus it must be added to the classpath for the service to work.

**To add an external dependency to the classpath just copy it into the directory `plugins`.**

This service just returns a random string on GET requests and uses an external dependency, Apache Commons Lang.

It uses the `maven-dependency-plugin` to copy the jar of the external dependency to `target/lib`.

Tip: You should make sure not to copy jars to the directory plugins that are already included in restheart.jar. To list of all dependencies included in restheart.jar, you can use Maven:

```bash
$ ./mvnw dependency:tree
```

### Deploy

Copy both the service JAR `target/random-string-service.jar `and `target/lib/commons-lang3-3.10.jar` into `../restheart/plugins/` folder

## Test

We suggest using [httpie](https://httpie.org) for calling the API from command line, or just use your [browser](http://localhost:8080/status).

```http
$ http localhost:8080/rndStr

HTTP/1.1 200 OK
Access-Control-Allow-Credentials: true
Access-Control-Allow-Origin: *
Access-Control-Expose-Headers: Location, ETag, Auth-Token, Auth-Token-Valid-Until, Auth-Token-Location, X-Powered-By
Connection: keep-alive
Content-Encoding: gzip
Content-Length: 30
Content-Type: application/txt
Date: Sun, 10 May 2020 15:18:55 GMT
X-Powered-By: restheart.org

xdGtToDjid
```
