package org.restheart.examples;

import java.nio.ByteBuffer;
import org.restheart.exchange.ServiceResponse;
import io.undertow.server.HttpServerExchange;

public class ProtoResponse extends ServiceResponse<HelloReply> {

    protected ProtoResponse(HttpServerExchange exchange) {
        super(exchange);

        setContentType("application/protobuf");
        setCustomSender(() -> {
            if (getContent() != null) {
                exchange.getResponseSender().send(ByteBuffer.wrap(getContent().toByteArray()));
            } else {
                exchange.getResponseSender().send(ByteBuffer.wrap(HelloReply.getDefaultInstance().toByteArray()));
            }
        });
    }

    @Override
    public String readContent() {
        // not needed, use custom sender
        return null;
    }

    @Override
    public void setInError(int code, String message, Throwable t) {
        setInError(true);
        setStatusCode(code);
        setContent(HelloReply.getDefaultInstance());
    }

    public static ProtoResponse init(HttpServerExchange exchange) {
        return new ProtoResponse(exchange);
    }

    public static ProtoResponse of(HttpServerExchange exchange) {
        return of(exchange, ProtoResponse.class);
    }
}
