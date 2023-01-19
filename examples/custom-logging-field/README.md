# Custom Logging Field Interceptor

This plugin shows how to add a custom field to logging via MDC Context

```bash
$ ./mvnw clean package
$ cp examples/custom-logging-field/target/custom-logging-field.jar core/target/plugins
$ java -Dlogback.configurationFile=./examples/custom-logging-field/etc/logback.xml -jar core/target/restheart.jar
```

```bash
$ http :8080/ping
```

Logging show the variable `foo=bar` (just before `INFO`)

```bash
 16:52:33.440 [main]   INFO  org.restheart.Bootstrapper - RESTHeart started
 16:52:33.761 [XNIO-1 I/O-8] bar  INFO  org.restheart.handlers.RequestLogger - GET http://localhost:8080/ping from /127.0.0.1:61578 => status=200 elapsed=33ms contentLength=45
```