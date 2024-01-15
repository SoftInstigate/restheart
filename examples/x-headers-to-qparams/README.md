# x-headers-to-qparams Interceptor for Header-to-Query Parameter Mapping

This interceptor, named `x-headers-to-qparams`, offers a convenient way to convert specific request headers into query parameters, showing how to dynamically modify the request before it reaches the designated handling Service.

The interceptor scans the incoming request for any headers that start with X-RH-. Upon finding such headers, it automatically adds corresponding query parameters to the request.

The interceptor also adds all the headers starting with `X-RH-` in the request to the response header `Access-Control-Expose-Headers` for CORS support.

```bash
$ http -a admin:secret OPTIONS :8080/coll X-RH-filter:'{"a":1}'
HTTP/1.1 200 OK

Access-Control-Expose-Headers: Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, If-Match, Origin, X-Requested-With, User-Agent, No-Auth-Challenge,X-RH-filter <----
(other headers omitted)
```

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
$ docker run mongodb
```

2) **Launch RESTHeart Container**

Run the RESTHeart container, linking it to the MongoDB container:

```bash
$ docker run --name restheart --rm -p "8080:8080"  -v ./target:/opt/restheart/plugins/custom softinstigate/restheart:latest
```

For more information see: [For development: run RESTHeart and open MongoDB with Docker](https://restheart.org/docs/setup-with-docker#for-development-run-restheart-and-open-mongodb-with-docker)

## Testing the Service

The MongoService handles the request `GET /coll?filter={"foo":"bar}` using the query parameter `filter` to execute the specified MongoDb query.

Using this plugin, you can pass the query using the request header `X-RH-filter`

```bash
$ http -a admin:secret PUT :8080/coll # create collection coll
$ echo '[{"foo":"bar"},{"foo":"zap"}]' | http -a admin:secret :8080/coll # create two documents
$ http -a admin:secret :8080/coll X-RH-filter:'{"foo":"bar"}' X-RH-keys:'{"foo":1,"_id":0}' # get filtering via x-rh-filter and projecting via x-rh-keys headers

[
    {
        "foo": "bar"
    }
]
```