# kotlinGreeterService

This project demonstrates how to implement a RESTHeart service using the Kotlin programming language.

The service, named `kotlinGreeterService` provides a simple RESTful endpoint that returns a JSON-formatted greetings message.

It uses the maven plugin `kotlin-maven-plugin` to build the code.

To manage external dependencies, the service employs the `maven-dependency-plugin`, which automates the copying of the external dependency's JAR file into the `target/lib` directory.

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

## Testing the Kotlin Service


```bash
$ http localhost:8080/greetings

HTTP/1.1 200 OK

{
    "msg": "Hello World"
}
```
