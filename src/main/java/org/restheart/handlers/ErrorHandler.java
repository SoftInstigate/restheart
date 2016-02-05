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
import com.mongodb.MongoTimeoutException;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.restheart.hal.Representation;
import org.restheart.handlers.bulk.BulkResultRepresentationFactory;
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
        } catch(MongoBulkWriteException mce) {
            MongoBulkWriteException bmce = (MongoBulkWriteException) mce;

            BulkResultRepresentationFactory rf = new BulkResultRepresentationFactory();
            
            Representation rep = rf.getRepresentation(exchange, bmce);
            
            exchange.setStatusCode(HttpStatus.SC_MULTI_STATUS);
            
            rf.sendRepresentation(exchange, null, rep);
            
            exchange.endExchange();
        } catch (MongoException mce) {
            switch (mce.getCode()) {
                case 13:
                    ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_FORBIDDEN, "The MongoDB user does not have enough permissions to execute this operation.");
                    break;
                case 18:
                    ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_FORBIDDEN, "Wrong MongoDB user credentials (wrong password or need to specify the authentication dababase with 'authSource=<db>' option in mongo-uri).");
                    break;
                case 121:
                    //Document failed validation
                    ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_BAD_REQUEST, "Document failed collection validation.");
                    break;
                default:
                    ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error handling the request, see log for more information", mce);
                    break;
            }
        } catch (Throwable t) {
            LOGGER.error("Error handling the request", t);

            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error handling the request, see log for more information", t);
        }
    }
}
