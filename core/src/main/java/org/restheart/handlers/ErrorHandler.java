/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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

import org.restheart.Version;
import org.restheart.exchange.BadRequestException;
import org.restheart.exchange.Request;
import org.restheart.exchange.Response;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ErrorHandler extends PipelinedHandler {


    private final PipelinedHandler sender = new ResponseSender(null);

    private final Logger LOGGER = LoggerFactory.getLogger(ErrorHandler.class);

    /**
     * Creates a new instance of ErrorHandler
     *
     */
    public ErrorHandler() {
        super(null);
    }

    /**
     * Creates a new instance of ErrorHandler
     *
     * @param next
     */
    public ErrorHandler(PipelinedHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        try {
            next(exchange);
        } catch (NoClassDefFoundError ncdfe) {
            // this can occur with plugins missing external dependencies

            Response.of(exchange).setInError(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error handling the request, see logs for more information");

            var pi = Request.of(exchange).getPipelineInfo();

            if (pi != null) {
                var errMsg = "Error handling the request. "
                        + "An external dependency appears to be missing for plugin type '"
                        + pi.getType().name().toLowerCase()
                        + "' with the name '"
                        + pi.getName()
                        + "'.Please copy the missing dependency JAR file to the plugins directory to ensure it's included in the classpath.";

                LOGGER.error(errMsg, ncdfe);
            } else {
                LOGGER.error("Error handling the request", ncdfe);
            }

            sender.handleRequest(exchange);
        } catch (LinkageError le) {
            // this occurs executing plugin code compiled
            // with wrong version of restheart-commons
            var pi = Request.of(exchange).getPipelineInfo();

            if (pi != null) {
                var version = Version.getInstance().getVersionNumber()
                    .map(v -> "v" + v)
                    .orElse("of correct version");

                var errMsg = "Linkage error occurred while handling the request. "
                    + "Ensure that '"
                    + pi.getType().name().toLowerCase()
                    + "' with the name '"
                    + pi.getName()
                    + "' was compiled using the correct version of restheart-commons: "
                    + version;

                LOGGER.error(errMsg, le);
            } else {
                LOGGER.error("Error handling the request", le);
            }

		  if (!exchange.isResponseStarted()) {
			Response.of(exchange).setInError(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error handling the request, see logs for more information", le);
			sender.handleRequest(exchange);
		  } else {
			LOGGER.debug("Omit error response since response already started");
		  }
        } catch (BadRequestException bre) {
			if (!exchange.isResponseStarted()) {
			  Response.of(exchange).setInError(bre.getStatusCode(), bre.getMessage());
			  sender.handleRequest(exchange);
			} else {
			  LOGGER.debug("Bad request. Omit error response since response already started", bre);
			}
        } catch (Throwable t) {
            LOGGER.error("Error handling the request", t);

			if (!exchange.isResponseStarted()) {
            	Response.of(exchange).setInError(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error handling the request, see logs for more information", t);
            	sender.handleRequest(exchange);
			} else {
			  LOGGER.debug("Omit error response since response already started", t);
			}
        }
    }
}
