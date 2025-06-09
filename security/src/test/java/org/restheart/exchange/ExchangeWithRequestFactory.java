/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2025 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
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
