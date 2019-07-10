/*
 * RESTHeart - the Web API for MongoDB
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
package org.restheart.handlers;

import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoException;
import com.mongodb.MongoExecutionTimeoutException;
import com.mongodb.MongoTimeoutException;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.restheart.handlers.bulk.BulkResultRepresentationFactory;
import org.restheart.handlers.metadata.TransformersListHandler;
import org.restheart.handlers.transformers.RepresentationTransformer;
import org.restheart.metadata.TransformerMetadata.PHASE;
import org.restheart.representation.Resource;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ErrorHandler implements HttpHandler {

    private final HttpHandler next;

    private final PipedHttpHandler sender = new TransformersListHandler(
            new ResponseSenderHandler(null),
            PHASE.RESPONSE,
            new RepresentationTransformer());

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
    public void handleRequest(
            HttpServerExchange exchange)
            throws Exception {
        try {
            next.handleRequest(exchange);
        } catch (MongoTimeoutException nte) {
            RequestContext errorContext = new RequestContext(exchange, "/", "_error");

            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    errorContext,
                    HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    "Timeout connecting to MongoDB, is it running?", nte);

            sender.handleRequest(exchange, errorContext);
        } catch (MongoExecutionTimeoutException mete) {
            RequestContext errorContext = new RequestContext(exchange, "/", "_error");

            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    errorContext,
                    HttpStatus.SC_REQUEST_TIMEOUT,
                    "Operation exceeded time limit"
            );

            sender.handleRequest(exchange, errorContext);
        } catch (MongoBulkWriteException mce) {
            MongoBulkWriteException bmce = mce;

            BulkResultRepresentationFactory rf = new BulkResultRepresentationFactory();

            Resource rep = rf.getRepresentation(exchange, bmce);

            RequestContext errorContext = new RequestContext(exchange, "/", "_error");

            ResponseHelper.endExchangeWithRepresentation(
                    exchange,
                    errorContext,
                    HttpStatus.SC_MULTI_STATUS,
                    rep);

            sender.handleRequest(exchange, errorContext);
        } catch (MongoException mce) {
            int httpCode = ResponseHelper.getHttpStatusFromErrorCode(mce.getCode());

            LOGGER.error("Error handling the request", mce);

            RequestContext errorContext = new RequestContext(exchange, "/", "_error");

            if (httpCode >= 500
                    && mce.getMessage() != null
                    && !mce.getMessage().trim().isEmpty()) {

                ResponseHelper.endExchangeWithMessage(
                        exchange,
                        errorContext,
                        httpCode,
                        mce.getMessage());

            } else {
                ResponseHelper.endExchangeWithMessage(
                        exchange,
                        errorContext,
                        httpCode,
                        ResponseHelper.getMessageFromErrorCode(mce.getCode()));
            }

            sender.handleRequest(exchange, errorContext);
        } catch (Exception t) {
            LOGGER.error("Error handling the request", t);

            RequestContext errorContext = new RequestContext(exchange, "/", "_error");

            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    errorContext,
                    HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    "Error handling the request, see log for more information", t);
            sender.handleRequest(exchange, errorContext);
        }
    }
}
