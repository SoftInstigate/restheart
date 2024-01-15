# MongoDB serverStatus service

This example service demonstrates how to create a Web Service that retrieves and returns the JSON output of MongoDB's `serverStatus` system command. It provides a practical illustration of using dependency injection with the `@Inject` annotation to obtain the `MongoClient`.

**Default Operation:**

By default, the service executes the MongoDB command `db.runCommand({ serverStatus: 1 })`. This command fetches a comprehensive status report of the MongoDB server.

**Customizing the Output with Optional Query Parameters:**

To tailor the information included in the `serverStatus` report, you can use the optional `command` query parameter. This feature allows you to specify additional conditions, enabling you to include or exclude specific sections of the report.

**Example Usage:**

For instance, if you want to exclude the replica set status (`repl`), metrics, and locks information from the output, you can structure your HTTP request as follows:

```bash
$ http -a admin:secret -b :8080/mongoServerStatus?command='{serverStatus: 1, repl: 0, metrics: 0, locks: 0}'
```

This request will return a `serverStatus` JSON document, customized to omit the specified sections.

**Further Reading and Reference:**

For a more detailed understanding of the `serverStatus` command and its potential outputs, you can refer to MongoDB's official documentation: [MongoDB serverStatus command reference](https://docs.mongodb.com/manual/reference/command/serverStatus/).

This service is an excellent example of creating flexible and dynamic Web Services with RESTHeart, utilizing MongoDB's powerful querying capabilities.

## Building the Plugin

Use the following command to build the plugin. Ensure you are in the project's root directory before executing it:

```bash
$ ../mvnw clean package
```

## Running RESTHeart with the plugin

To run the RESTHeart with the plugin, use Docker as follows. This command maps the host's port 8080 to the container's port 8080 and mounts the build directory as a volume:

1) **Start MongoDB Container**

**For First-Time Setup:**

Use the following commands to start a MongoDB container and initialize it as a single node replica set.

```bash
$ docker run -d --name mongodb -p 27017:27017 mongo --replSet=rs0 # Launch a MongoDB container
$ docker exec mongodb mongosh --quiet --eval "rs.initiate()" # Initialize the MongoDB instance to work as a single node replica set
```

**For Subsequent Uses:**

If you've previously created the MongoDB container and just need to start it again, you can simply use the following command:

```bash
$ docker start mongodb
```

2) **Launch RESTHeart Container**

Run the RESTHeart container, linking it to the MongoDB container:

```bash
$ docker run --name restheart --rm -p "8080:8080"  -v ./target:/opt/restheart/plugins/custom softinstigate/restheart:latest
```

For more information see: [For development: run RESTHeart and open MongoDB with Docker](https://restheart.org/docs/setup-with-docker#for-development-run-restheart-and-open-mongodb-with-docker)

## Testing the Service

```bash
$ http -a admin:secret -b :8080/mongoServerStatus

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
