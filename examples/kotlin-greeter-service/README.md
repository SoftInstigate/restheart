# kotlinGreeterService

This example shows how to implement a service in Kotlin programming language

This service just returns a JSON response with a greetings message

### Deploy

1. Copy the service JAR `target/kotlin-greeter-service.jar` into `../restheart/plugins/` folder
1. Copy the kotlin stdlib JAR `target/lib/kotlin-stdlib-1.6.0.jar` into `../restheart/plugins/` folder
## Test

We suggest using [httpie](https://httpie.org) for calling the API from command line, or just use your [browser](http://localhost:8080/status).

```http
$ http localhost:8080/greetings

HTTP/1.1 200 OK

{
    "msg": "Hello World"
}
```
