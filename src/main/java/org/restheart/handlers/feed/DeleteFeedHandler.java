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
package org.restheart.handlers.feed;

import io.undertow.server.HttpServerExchange;
import java.util.Set;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @author Omar Trasatti {@literal <omar@softinstigate.com>}
 */
public class DeleteFeedHandler extends PipedHttpHandler {

    public DeleteFeedHandler() {
        super();
    }

    /**
     * Default ctor
     *
     * @param next
     */
    public DeleteFeedHandler(PipedHttpHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange,
            RequestContext context) throws Exception {

        if (context.isInError()) {
            next(exchange, context);
            return;
        }

        String changeStreamUriPath = context
                .getUri()
                .getPath();

        if (context.getFeedIdentifier() != null) {
            Set<String> changeStreamUriSet = CacheManagerSingleton
                    .getChangeStreamsUriSet();

            if (!changeStreamUriSet.isEmpty()) {

                if (changeStreamUriSet.contains(changeStreamUriPath)) {
                    CacheManagerSingleton.removeChangeStream(changeStreamUriPath);
                    ResponseHelper.endExchangeWithMessage(
                            exchange,
                            context,
                            HttpStatus.SC_NO_CONTENT,
                            null);
                } else {
                    String responseMessage = "Feed "
                            + getFeedIdentifier(changeStreamUriPath)
                            + " hasn't been found";

                    ResponseHelper.endExchangeWithMessage(
                            exchange,
                            context,
                            HttpStatus.SC_NOT_FOUND,
                            responseMessage);
                }

                next(exchange, context);

            } else {
                ResponseHelper.endExchangeWithMessage(
                        exchange,
                        context,
                        HttpStatus.SC_NOT_FOUND,
                        "No feeds are notifying for this feedOperation");

                next(exchange, context);
            }
        } else {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_BAD_REQUEST,
                    null);

            next(exchange, context);
        }

    }

    private String getFeedIdentifier(String path) {
        String[] pathArray = path.split("/");
        return pathArray[pathArray.length - 1];
    }

}
