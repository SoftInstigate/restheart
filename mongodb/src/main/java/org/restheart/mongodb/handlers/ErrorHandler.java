/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
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
package org.restheart.mongodb.handlers;

import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoException;
import com.mongodb.MongoExecutionTimeoutException;
import com.mongodb.MongoTimeoutException;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.mongodb.handlers.bulk.BulkResultRepresentationFactory;
import org.restheart.mongodb.utils.ResponseHelper;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ErrorHandler implements HttpHandler {

    private final HttpHandler next;

    private final Logger LOGGER = LoggerFactory.getLogger(ErrorHandler.class);

    /**
     * Creates a new instance of ErrorHandler
     *
     */
    public ErrorHandler() {
        this(null);
    }

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
        var response = MongoResponse.of(exchange);

        try {
            next.handleRequest(exchange);
        } catch (MongoTimeoutException nte) {
            response.setInError(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Timeout connecting to MongoDB, is it running?", nte);
        } catch (MongoExecutionTimeoutException mete) {
            response.setInError(HttpStatus.SC_REQUEST_TIMEOUT, "Operation exceeded time limit");
        } catch (MongoBulkWriteException mce) {
            response.setInError(true);
            var error = new BulkResultRepresentationFactory().getRepresentation(MongoRequest.of(exchange).getPath(), mce);

            response.setStatusCode(HttpStatus.SC_MULTI_STATUS);
            response.setContent(error);
        } catch (MongoException mce) {
            int httpCode = ResponseHelper.getHttpStatusFromErrorCode(mce.getCode());

            LOGGER.error("Error handling the request", mce);

            if (httpCode >= 500 && mce.getMessage() != null && !mce.getMessage().trim().isEmpty()) {
                response.setInError(httpCode, mce.getMessage());
            } else {
                response.setInError(httpCode, ResponseHelper.getMessageFromErrorCode(mce.getCode()));
            }
        } catch (Exception t) {
            LOGGER.error("Error handling the request", t);

            response.setInError(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error handling the request, see log for more information", t);
        }
    }
}
