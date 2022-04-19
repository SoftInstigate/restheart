# Instance Name

RESTHeart configuration file allows defining an instance name:

```yml
#### Instance name

 # The name of this instance.
 # Displayed in log, also allows to implement instance specific custom code

instance-name: default
```

This interceptor returns the instance name in the `X-Restheart-Instance` response header on requests handled by the `MongoService`.

## How to build and run

You need **JDK 17++** to build and run this example.

-   Clone this repo `git clone git@github.com:SoftInstigate/restheart-examples.git`
-   `cd` into the `restheart-examples` folder
-   [Download RESTHeart](https://github.com/SoftInstigate/restheart/releases/)
-   uncompress it: `unzip restheart.zip` or `tar -xvf restheart.tar.gz`.

### With Docker

If you have __docker__ running, then just executing the `run.sh` shell script will:

1. Build the JAR with Maven
1. Copy the JAR file into `../restheart/plugins/` folder
1. Starts a volatile MongoDB docker container
1. Starts RESTHeart Java process on port 8080

When finished testing, send a `CTRL-C` command to: stop the script, kill RESTHeart and clean-up the MongoDB container.

### Without Docker

1. Build the plugin with `../mvnw package`.
1. Copy the built JAR into the plugins folder `cp target/instance-name.jar ../restheart/plugins/`.
1. Start MongoDB in your localhost.
1. cd into the restheart distribution you have previously downloaded and uncompressed.
1. Start the process: `java -jar restheart.jar etc/restheart.yml -e etc/default.properties`.

## Test GET

```http
$ http -a admin:secret :8080/

HTTP/1.1 200 OK
Access-Control-Allow-Credentials: true
Access-Control-Allow-Origin: *
Access-Control-Expose-Headers: Location, ETag, Auth-Token, Auth-Token-Valid-Until, Auth-Token-Location, X-Powered-By
Connection: keep-alive
Content-Encoding: gzip
Content-Length: 34
Content-Type: text/plain
Date: Mon, 13 Apr 2020 13:32:52 GMT
X-Powered-By: restheart.org
X-Restheart-Instance: default   <========

(response content omitted)
```