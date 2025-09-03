# CSV Interceptor

This plugin features two sophisticated Interceptors, each designed to enhance specific functionalities within the RESTHeart platform:

1. **`csvRepresentationTransformer`**: This interceptor is adept at transforming responses from the `mongoService` into a CSV format. It leverages the capabilities of the `mongoService` to fetch data, which it then efficiently converts into a structured CSV representation. This transformation is particularly useful for applications requiring data in a universally compatible, tabular format.

2. **`coordsToGeoJson`**: Serving a unique purpose, this interceptor captures requests directed to the `csvLoader` service. It specializes in converting coordinates provided in CSV format into a GeoJSON object. This conversion is pivotal for operations involving geographical data processing and visualization. This interceptor is an exemplary illustration of data transformation in a CSV upload context. For an in-depth understanding, the [Upload CSV files](https://restheart.org/docs/csv/) documentation provides comprehensive insights into its functionality.

The essence of this plugin lies in its ability to demonstrate the implementation of Interceptors that modify both requests and responses within the RESTHeart framework. It incorporates two distinct Interceptors:

- One that applies to the `csvLoader` service, and
- Another that interfaces with the `MongoService`.

To effectively intercept a request processed by a service, an interceptor must implement an interface whose generic types align with those of the service. For instance:

- The `csvRepresentationTransformer` employs the `MongoInterceptor` interface, aligning with the `MongoService` types, to intercept requests processed by the `MongoService`.
- Conversely, the `coordsToGeoJson` interceptor implements the `Interceptor<BsonFromCsvRequest, BsonResponse>` interface, perfectly matching the `CsvLoader` service's types. This strategic implementation allows it to seamlessly intercept and transform requests directed to the `CsvLoader` service.

In summary, this plugin not only provides practical tools for data transformation and handling within RESTHeart but also serves as a valuable guide for creating effective Interceptors tailored to specific service types.

## Building the Plugin

Use the following command to build the plugin. Ensure you are in the project's root directory before executing it:

```bash
../mvnw clean package
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

```bash
docker run --name restheart --rm -p "8080:8080"  -v ./target:/opt/restheart/plugins/custom softinstigate/restheart:latest
```

For more information see: [For development: run RESTHeart and open MongoDB with Docker](https://restheart.org/docs/setup-with-docker#for-development-run-restheart-and-open-mongodb-with-docker)

## Testing the Service

Example output with the `csvRepresentationTransformer` in action

1) create a collection and some documents

```bash
http -a admin:secret PUT :8080/coll # create collection coll
echo '[{"_id": 1, "msg": "foo" }, {"_id":2, "msg": "bar"}]' | http -a admin:secret :8080/coll # create two documents
```

2) get data in `text/csv` format:

```bash
http -b -a admin:secret :8080/coll?csv

_id,msg,_etag
 2, "bar", {"$oid": "65a50401f704d70ed2138b12"}
 1, "foo", {"$oid": "65a50401f704d70ed2138b12"}
```