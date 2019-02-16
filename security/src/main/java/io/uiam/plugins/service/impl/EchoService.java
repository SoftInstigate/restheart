/*
 * uIAM - the IAM for microservices
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.uiam.plugins.service.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import io.uiam.handlers.ExchangeHelper;
import io.uiam.handlers.PipedHttpHandler;
import io.uiam.plugins.service.PluggableService;
import io.uiam.utils.HttpStatus;
import io.undertow.server.HttpServerExchange;
import java.io.IOException;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class EchoService extends PluggableService {

    /**
     *
     * @param next
     * @param name
     * @param uri
     * @param secured
     */
    public EchoService(PipedHttpHandler next, String name, String uri, Boolean secured) {
        super(next, name, uri, secured, null);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var eh = new ExchangeHelper(exchange);

        JsonObject resp = new JsonObject();

        eh.setResponseContent(resp);

        resp.addProperty("method", exchange.getRequestMethod().toString());
        resp.addProperty("URL", exchange.getRequestURL());

        if (eh.isRequesteContentTypeJson()) {
            try {
                resp.add("body", eh.getRequestBodyAsJson());
            }
            catch (JsonSyntaxException jse) {
                resp.add("body", getTruncatedContentBytes(eh));
                resp.addProperty("note",
                        "showing up to 20 bytes of the request content");
            }
        } else if (eh.isRequesteContentTypeXml() || eh.isRequesteContentTypeText()) {
            resp.addProperty("body", eh.getRequestBodyAsText());
        } else {
            resp.add("body", getTruncatedContentBytes(eh));
            resp.addProperty("note",
                    "showing up to 20 bytes of the request content");
        }

        var qparams = new JsonObject();
        resp.add("qparams", qparams);

        exchange.getQueryParameters().forEach((name, values) -> {
            var _values = new JsonArray();

            qparams.add(name, _values);

            values.iterator().forEachRemaining(value -> {
                _values.add(value);
            });
        });

        var headers = new JsonObject();
        resp.add("headers", headers);

        exchange.getRequestHeaders().forEach(header -> {
            var _values = new JsonArray();
            headers.add(header.getHeaderName().toString(), _values);

            header.iterator().forEachRemaining(value -> {
                _values.add(value);
            });

        });

        eh.setResponseStatusCode(HttpStatus.SC_OK);
    }

    private JsonArray getTruncatedContentBytes(ExchangeHelper eh) throws IOException {
        byte[] content = eh.getRequestBodyAsBytes();

        if (content == null) {
            return null;
        }
        
        JsonArray ret = new JsonArray(20);

        for (int i = 0; i < 20 && i < content.length; i++) {
            ret.add(content[i]);
        }

        return ret;
    }
}
