# JavaScript Example plugin

RESTHeart can execute JavaScript plugins when running on the GraalVM.

This plugin includes 6 services:

- hello.mjs                         simple hello world service
- sub/hello.mjs                     simple hello world service in a subdirectory
- http-client.mjs                   service that shows how to use the Java HttpClient from JS code
- require-module-service.mjs        service with a require module.JS code
- mclient-service.mjs               service that shows how to use the Java MongoClient from JS code
- test.ts                           simple hello world service in TypeScript. Transpiled to test.mjs

 and 3 interceptors:

- mongo-post-coll-interceptor.mjs   intercept POST requests handled by the MongoService
- mongo-get-doc-interceptor.mjs     intercept GET requests handled by the MongoService
- hello-interceptor.mjs             intercept the service hello.mjs modifying the response

You can notice that some plugins have the `.mjx` extension. You can use the usual `.js` extension for JavaScript plugins. However, if one of the plugins use `require` than GraalVM imposes to use the `.mjx` extension. In this example, the plugin `require-module-service.mjs` uses `require`, than we use the `.mjs` extension.

## Install dependencies

```bash
npm install
```

## Running RESTHeart with the plugin

To run the RESTHeart with the plugin, use Docker as follows. This command maps the host's port 8080 to the container's port 8080 and mounts the build directory as a volume:

1) **Start MongoDB Container**

**For First-Time Setup:**

Use the following commands to start a MongoDB container and initialize it as a single node replica set.

```bash
docker run -d --name mongodb -p 27017:27017 mongo --replSet=rs0 # Launch a MongoDB container
docker exec mongodb mongosh --quiet --eval "rs.initiate()" # Initialize the MongoDB instance to work as a single node replica set
```

**For Subsequent Uses:**

If you've previously created the MongoDB container and just need to start it again, you can simply use the following command:

```bash
docker start mongodb
```

2) **Launch RESTHeart Container**

Run the RESTHeart container, linking it to the MongoDB container:

Note that we are using the RESTHart native image that supports js plugins. Alternatively you can use the GraalVM image (docker tag `softinstigate/restheart:latest-graalvm`).

Ensure you are in the project's root directory before executing it.

```bash
docker run --name restheart --rm -p "8080:8080" -v .:/opt/restheart/plugins/custom softinstigate/restheart:latest-native
```

For more information see: [For development: run RESTHeart and open MongoDB with Docker](https://restheart.org/docs/setup-with-docker#for-development-run-restheart-and-open-mongodb-with-docker)

## Testing the Service

Invoke service `/hello` (whose execution involves an interceptors):

```bash
http -b :8080/hello
{
    "msg": "Hello World! from Italy with Love",
    "note": "'from Italy with Love' was added by 'helloWorldInterceptor' that modifies the response of 'helloWorldService'"
}
```