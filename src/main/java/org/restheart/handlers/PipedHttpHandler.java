/*
 * RESTHeart - the data REST API server
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

import com.mongodb.BasicDBObject;
import org.restheart.handlers.document.DocumentRepresentationFactory;
import org.restheart.utils.HttpStatus;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import java.net.URISyntaxException;
import org.bson.types.ObjectId;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public abstract class PipedHttpHandler implements HttpHandler {

    protected final PipedHttpHandler next;

    public PipedHttpHandler(PipedHttpHandler next) {
        this.next = next;
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    public abstract void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception;

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        handleRequest(exchange, null);
    }

    protected static void sendWarnings(int SC, HttpServerExchange exchange, RequestContext context) throws IllegalQueryParamenterException, URISyntaxException {
        if (SC == HttpStatus.SC_NO_CONTENT) {
            exchange.setResponseCode(HttpStatus.SC_OK);
        } else {
            exchange.setResponseCode(SC);
        }

        DocumentRepresentationFactory.sendDocument(exchange.getRequestPath(), exchange, context, new BasicDBObject());
    }
}