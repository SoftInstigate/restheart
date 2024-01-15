# Greetings Services - RESTHeart Plugin

This plugin includes two straightforward Web services: `textGreeter` and `jsonGreeter`. These services generate greeting messages, with `textGreeter` delivering its message in `text/plain` format and `jsonGreeter` in `application/json` format.

The plugin serves as a practical demonstration of how to create Services by implementing various specialized `Service` interfaces. Specifically, it utilizes `JsonService` and `ByteArrayService` to manage the request and response content effectively. This implementation showcases how to interact with the Request and Response objects, retrieve query parameters, and correctly set the `Content-Type` in the response header. This approach provides a clear example of handling different content types and responses within the RESTHeart framework.

## Building the Plugin

Use the following command to build the plugin. Ensure you are in the project's root directory before executing it:

```bash
$ ../mvnw clean package
```

## Running RESTHeart with the plugin

This Plugin doesn't require MongoDB so we can run RESTHeart in standalone mode (specifying the `-s` option).

To run the RESTHeart with the plugin, use Docker as follows. This command maps the host's port 8080 to the container's port 8080 and mounts the build directory as a volume:

```bash
$ docker run --rm -p 8080:8080 -e RHO="/fileRealmAuthenticator/users[userid='admin']/password->'secret';/http-listener/host->'0.0.0.0'" -v ./target:/opt/restheart/plugins/custom softinstigate/restheart:latest -s
```

For more information see [RESTHeart with custom Plugin](https://restheart.org/docs/setup-with-docker#run-restheart-with-custom-plugin) documentation section.

## Plugin Code Overview

Here's the basic structure of the `TextGreeterService` class:

```java
@RegisterPlugin(
        name = "textGreeter",
        description = "just another text/plain Hello World")
public class TextGreeterService implements ByteArrayService {
    @Override
    public void handle(ByteArrayRequest req, ByteArrayResponse res) throws Exception {
        res.setContentType("text/plain; charset=utf-8");

        switch (req.getMethod()) {
            case OPTIONS -> handleOptions(req);

            case GET -> {
                var name = req.getQueryParameters().get("name");
                res.setContent("Hello, " + (name == null ? "World" : name.getFirst()));
            }

            case POST -> {
                var content = req.getContent();
                res.setContent("Hello, " + (content == null ? "World" : new String(content)));
            }

            default -> res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
        }
    }
}
```
## Testing the Service

You can test the service with the following HTTP requests:

### Default Greeting

```bash
$ http -b :8080/textGreeter
Hello, World
```

### Custom Greeting

```bash
$ http -b :8080/textGreeter?name=Andrea
Hello, Andrea
```

### Alternative Request Method

```bash
$ http --raw 'Sara' :8080/textGreeter
Hello, Sara
```

Note: Using HTTP verbs other than `OPTIONS`, `GET` or `POST` for `/hello` will result in a `HTTP  405 Method Not Allowed` response.