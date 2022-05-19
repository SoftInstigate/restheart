package org.restheart.examples;

import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;

import static org.restheart.utils.GsonUtils.object;

@RegisterPlugin(name = "greetings", description = "just another Hello World")
public class GreeterService implements JsonService {
    @Override
    public void handle(JsonRequest req, JsonResponse res) {
        switch(req.getMethod()) {
            case GET -> res.setContent(object().put("message", "Hello World!"));
            case OPTIONS -> handleOptions(req);
            default -> res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
        }
    }
}