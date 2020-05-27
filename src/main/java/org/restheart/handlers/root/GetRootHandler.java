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
package org.restheart.handlers.root;

import io.undertow.server.HttpServerExchange;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.bson.BsonDocument;
import org.restheart.db.Database;
import org.restheart.representation.Resource;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.injectors.LocalCachesSingleton;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class GetRootHandler extends PipedHttpHandler {
    public GetRootHandler() {
        super();
    }

    public GetRootHandler(PipedHttpHandler next) {
        super(next);
    }

    public GetRootHandler(PipedHttpHandler next, Database dbsDAO) {
        super(next, dbsDAO);
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        if (context.isInError()) {
            next(exchange, context);
            return;
        }

        int size = 0;

        List<BsonDocument> data = new ArrayList<>();

        if (context.getPagesize() >= 0) {
            List<String> _dbs = getDatabase().getDatabaseNames(context.getClientSession());

            // filter out reserved resources
            List<String> dbs = _dbs.stream()
                    .filter(db -> !RequestContext.isReservedResourceDb(db))
                    .collect(Collectors.toList());

            if (dbs == null) {
                dbs = new ArrayList<>();
            }

            size = dbs.size();

            if (context.getPagesize() > 0) {
                Collections.sort(dbs); // sort by id

                // apply page and pagesize
                dbs = dbs.subList((context.getPage() - 1) * context.getPagesize(), (context.getPage() - 1) * context.getPagesize()
                        + context.getPagesize() > dbs.size() ? dbs.size() : (context.getPage() - 1) * context.getPagesize() + context.getPagesize());

                dbs.stream().map((db) -> {
                    if (LocalCachesSingleton.isEnabled() || context.isNoCache()) {
                        return LocalCachesSingleton.getInstance()
                                .getDBProperties(db);
                    } else {
                        return getDatabase()
                                .getDatabaseProperties(
                                        context.getClientSession(), 
                                        db);
                    }
                }
                ).forEach((item) -> {
                    data.add(item);
                });
            }
        }

        context.setResponseContent(new RootRepresentationFactory().
                getRepresentation(exchange, context, data, size)
                .asBsonDocument());

        context.setResponseContentType(Resource.HAL_JSON_MEDIA_TYPE);
        context.setResponseStatusCode(HttpStatus.SC_OK);

        next(exchange, context);
    }
}
