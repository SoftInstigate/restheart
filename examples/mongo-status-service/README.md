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
