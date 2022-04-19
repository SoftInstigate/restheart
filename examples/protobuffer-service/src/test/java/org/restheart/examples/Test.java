package org.restheart.examples;

import java.io.IOException;
import com.google.protobuf.InvalidProtocolBufferException;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;

public class Test {
    public static void main(String[] args) throws Exception  {
        testRequest(args[0] == null ? "World" : args[0]);
    }

    public static void testRequest(String name) throws UnirestException, InvalidProtocolBufferException, IOException  {
        var body = HelloRequest.newBuilder()
            .setName(name)
            .build();

        var resp = Unirest.post("http://localhost:8080/proto")
                .header("Content-Type", "application/protobuf")
                .body(body.toByteArray())
                .asBinary();

        var reply = HelloReply.parseFrom(resp.getBody().readAllBytes());

        System.out.println(reply.getMessage());
    }
}
