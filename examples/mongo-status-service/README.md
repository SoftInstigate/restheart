# MongoDB serverStatus service

This example service implements the `serverStatus` MongoDB system call.

By default, it runs `db.runCommand( { serverStatus: 1 } )`

```http
GET /status HTTP/1.1
```

Use the optional `command` query parameter to specify additional conditions. For example, the following operation suppresses the repl, metrics and locks information in the output.

```http
GET /status?command='{serverStatus: 1, repl: 0, metrics: 0, locks: 0}' HTTP/1.1

```

It returns the `serverStatus` JSON document.

See https://docs.mongodb.com/manual/reference/command/serverStatus/

## How to build and run

You need **JDK 17++** to build and run this example.

-   Clone this repo `git clone git@github.com:SoftInstigate/restheart-examples.git`
-   `cd` into the `restheart-examples` folder
-   [Download RESTHeart](https://github.com/SoftInstigate/restheart/releases/)
-   uncompress it: `unzip restheart.zip` or `tar -xvf restheart.tar.gz`.

### With Docker

If you have **docker** running, then just executing the `run.sh` shell script will:

1. Build the JAR with Maven
1. Copy the JAR file into `../restheart/plugins/` folder
1. Starts a volatile MongoDB docker container
1. Starts RESTHeart Java process on port 8080

When finished testing, send a `CTRL-C` command to: stop the script, kill RESTHeart and clean-up the MongoDB container.

### Without Docker

1. Build the plugin with `../mvnw package`.
1. Copy the built JAR into the plugins folder `cp target/mongo-status-service.jar ../restheart/plugins/`.
1. Start MongoDB in your localhost.
1. cd into the restheart distribution you have previously downloaded and uncompressed.
1. Start the process: `java -jar restheart.jar etc/restheart.yml -e etc/default.properties`.

## Test

We suggest using [httpie](https://httpie.org) for calling the API from command line, or just use your [browser](http://localhost:8080/status).

```http
$ http localhost:8080/status

HTTP/1.1 200 OK
Access-Control-Allow-Credentials: true
Access-Control-Allow-Origin: *
Access-Control-Expose-Headers: Location, ETag, Auth-Token, Auth-Token-Valid-Until, Auth-Token-Location, X-Powered-By
Connection: keep-alive
Content-Encoding: gzip
Content-Length: 8192
Content-Type: application/json
Date: Thu, 16 Apr 2020 16:54:32 GMT
X-Powered-By: restheart.org

{
    "asserts": {
        "msg": 0,
        "regular": 0,
        "rollovers": 0,
        "user": 2,
        "warning": 0
    },
    "connections": {
        "active": 1,
        "available": 838858,
        "current": 2,
        "totalCreated": 2
    },
    "electionMetrics": {
        "averageCatchUpOps": 0.0,
        "catchUpTakeover": {
            "called": {
                "$numberLong": "0"
            },
            "successful": {
                "$numberLong": "0"
            }
        },
        "electionTimeout": {
            "called": {
                "$numberLong": "0"
            },
            "successful": {
                "$numberLong": "0"
            }
        },

    ...

    }
}

```
