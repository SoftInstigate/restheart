# Instance Name Interceptor with Wildcard and Configuration Injection

This interceptor adds a custom `X-Restheart-Instance` header to all responses processed by any service in RESTHeart. It serves as a practical demonstration of using the `WildcardInterceptor` interface and illustrates how to inject the `Configuration` object into a plugin.

## Features and Implementation

- **Custom Response Header**: The interceptor appends the `X-Restheart-Instance` header to responses, providing a clear identifier of the RESTHeart instance handling the request.

- **Use of `WildcardInterceptor` Interface**: By implementing the `WildcardInterceptor` interface, the interceptor is capable of applying its logic to any service within RESTHeart, making it universally effective across all endpoints.

- **Configuration Injection**: The RESTHeart configuration file defines a property named `instance-name`. This property is injected into the interceptor, allowing it to dynamically retrieve and utilize the instance name set in the configuration.

  ```yml
  #### Instance name

   # The name of this instance.
   # Displayed in log, also allows to implement instance specific custom code

  instance-name: default
  ```

- **Dynamic Response Enhancement**: The interceptor enhances each response with the instance name from the configuration, adding it to the `X-Restheart-Instance` header. This feature is particularly useful for scenarios involving multiple RESTHeart instances, as it provides clarity on which instance processed a given request.

## Building the Plugin

Use the following command to build the plugin. Ensure you are in the project's root directory before executing it:

```bash
../mvnw clean package
```

## Running RESTHeart with the plugin

This Plugin doesn't require MongoDB so we can run RESTHeart in standalone mode (specifying the `-s` option).

To run the RESTHeart with the plugin, use Docker as follows. This command maps the host's port 8080 to the container's port 8080 and mounts the build directory as a volume:

```bash
docker run --rm -p 8080:8080 -e RHO="/fileRealmAuthenticator/users[userid='admin']/password->'secret';/http-listener/host->'0.0.0.0'" -v ./target:/opt/restheart/plugins/custom softinstigate/restheart:latest -s
```

For more information see [RESTHeart with custom Plugin](https://restheart.org/docs/setup-with-docker#run-restheart-with-custom-plugin) documentation section.

## Testing the Service


## Test GET

```http
http :8080/ping

HTTP/1.1 200 OK
(other headers omitted)
Content-Type: text/plain
Date: Mon, 13 Apr 2020 13:32:52 GMT
X-Powered-By: restheart.org
X-Restheart-Instance: default-no-mongodb   <========

(response content omitted)
```