/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
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
package org.restheart.mongodb.handlers;

import org.bson.BSONException;
import org.restheart.exchange.BadRequestException;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.mongodb.handlers.bulk.BulkResultRepresentationFactory;
import org.restheart.mongodb.utils.ResponseHelper;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoException;
import com.mongodb.MongoExecutionTimeoutException;
import com.mongodb.MongoTimeoutException;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 * Handles errors that occur during MongoDB request processing and maps them to appropriate HTTP responses.
 */
public class ErrorHandler implements HttpHandler {

    private static final String ERROR_HANDLING_THE_REQUEST = "Error handling the request";

    private final HttpHandler next;

    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorHandler.class);

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
    public ErrorHandler(final HttpHandler next) {
        this.next = next;
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        final var response = MongoResponse.of(exchange);

        try {
            next.handleRequest(exchange);
        } catch (final MongoTimeoutException nte) {
            response.setInError(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Timeout connecting to MongoDB, is it running?", nte);
        } catch (final MongoExecutionTimeoutException mete) {
            response.setInError(HttpStatus.SC_REQUEST_TIMEOUT, "Operation exceeded time limit");
        } catch (final MongoBulkWriteException mce) {
            generateBulkWriteErrorResponse(exchange, response, mce);
        } catch (final MongoException mce) {
            mapMongoExceptionToHttpCode(response, mce);
        } catch (final BSONException be) {
            handleBsonException(response, be);
        } catch (final BadRequestException be) {
            response.setInError(be.getStatusCode(), be.getMessage());
        } catch (final Exception t) {
            handleErrorResponse(response, t);
        }
    }

    private void generateBulkWriteErrorResponse(final HttpServerExchange exchange, final MongoResponse response, final MongoBulkWriteException mce) {
        response.setInError(true);
        final var error = new BulkResultRepresentationFactory().getRepresentation(MongoRequest.of(exchange).getPath(), mce);

        response.setStatusCode(HttpStatus.SC_MULTI_STATUS);
        response.setContent(error);
    }

    private void handleErrorResponse(final MongoResponse response, final Exception t) {
        if (t.getMessage() != null && t.getMessage().contains("UT000128")) { // Remote peer closed connection before all data could be read
            LOGGER.error("Remote peer closed connection before all data could be read");
            response.setInError(HttpStatus.SC_BAD_REQUEST, ""); // setting status just for logging, the client won't receive the response.
        } else {
            LOGGER.error(ERROR_HANDLING_THE_REQUEST, t);
            response.setInError(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error handling the request, see log for more information", t);
        }
    }

    private void handleBsonException(final MongoResponse response, final BSONException be) {
        LOGGER.debug("Request failed due to invalid BSON", be);
        response.setInError(400, "Invalid BSON: " + be.getMessage());
    }

    private void mapMongoExceptionToHttpCode(final MongoResponse response, final MongoException mce) {
        final int httpCode = ResponseHelper.getHttpStatusFromErrorCode(mce.getCode());

        if (httpCode == HttpStatus.SC_SERVICE_UNAVAILABLE) {
            // MongoDB overloaded or at a limit (e.g. too many open transactions):
            // expected under load, so no stack trace and a clean message for the client
            LOGGER.warn("MongoDB cannot serve the request now: {}", mce.getMessage());
            response.setInError(httpCode, ResponseHelper.getMessageFromMongoException(mce));
        } else if (httpCode >= 500 && mce.getMessage() != null && !mce.getMessage().isBlank()) {
            LOGGER.error(ERROR_HANDLING_THE_REQUEST, mce);
            response.setInError(httpCode, mce.getMessage());
        } else {
            LOGGER.debug(ERROR_HANDLING_THE_REQUEST, mce);
            response.setInError(httpCode, ResponseHelper.getMessageFromMongoException(mce));
        }
    }
}
