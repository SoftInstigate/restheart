package org.restheart.examples;

import java.io.IOException;

import org.restheart.exchange.ServiceRequest;
import org.restheart.utils.ChannelReader;
import io.undertow.server.HttpServerExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProtoRequest extends ServiceRequest<HelloRequest> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProtoRequest.class);

    protected ProtoRequest(HttpServerExchange exchange) {
        super(exchange);
    }

    public static ProtoRequest init(HttpServerExchange exchange) {
        var ret = new ProtoRequest(exchange);

        try {
            ret.injectContent();
        } catch (Throwable ieo) {
            ret.setInError(true);
        }

        return ret;
    }

    public static ProtoRequest of(HttpServerExchange exchange) {
        return of(exchange, ProtoRequest.class);
    }

    public void injectContent() throws IOException {
        try {
            setContent(HelloRequest.parseFrom(ChannelReader.readBytes(wrapped)));
        } catch(IOException ioe) {
            LOGGER.error("error parsing request {}", ioe);
            throw ioe;
        }
    }
}
