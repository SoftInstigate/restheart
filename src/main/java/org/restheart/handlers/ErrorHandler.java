/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
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

import com.mongodb.MongoException;
import com.mongodb.MongoTimeoutException;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class ErrorHandler implements HttpHandler {

    private final HttpHandler next;

    private final Logger LOGGER = LoggerFactory.getLogger(ErrorHandler.class);

    /**
     * Creates a new instance of ErrorHandler
     *
     * @param next
     */
    public ErrorHandler(HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        try {
            next.handleRequest(exchange);
        } catch (MongoTimeoutException nte) {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_INTERNAL_SERVER_ERROR, "Timeout connecting to MongoDB, is it running?", nte);

        } catch (MongoException mce) {
            LOGGER.error("MongoDB error", mce);
            switch (mce.getCode()) {
                case 13:
                    ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_FORBIDDEN, "The MongoDB user does not have enough permissions to execute this operation.");
                    break;
                case 18:
                    ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_FORBIDDEN, "Wrong MongoDB user credentials (wrong password or need to specify the authentication dababase with 'authSource=<db>' option in mongo-uri).");
                    break;
                default:
                    ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error handling the request", mce);
                    break;
            }
        } catch (Throwable t) {
            LOGGER.error("Error handling the request", t);

            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error handling the request", t);
        }
    }
}
