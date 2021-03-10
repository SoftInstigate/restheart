package org.restheart.exchange;

import com.google.gson.JsonElement;

import org.bson.BsonValue;
import org.restheart.exchange.PipelineInfo.PIPELINE_TYPE;
import org.restheart.plugins.RegisterPlugin.MATCH_POLICY;

import io.undertow.server.HttpServerExchange;

public class ExchangeWithRequestFactory {
    public static HttpServerExchange withBson(HttpServerExchange exchange, BsonValue content) {
        var br = new BsonRequest(exchange);
        br.setContent(content);
        var pipelineInfo = new PipelineInfo(PIPELINE_TYPE.SERVICE, "/", MATCH_POLICY.EXACT, "foo");
        Request.setPipelineInfo(exchange, pipelineInfo);

        return exchange;
    }

    public static HttpServerExchange withJson(HttpServerExchange exchange, JsonElement content) {
        var br = new JsonRequest(exchange);
        br.setContent(content);
        var pipelineInfo = new PipelineInfo(PIPELINE_TYPE.SERVICE, "/", MATCH_POLICY.EXACT, "foo");
        Request.setPipelineInfo(exchange, pipelineInfo);

        return exchange;
    }
}
