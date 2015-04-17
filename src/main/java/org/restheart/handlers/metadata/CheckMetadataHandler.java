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
import java.util.List;
import org.restheart.hal.metadata.InvalidMetadataException;
import org.restheart.hal.metadata.RequestChecker;
import org.restheart.hal.metadata.singletons.Checker;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.HttpStatus;
import org.restheart.hal.metadata.singletons.NamedSingletonsFactory;
import org.restheart.utils.ResponseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class CheckMetadataHandler extends PipedHttpHandler {
    static final Logger LOGGER = LoggerFactory.getLogger(CheckMetadataHandler.class);

    /**
     * Creates a new instance of SchemaEnforcerHandler
     *
     * @param next
     */
    public CheckMetadataHandler(PipedHttpHandler next) {
        super(next);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        if (doesCheckerAppy(context)) {
            if (check(exchange, context)) {
                getNext().handleRequest(exchange, context);
            } else {
                String msg = "request data does not fulfill the collection schema check constraint";
                
                List<String> warnings = context.getWarnings();
                
                if (warnings != null && !warnings.isEmpty()) {
                    msg = msg + ". " + warnings.get(0);
                }
                
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_BAD_REQUEST, msg);
            }
        } else {
            getNext().handleRequest(exchange, context);
        }
    }

    private boolean doesCheckerAppy(RequestContext context) {
        return context.getCollectionProps() != null
                && context.getCollectionProps().containsField(RequestChecker.SCS_ELEMENT_NAME);
    }

    private boolean check(HttpServerExchange exchange, RequestContext context) throws InvalidMetadataException {
        List<RequestChecker> checkers = RequestChecker.getFromJson(context.getCollectionProps());

        return checkers.stream().allMatch(checker -> {
            try {
                Checker _checker = (Checker) NamedSingletonsFactory.getInstance().get("checkers", checker.getName());

                if (_checker == null) {
                    throw new IllegalArgumentException("cannot find singleton " + checker.getName() + " in singleton group checkers");
                }

                return _checker.check(exchange, context, checker.getArgs());

            } catch (IllegalArgumentException ex) {
                context.addWarning("error applying checker: " + ex.getMessage());
                return false;
            }
        });
    }
}