# Random String Service with External Dependency Integration

This example provides a guide on incorporating an external dependency into the classpath, enabling its utilization within a RESTHeart plugin.

> An *external dependency* refers to any library or module not inherently included in `restheart.jar`. To use such a dependency, it's necessary to add it to the classpath.

## Adding an External Dependency

**To integrate an external dependency into your RESTHeart service, simply copy the corresponding JAR file into the `plugins` directory or any of its subdirectories.**

When launching RESTHeart, the system scans and recognizes these external dependencies, as evidenced by log messages like the following:

```
...
10:41:21.478 [main] INFO  o.restheart.plugins.PluginsScanner - Found plugin jar /opt/restheart/plugins/custom/random-string-service.jar
10:41:21.490 [main] INFO  o.restheart.plugins.PluginsScanner - Found plugin jar /opt/restheart/plugins/custom/lib/commons-lang3-3.10.jar
...
```

In this example, RESTHeart successfully detects and deploys both the `random-string-service.jar` and its external dependency, `lib/commons-lang3-3.10.jar`.

## Service Functionality

The service in question generates a random string upon receiving GET requests. It leverages the Apache Commons Lang library, an external dependency, to accomplish this.

To manage external dependencies, the service employs the `maven-dependency-plugin`, which automates the copying of the external dependency's JAR file into the `target/lib` directory.

## Best Practices

**Tip:** It's crucial to avoid duplicating dependencies. Ensure that you do not copy JAR files to the `plugins` directory if they are already included in `restheart.jar`. To check for existing dependencies within `restheart.jar`, utilize Maven's dependency tree feature:

```bash
$ ./mvnw dependency:tree
```

This approach ensures a streamlined and efficient use of dependencies, preventing conflicts and redundancy in your RESTHeart plugin development.

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

## Testing the Service

```bash
$ http -b localhost:8080/rndStr

xdGtToDjid
```
