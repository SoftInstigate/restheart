# Credit Card Interceptor

A JavaScript Interceptor that hides credit card numbers from GET request to the MongoDB `restheart.creditcards` collection.

Note that `package.json` defines the interceptor `cc-hider.js`.

```json
"rh:interceptors": [
    "cc-hider.js"
  ]
```

In the RESTHeart logs you'll see something like:

```
 11:24:31.009 [Thread-1] INFO  o.r.polyglot.PolyglotDeployer - Added interceptor ccHider, description: hides credit card numbers
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

Note that we are using the RESTHart native image that supports js plugins. Alternatively you can use the GraalVM image (docker tag `softinstigate/restheart:latest-graalvm`).

Ensure you are in the project's root directory before executing it.

```bash
$ docker run --name restheart --rm -p "8080:8080" -v .:/opt/restheart/plugins/custom softinstigate/restheart:latest-native
```

For more information see: [For development: run RESTHeart and open MongoDB with Docker](https://restheart.org/docs/setup-with-docker#for-development-run-restheart-and-open-mongodb-with-docker)

## Testing the Interceptor

### Create test data

We use httpie

```bash
$ http -a admin:secret PUT :8080/creditcards
$ http -a admin:secret POST :8080/creditcards cc=1234-0000-5555-0001
$ http -a admin:secret POST :8080/creditcards cc=1234-0000-5555-0002
```

### See it in action

```bash
$ http -b -a admin:secret :8080/creditcards
[
    {
        "_etag": {
            "$oid": "60dae4b8a16b227e471d96f1"
        },
        "_id": {
            "$oid": "60dae4b8a16b227e471d96f2"
        },
        "cc": "****-****-****-0002"
    },
    {
        "_etag": {
            "$oid": "60dae4b6a16b227e471d96ef"
        },
        "_id": {
            "$oid": "60dae4b6a16b227e471d96f0"
        },
        "cc": "****-****-****-0001"
    }
]
```
