# Hello world example

A basic Java REST Web service implemented with few lines of code. It implements the `ByteArrayService` interface and returns `text/plain` content.

GET request, streamlined version:

```java
@RegisterPlugin(
        name = "hello",
        defaultURI = "/hello")
public class HelloByteArrayService implements ByteArrayService {
    @Override
    public void handle(ByteArrayRequest request, ByteArrayResponse response) throws Exception {
        String message = "Hello " + request.getExchange().getQueryParameters().get("name").getFirst();
        response.setStatusCode(200);
        response.setContent(message.getBytes());
        response.setContentType("text/plain");
    }
}
```

See the [complete source code](src/main/java/org/restheart/examples/HelloByteArrayService.java).

For example:

```http
GET /hello HTTP/1.1
```

Returns `Hello, Anonymous`.

```http
GET /hello?name=Andrea HTTP/1.1
```

Or

```http
POST /hello HTTP/1.1

Andrea
```

Both returns `Hello, Andrea`.

Using any other HTTP verb returns a "HTTP 400 - Bad request"  error.