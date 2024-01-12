# Hello World Example - RESTHeart Plugin

This is a simple Java REST Web service, demonstrating a minimal implementation using the `ByteArrayService` interface from RESTHeart. It serves `text/plain` content and importantly, does not require MongoDB, making it lightweight and easy to set up.

## Building the Plugin

Use the following command to build the plugin. Ensure you are in the project's root directory before executing it:

```bash
$ ../mvnw clean package
```

## Running the Service

To run the service, use Docker as follows. This command maps the host's port 8080 to the container's port 8080 and mounts the build directory as a volume:

```bash
$ docker run --rm -p 8080:8080 -v ./target:/opt/restheart/plugins/custom softinstigate/restheart:7.7 -s
```

### Enabling Remote Debugging

For remote debugging, run the service with the following command. This will expose both the service and debugging ports, allowing you to attach an IDE for debugging:

```bash
docker run --rm -p 8080:8080 -p 4000:4000 -v ./target:/opt/restheart/plugins/custom --entrypoint "java" softinstigate/restheart:7.7 -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=0.0.0.0:4000 -jar restheart.jar -s
```

You can attach to port 4000 using an IDE like Visual Studio Code with the Java Plugin to debug your code.

## Plugin Code Overview

Here's the basic structure of the `HelloByteArrayService` class:

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

For the complete source code, see [HelloByteArrayService.java](src/main/java/org/restheart/examples/HelloByteArrayService.java).

## Testing the Service

You can test the service with the following HTTP requests:

### Default Greeting

```bash
$ http -b :8080/hello
Hello, Anonymous
```

### Custom Greeting

```bash
$ http -b :8080/hello?name=Andrea
Hello, Andrea
```

### Alternative Request Method

```bash
$ http --raw 'Sara' :8080/hello
Hello, Sara
```

Note: Using HTTP verbs other than `GET` or `POST` for `/hello` will result in a `HTTP 400 - Bad Request` response.