package org.restheart.examples;

import java.util.function.Consumer;
import java.util.function.Function;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.undertow.server.HttpServerExchange;

@RegisterPlugin(
        name = "protoBufferService",
        description = "A simple service using Protobuffer payloads",
        defaultURI = "/proto")
public class ProtobufferService implements Service<ProtoRequest, ProtoResponse> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProtobufferService.class);

    @Override
    public void handle(ProtoRequest request, ProtoResponse response) throws Exception {
        LOGGER.debug("request content: {}", request.getContent());
        response.setContent(HelloReply.newBuilder().setMessage("Hello " + request.getContent().getName()) .build());
    }

    @Override
    public Consumer<HttpServerExchange> requestInitializer() {
        return e -> ProtoRequest.init(e);
    }

    @Override
    public Consumer<HttpServerExchange> responseInitializer() {
        return e -> ProtoResponse.init(e);
    }

    @Override
    public Function<HttpServerExchange, ProtoRequest> request() {
        return e -> ProtoRequest.of(e);
    }

    @Override
    public Function<HttpServerExchange, ProtoResponse> response() {
        return e -> ProtoResponse.of(e);
    }
}
