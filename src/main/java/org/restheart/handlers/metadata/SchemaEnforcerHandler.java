/*
 * RESTHeart - the data REST API server
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
package org.restheart.handlers.metadata;

import io.undertow.server.HttpServerExchange;
import org.restheart.hal.metadata.InvalidMetadataException;
import org.restheart.hal.metadata.SchemaChecker;
import org.restheart.hal.metadata.singletons.Checker;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.NamedSingletonsFactory;
import org.restheart.utils.ResponseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class SchemaEnforcerHandler extends PipedHttpHandler {
    static final Logger LOGGER = LoggerFactory.getLogger(SchemaEnforcerHandler.class);

    /**
     * Creates a new instance of SchemaEnforcerHandler
     *
     * @param next
     */
    public SchemaEnforcerHandler(PipedHttpHandler next) {
        super(next);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        if (doesSchemaCheckerAppy(context)) {
            if (checkSchema(exchange, context)) {
                getNext().handleRequest(exchange, context);
            } else {
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_BAD_REQUEST, "request data does not fulfill the collection schema check constraint");
            }
        } else {
            getNext().handleRequest(exchange, context);
        }
    }

    private boolean doesSchemaCheckerAppy(RequestContext context) {
        return context.getCollectionProps() != null
                && context.getCollectionProps().containsField(SchemaChecker.SC_ELEMENT_NAME);
    }

    private boolean checkSchema(HttpServerExchange exchange, RequestContext context) throws InvalidMetadataException {
        SchemaChecker sc = SchemaChecker.getFromJson(context.getCollectionProps());

        // evaluate the script on document
        Checker checker = (Checker) NamedSingletonsFactory.getInstance().get("checkers", sc.getName());

        if (checker == null) {
            throw new IllegalArgumentException("cannot find singleton " + sc.getName() + " in singleton group checkers");
        }

        return checker.check(exchange, context, sc.getArgs());
    }
}
