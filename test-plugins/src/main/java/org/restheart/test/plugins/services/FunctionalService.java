package org.restheart.test.plugins.services;

import static org.restheart.utils.GsonUtils.object;

import java.util.function.BiConsumer;

import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.RegisterPlugin;

/**
 * functional style, test service
 */
@RegisterPlugin(name="test",description = "foo")
public class FunctionalService implements JsonService {
    @Override
    public BiConsumer<JsonRequest, JsonResponse> handle() {
        return (req, res) -> {
            (switch(req.getMethod()) {
                case GET -> body().andThen(ok());
                case OPTIONS -> handleOptions();
                default -> error();
            }).accept(req, res);
        };
    }

    private BiConsumer<JsonRequest, JsonResponse> body() {
        return (req, resp) -> resp.setContent(object().put("msg", "hello world"));
    }

    private BiConsumer<JsonRequest, JsonResponse> ok() {
        return (req, resp) -> resp.setStatusCode(201);
    }

    private BiConsumer<JsonRequest, JsonResponse> error() {
        return (req, resp) -> resp.setStatusCode(500);
    }
}
