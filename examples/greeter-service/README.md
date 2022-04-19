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