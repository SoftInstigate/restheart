/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
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

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.restheart.Version;
import org.restheart.exchange.Response;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.PluginUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ErrorHandler implements HttpHandler {

    private final HttpHandler next;

    private final PipelinedHandler sender = new ResponseSender(null);

    private final Logger LOGGER = LoggerFactory.getLogger(ErrorHandler.class);

    /**
     * Creates a new instance of ErrorHandler
     *
     * @param next
     */
    public ErrorHandler(HttpHandler next) {
        this.next = next;
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        try {
            next.handleRequest(exchange);
        } catch (LinkageError le) {
            // this occurs executing plugin code compiled
            // with wrong version of restheart-commons
            var pi = PluginUtils.pipelineInfo(exchange);

            String version = Version.getInstance().getVersion() == null
                    ? "of correct version"
                    : "v" + Version.getInstance().getVersion();

            var errMsg = "Linkage error handling the request. "
                    + "Check that "
                    + pi.getType().name().toLowerCase()
                    + " "
                    + pi.getName()
                    + " was compiled against restheart-commons "
                    + version;

            LOGGER.error(errMsg, le);

            Response.of(exchange).setInError(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    errMsg);

            sender.handleRequest(exchange);
        } catch (Throwable t) {
            LOGGER.error("Error handling the request", t);

            Response.of(exchange).setInError(
                    HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    "Error handling the request, see logs for more information", t);

            sender.handleRequest(exchange);
        }
    }
}
