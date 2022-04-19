# kotlinGreeterService

This example shows how to implement a service in Kotlin programming language

This service just returns a JSON response with a greetings message

## How to build and run

You need **JDK 17++** to build and run this example.

-   Clone this repo `git clone git@github.com:SoftInstigate/restheart-examples.git`
-   `cd` into the `restheart-examples` folder
-   [Download RESTHeart](https://github.com/SoftInstigate/restheart/releases/)
-   uncompress it: `unzip restheart.zip` or `tar -xvf restheart.tar.gz`.

### Run

1. `cd restheart-examples/kotlin-greeter-service`
1. Build the plugin with `../mvnw package`
1. Copy the service JAR `target/kotlin-greeter-service.jar` into `../restheart/plugins/` folder
1. Copy the kotlin stdlib JAR `target/lib/kotlin-stdlib-1.6.0.jar` into `../restheart/plugins/` folder
1. cd into the restheart distribution you have previously downloaded and uncompressed.
1. Start the process: `java -jar restheart.jar etc/restheart.yml -e etc/default.properties`.

## Test

We suggest using [httpie](https://httpie.org) for calling the API from command line, or just use your [browser](http://localhost:8080/status).

```http
$ http localhost:8080/greetings

HTTP/1.1 200 OK

{
    "msg": "Hello World"
}
```
