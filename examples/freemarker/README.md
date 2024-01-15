# Server-Side HTML Rendering with Apache Freemarker in RESTHeart

This RESTHeart Service is enhanced by integrating the [Apache Freemarker](https://freemarker.apache.org/) template engine, a powerful tool for generating server-side HTML content. 

## Key Features

- **Apache Freemarker Integration**: By incorporating the Apache Freemarker template engine, this service can dynamically generate HTML content on the server side. Apache Freemarker is renowned for its efficiency and flexibility in processing and presenting data-driven web content.

- **Template Rendering**: The service specifically focuses on rendering an HTML template located at `src/main/resources/templates/index.html`. This template is crafted using the FreeMarker Template Language (FTL), known for its simplicity and specialization in templating.

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

Open you browser at [http://localhost:8080/site?user=Sara](http://localhost:8080/site?user=Sara)

You can add a `user` query parameter to see it rendered server-side (e.g. <http://localhost:8080/site?user=Anna>).