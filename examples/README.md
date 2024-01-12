# RESTHeart Example Plugins

This collection showcases various RESTHeart plugins, each serving a specific purpose. Below is a brief overview of each plugin:

## Java Plugins

 - **[Greeter Service](greeter-service/README.md)**: Implements a basic Java REST Web service returning JSON payloads. Demonstrates usage of `JsonService`.
 - **[Example Configuration Files](example-conf-files/README.md)**: Provides a collection of sample configuration files for RESTHeart.
 - **[User Signup](user-signup/README.md)**: Facilitates user signup processes. Utilizes interceptors, services, JSON schema validation, and security permissions.
 - **[Slack Notifier](slack-notifier/README.md)**: An asynchronous interceptor that posts messages to a Slack channel when a document is created in a specified collection.
 - **[Hello World](bytes-array-service/README.md)**: A simple Java REST Web service delivering text payloads. Employs `ByteArrayService`.
 - **[MongoDB serverStatus Service](mongo-status-service/README.md)**: Offers access to MongoDB's `serverStatus` system call and demonstrates how to inject the `MongoClient` in a plugin.
 - **[Random String Service](random-string-service/README.md)**: Provides random strings. Showcases deployment of services with external dependencies.
 - **[CSV Interceptor](csv-interceptor/README.md)**: Transforms CSV coordinates into GeoJSON objects. Illustrates how to modify requests with Interceptors.
 - **[Protocol Buffer Contacts](protobuffer-contacts/README.md)**: Interceptors adjusting request and response formats to and from MongoService for Protocol Buffer payloads.
 - **[Freemarker HTML page](freemarker/README.md)**: Generates HTML Web pages using [Apache Freemarker](https://freemarker.apache.org/).
 - **[Custom Logging Field](custom-logging-field/README.md)**: Details adding custom fields to logs via MDC Context.
 - **[Form Header](form-header/README.md)**: A Service with a bespoke `Request` to manage Form POST requests.
 - **[Instance name](instance-name/README.md)**: Adds an `X-Restheart-Instance` header to responses handled by `MongoService`.
 - **[X-Headers-to-qparams](x-headers-to-qparams/README.md)**: An interceptor that converts headers into query parameters.
 - **[X-Powered-By Remover](x-powered-by-remover/README.md)**: Response interceptors that remove the `X-Powered-By: restheart.org` header from service and proxied resource responses.

# Kotlin Plugins

- **[Kotlin Greeter Service](kotlin-greeter-service/README.md)**: A basic service implemented using Kotlin.

# JavaScript Plugins

- **[JavaScript Plugins](js-plugin/README.md)**: A compilation of example JavaScript plugins.
- **[Node Plugin](node-plugin/README.md)**: A JavaScript plugin for RESTHeart, running on GraalVM's Node.js implementation.
- **[Credit Card Hider](credit-card-hider/README.md)**: A JavaScript Interceptor that obfuscates credit card numbers in responses. Demonstrates response transformation in existing Services.

## Requirements to Build and Run the Examples

To work with these examples, the following are required:

1. **Java 17**: Install it using [sdkman](https://sdkman.io/):
   ```bash
   $ sdk install java 17.0.9-tem
   ```

2. **Docker**: Necessary for running RESTHeart.

3. **MongoDB**: Some examples need a MongoDB instance running locally.

4. **HTTPie**: A command-line HTTP client for testing, available at [httpie.io/cli](https://httpie.io/cli).

Note: The examples use Maven, but it's accessible through the included Maven wrapper (`mvnw`), so a separate installation is not required.

---

This revision organizes the plugins list into a more readable format, adds a brief description to each plugin for context, and clarifies the requirements section for better user guidance.