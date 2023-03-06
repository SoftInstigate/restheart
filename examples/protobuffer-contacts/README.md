# Protocol Buffer Interceptors

> NOTE: this example requires RESTHeart 7+

This example includes two interceptors that allow to use the `MongoService`
with [Protocol Buffer](https://developers.google.com/protocol-buffers) over HTTP.

It shows how to transform the request and response content to and from a different format
than expected by a Service.

The example creates documents in the `/contacts` collection via a command line client that sends requests using Protocol Buffer.

The proto file is:

```proto
// The request message
message ContactPostRequest {
  string name = 1;
  string email = 2;
  string phone = 3;
}

// The response message containing the id of the created document
message ContactPostReply {
    string id = 1;
}
```

## Deploy

```bash
# copy the jar files to the RESTHeart plugin directory
$ cp target/protobuffer-contacts.jar target/lib/* <RH_HOME>/plugins

# (re)start RESTHeart in a different terminal

# create the /contacts collection
$ http -a admin:secret :8080/contacts

# allow unauthenticated client to POST to /proto
$ echo '{"_id":"openProto","predicate":"path[/proto]","roles":["$unauthenticated"],"priority":1}' | http -a admin:secret POST :8080/acl\?wm=upsert
```

## Run

```bash
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