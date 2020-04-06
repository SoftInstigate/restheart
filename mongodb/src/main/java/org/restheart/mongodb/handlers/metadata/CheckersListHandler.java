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
package org.restheart.mongodb.handlers.metadata;

import io.undertow.server.HttpServerExchange;
import java.util.Arrays;
import java.util.List;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.handlers.exchange.BsonRequest;
import org.restheart.handlers.exchange.BsonResponse;
import org.restheart.handlers.exchange.RequestContext;
import org.restheart.plugins.mongodb.Checker;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * handler that applies the checkers passed in the costructor
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class CheckersListHandler extends PipelinedHandler {

    static final Logger LOGGER = LoggerFactory.getLogger(CheckersListHandler.class);
    
    private final List<Checker> checkers;

    /**
     * Creates a new instance of CheckHandler
     *
     * @param checkers
     */
    public CheckersListHandler(Checker... checkers) {
        this(null, checkers);
    }
        
    /**
     * Creates a new instance of CheckHandler
     *
     * @param next
     * @param checkers
     */
    public CheckersListHandler(PipelinedHandler next, Checker... checkers) {
        super(next);

        this.checkers = Arrays.asList(checkers);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = BsonRequest.wrap(exchange);
        var context = RequestContext.wrap(exchange);
        
        if (request.isInError()) {
            next(exchange);
            return;
        }
        
        if (doesCheckerAppy()) {
            if (check(exchange, context)) {
                next(exchange);
            } else {
                BsonResponse.wrap(exchange).setIError(
                        HttpStatus.SC_BAD_REQUEST,
                        "request check failed");
                next(exchange);
            }
        } else {
            next(exchange);
        }
    }

    private boolean doesCheckerAppy() {
        return checkers != null
                && !checkers.isEmpty();
    }

    private boolean check(
            HttpServerExchange exchange,
            RequestContext context)
            throws InvalidMetadataException {
        if (context.getContent() != null
                && !context.getContent().isDocument()) {
            throw new RuntimeException(
                    "this hanlder only supports content of type json object; "
                    + "content type: " + context
                            .getContent()
                            .getBsonType()
                            .name());
        }

        return checkers.stream().allMatch(checker
                -> checker.check(exchange,
                        context,
                        context.getContent().asDocument(),
                        null));
    }
}
