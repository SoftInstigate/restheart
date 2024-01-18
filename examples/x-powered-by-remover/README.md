# X-Powered-By Remover

Two response interceptors that remove the `X-Powered-By: restheart.org` header from Service and Proxied resources responses.

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

## Testing the Interceptor

```bash
$ http :8080/ping

HTTP/1.1 200 OK
Access-Control-Allow-Credentials: true
Access-Control-Allow-Origin: *
Access-Control-Expose-Headers: Location, ETag, Auth-Token, Auth-Token-Valid-Until, Auth-Token-Location, X-Powered-By
Connection: keep-alive
Content-Encoding: gzip
Content-Length: 45
Content-Type: text/plain
Date: Mon, 15 Jan 2024 11:31:16 GMT

Greetings from RESTHeart!
```

Note that the response header `X-Powered-By: restheart.org` is effectively removed.