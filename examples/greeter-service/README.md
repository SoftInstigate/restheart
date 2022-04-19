# Greeter Service

A basic Java REST Web service implemented with few lines of code. It implements the `JsonService` interface and returns `application/json` content.

GET request, streamlined version:

```java
@RegisterPlugin(name = "greetings", description = "just another Hello World")
public class GreeterService implements JsonService {
    @Override
    public void handle(JsonRequest req, JsonResponse res) {
        switch(req.getMethod()) {
            case GET -> res.setContent(object().put("message", "Hello World!"));
            case OPTIONS -> handleOptions(req);
            default -> res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
        }
    }
}
```

For example:

```http
GET /greetings HTTP/1.1
```

Returns

```json
{
    "message": "Hello World!"
}
```

## How to build and run

build

```
$ mvn clean package
```

deploy

```
$ cp target/greeter-service.jar <restheart-dir>/plugins
```

then start, or restart RESTHeart!

you'll see the following startup log message, confirming that the plugin has been deployed.

```
 12:18:08.984 [main] INFO  org.restheart.Bootstrapper - URI /greetings bound to service greetings, secured: false, uri match PREFIX
```