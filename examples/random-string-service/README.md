# randomString service

This example shows how to add an external dependency to the classpath so that a plugin can use it.

> By *external dependency* we mean a dependency that is not included in restheart.jar, thus it must be added to the classpath for the service to work.

**To add an external dependency to the classpath just copy it into the directory `plugins`.**

This service just returns a random string on GET requests and uses an external dependency, Apache Commons Lang.

Make sure not to copy jars to the directory plugins that are already included in restheart.jar. To list of all dependencies included in restheart.jar, you can use Maven: 
- clone restheart
- `cd restheart/core`
- `./mvnw dependency:tree`

## How to build and run

You need **JDK 17++** to build and run this example.

-   Clone this repo `git clone git@github.com:SoftInstigate/restheart-examples.git`
-   `cd` into the `restheart-examples` folder
-   [Download RESTHeart](https://github.com/SoftInstigate/restheart/releases/)
-   uncompress it: `unzip restheart.zip` or `tar -xvf restheart.tar.gz`.

### Run

1. `cd random-string-service`
1. Build the plugin with `../mvnw package` (uses the maven-dependency-plugin to copy the jar of the external dependency to /target/lib)
1. Copy both the service JAR `target/random-string-service.jar `and `target/lib/commons-lang3-3.10.jar` into `../restheart/plugins/` folder
1. Start MongoDB in your localhost.
1. cd into the restheart distribution you have previously downloaded and uncompressed.
1. Start the process: `java -jar restheart.jar etc/restheart.yml -e etc/default.properties`.

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
