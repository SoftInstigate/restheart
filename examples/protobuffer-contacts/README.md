# Protocol Buffer Interceptors for Enhanced Data Format Handling

This example showcases two powerful interceptors designed to enable the `MongoService` to handle data in [Protocol Buffer](https://developers.google.com/protocol-buffers) format over HTTP. Protocol Buffers (Protobuf) is a method developed by Google for serializing structured data, similar to XML or JSON but smaller, faster, and simpler.

## Functional Overview

The primary function of these interceptors is to transform the request and response content into a format different from what is typically expected by a Service. In this case, they convert data between the MongoDB's native format and Protocol Buffers.

## Application in RESTHeart

In this specific application, the interceptors facilitate the creation of documents within the `/contacts` collection. They enable a command-line client to send requests in Protocol Buffer format, making the communication more efficient and streamlined compared to traditional JSON or XML formats.

## Protocol Buffer Schema

The Protocol Buffer schema used in this example is defined as follows:

```proto
// The request message schema
message ContactPostRequest {
  string name = 1;
  string email = 2;
  string phone = 3;
}

// The response message schema containing the id of the created document
message ContactPostReply {
    string id = 1;
}
```

This schema outlines the structure for both the request (`ContactPostRequest`) and response (`ContactPostReply`). The request message includes fields for a contact's name, email, and phone, while the response message contains the unique ID of the newly created document.

## Implementation Significance

By implementing Protocol Buffers, this example not only enhances the efficiency of data transmission but also demonstrates the versatility of RESTHeart in accommodating different data serialization formats. It opens avenues for more optimized data handling, especially in scenarios where performance and bandwidth are critical.

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

Run the RESTHeart container, linking it to the MongoDB container and using the configuration override file `conf.yml`:

```bash
$ docker run --name restheart --rm -p "8080:8080" -v ./target:/opt/restheart/plugins/custom -v ./conf.yml:/opt/restheart/etc/conf.yml softinstigate/restheart:latest -o etc/conf.yml
```

For more information see: [For development: run RESTHeart and open MongoDB with Docker](https://restheart.org/docs/setup-with-docker#for-development-run-restheart-and-open-mongodb-with-docker)

## Testing the Service

```bash
# create the /contacts collection
$ http -a admin:secret :8080/contacts

# allow unauthenticated client to POST to /proto
$ echo '{"_id":"openProto","predicate":"path[/proto]","roles":["$unauthenticated"],"priority":1}' | http -a admin:secret POST :8080/acl\?wm=upsert
```

```bash
$ # send a protobuffer request
$ java -cp target/classes:target/test-classes:target/lib/\*:target/test-lib/\* org.restheart.examples.CreateContact Uji andrea@softinstigate.com "000 555 911"

response status: 201
id of new contact: {"$oid":"62619239935b6e1c117d0a56"}
```

Now you can check the created document with:

```bash
$ http -ba admin:secret :8080/contacts/62619239935b6e1c117d0a56
{
    "_etag": {
        "$oid": "62619239935b6e1c117d0a55"
    },
    "_id": {
        "$oid": "62619239935b6e1c117d0a56"
    },
    "email": "andrea@softinstigate.com",
    "name": "Uji",
    "phone": "000 555 911"
}
```

## Request transformation

When a request is received, RESTHeart first determines which service should handle it.

Each Service defines the request (and response) initializer, via the `ServiceRequest.requestInitializer()` method. The request initializer of the `MongoService` expects the request content to be valid JSON.

In order to allow the `MongoService` to be used with the Protocol Buffer format, the request content must be transformed before request initialization.

This can be achieved with a `WildcardInterceptor` with `interceptPoint=REQUEST_BEFORE_EXCHANGE_INIT`.

The `handle(request, response)` method receives an object of class `UninitializedRequest` as request argument, and `UninitializedResponse` as response argument. `UninitializedRequest.getRawContent()` and `UninitializedRequest.setRawContent()` allows to get and overwrite the request raw body.

The Interceptor sets a custom request initializer (that will be executed in place of the default request initializer defined by the MongoService) to translate the protobuf payload to BSON using the great [kotlin-protobuf-bson-codec library](https://github.com/gaplotech/kotlin-protobuf-bson-codec).


```java
var uninitializedRequest = (UninitializedRequest) request;

        uninitializedRequest.setCustomRequestInitializer(e -> {
            var req = (UninitializedRequest) request;

            try {
                // parse the protocol buffer request
                var helloReq = ContactPostRequest.parseFrom(req.getRawContent());

                // MongoRequest.init() will skip the parsing of the request content
                // and use the Bson attached to the exchange
                // with MongoServiceAttachments.attachBsonContent()
                MongoServiceAttachments.attachBsonContent(request.getExchange(), decode(helloReq));
            } catch(Throwable ex) {
                var r = MongoRequest.init(e, "/proto", "/restheart/contacts");
                r.setInError(true);
            }

            // we remap the request to the collection restheart.coll
            MongoRequest.init(e, "/proto", "/restheart/contacts");
        });
```


The custom also remaps the request URI `/proto` to the database resource `restheart.coll` using `MongoRequest.init()` that allows to specify the URI/MongoDB resource mapping.

## Response transformation

After the request has been handled by the `MongoService` it can be easily transformed by using an `MongoInterceptor` with `interceptPoint=RESPONSE`. It uses `ServiceResponse.setCustomSender()` to customize the response format.

```java
public void handle(MongoRequest request, MongoResponse response) throws Exception {
    var id = BsonUtils.toJson(response.getDbOperationResult().getNewId(), JsonMode.RELAXED);
    // transform the json to a protobuf
    var builder = ContactPostReply.newBuilder().setId(id);

    response.setCustomSender(() -> {
        try {
            response.getExchange().getResponseSender()
                .send(builder.build().toByteString().toStringUtf8());
        } catch(Throwable t) {
            LambdaUtils.throwsSneakyException(t);
        }
    });
}
```