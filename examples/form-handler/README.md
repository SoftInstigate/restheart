# Form Handler Service

This example showcases a specialized service designed to handle Form POST requests efficiently using the Undertow `FormParser`. This service is distinct in its ability to process Form data, a key feature for many web applications.

Note that the `MongoService` (MongoDB REST API) handles form requests out-of-the-box within the MongoDB context and does not require custom code.

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

```bash
http -b --form :8080/formHandler name=Andrea nickname=uji

{
    "name": "Andrea",
    "nickname": "uji"
}
```