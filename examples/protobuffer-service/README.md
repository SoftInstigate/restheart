# Protocol Buffer Service example

A simple Service that uses Protocol Buffer as payloads

For example:

```bash
$ mvn clean package
$ cp target/protobuffer-service.jar target/lib/* <restheart-dir>/plugins
$ java -cp target/classes:target/test-classes:target/lib/\*:target/test-lib/\* org.restheart.examples.Test World
```

Where `<restheart-dir>` is the RESTHeart directory

Returns `Hello World`.

The service uses the following .proto definition

```proto
syntax = "proto3";

option java_multiple_files = true;
option java_package = "org.restheart.examples";
option java_outer_classname = "HelloWorldProto";
option objc_class_prefix = "HLW";

package helloworld;

// The greeting service definition.
service Greeter {
  // Sends a greeting
  rpc SayHello (HelloRequest) returns (HelloReply) {}
}

// The request message containing the user's name.
message HelloRequest {
  string name = 1;
}

// The response message containing the greetings
message HelloReply {
  string message = 1;
}
```