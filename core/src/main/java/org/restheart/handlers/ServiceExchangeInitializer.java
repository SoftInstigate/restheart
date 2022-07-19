/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2022 SoftInstigate
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
package org.restheart.handlers;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.restheart.exchange.BadRequestException;
import org.restheart.exchange.Exchange;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.PluginsRegistryImpl;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.PluginUtils;
import org.restheart.utils.BsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Initializes the Request and the Response invoking requestInitializer() and
 * responseInitializer() functions defined by the handling service
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ServiceExchangeInitializer extends PipelinedHandler {
    private final Logger LOGGER = LoggerFactory.getLogger(ServiceExchangeInitializer.class);

    private final PluginsRegistry pluginsRegistry = PluginsRegistryImpl.getInstance();

    /**
     * Creates a new instance of RequestInitializer
     *
     */
    public ServiceExchangeInitializer() {
        super();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var path = exchange.getRequestPath();

        var pi = pluginsRegistry.getPipelineInfo(path);

        var srv = pluginsRegistry.getServices().stream()
            .filter(s -> s.getName().equals(pi.getName()))
            .findAny();

        if (srv.isPresent()) {
            try {
                // execute the service request initializer or a custom one if
                // attached to the exchange

                var customInitializer = PluginUtils.customInitializer(exchange);

                if (customInitializer == null) {
                    // service default request initializer
                    srv.get().getInstance().requestInitializer().accept(exchange);
                } else {
                    // custom initializer
                    customInitializer.accept(exchange);
                }

                // execute the response initializer
                srv.get().getInstance().responseInitializer().accept(exchange);
            } catch (BadRequestException bre) {
                LOGGER.debug("Error handling the request: {}", bre.getMessage(), bre);
                exchange.setStatusCode(bre.getStatusCode());
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, Exchange.JSON_MEDIA_TYPE);
                exchange.getResponseSender().send(BsonUtils.toJson(getErrorDocument(bre.getStatusCode(), bre.getMessage())));
                return;
            } catch (Throwable t) {
                exchange.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                throw t;
            }
        }

        next(exchange);
    }

    private BsonDocument getErrorDocument(int statusCode, String msg) {
        var rep = new BsonDocument();

        rep.put("http status code", new BsonInt32(statusCode));
        var text = HttpStatus.getStatusText(statusCode);
        if (text != null) {
            rep.put("http status description", new BsonString(HttpStatus.getStatusText(statusCode)));
        }

        if (msg != null) {
            rep.put("message", new BsonString(msg));
        }

        return rep;
    }
}
