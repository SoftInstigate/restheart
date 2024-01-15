# Custom Logging Field Interceptor

This plugin shows how to add a custom field to RESTHeart logging via MDC Context

## Building the Plugin

Use the following command to build the plugin. Ensure you are in the project's root directory before executing it:

```bash
$ ../mvnw clean package
```

## Running RESTHeart with the plugin

This Plugin doesn't require MongoDB so we can run RESTHeart in standalone mode (specifying the `-s` option).

To run the RESTHeart with the plugin, use Docker as follows. This command maps the host's port 8080 to the container's port 8080, mounts the build directory as a volume and specifies a custom `logback.xml` configuration file:

```bash
$ docker run --rm -p 8080:8080 -e RHO="/fileRealmAuthenticator/users[userid='admin']/password->'secret';/http-listener/host->'0.0.0.0'" -v ./target:/opt/restheart/plugins/custom -v ./etc/logback.xml:/opt/restheart/etc/logback.xml --entrypoint "java" softinstigate/restheart:latest -Dlogback.configurationFile=./etc/logback.xml -jar restheart.jar -s
```

For more information see [RESTHeart with custom Plugin](https://restheart.org/docs/setup-with-docker#run-restheart-with-custom-plugin) documentation section.

## Testing the Service

```bash
$ http :8080/ping
```

Logging show the variable `foo=bar` (just before `INFO`)

```bash
 16:52:33.440 [main]   INFO  org.restheart.Bootstrapper - RESTHeart started
 16:52:33.761 [XNIO-1 I/O-8] bar  INFO  org.restheart.handlers.RequestLogger - GET http://localhost:8080/ping from /127.0.0.1:61578 => status=200 elapsed=33ms contentLength=45
```