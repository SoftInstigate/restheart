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
package org.restheart.handlers.root;

import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import org.restheart.db.DbsDAO;
import org.restheart.db.MongoDBClientSingleton;
import org.restheart.utils.HttpStatus;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.injectors.LocalCachesSingleton;
import io.undertow.server.HttpServerExchange;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 * @author Andrea Di Cesare
 */
public class GetRootHandler extends PipedHttpHandler {

    private static final MongoClient client = MongoDBClientSingleton.getInstance().getClient();

    /**
     * Creates a new instance of GetRootHandler
     */
    public GetRootHandler() {
        super(null);
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        List<String> _dbs = client.getDatabaseNames();

        // filter out reserved resources
        List<String> dbs = _dbs.stream().filter(db -> !RequestContext.isReservedResourceDb(db)).collect(Collectors.toList());

        if (dbs == null) {
            dbs = new ArrayList<>();
        }

        int size = dbs.size();

        Collections.sort(dbs); // sort by id

        // apply page and pagesize
        dbs = dbs.subList((context.getPage() - 1) * context.getPagesize(), (context.getPage() - 1) * context.getPagesize() + context.getPagesize() > dbs.size() ? dbs.size() : (context.getPage() - 1) * context.getPagesize() + context.getPagesize());

        List<DBObject> data = new ArrayList<>();

        final DbsDAO dbsDAO = new DbsDAO();
        dbs.stream().map((db) -> {
            if (LocalCachesSingleton.isEnabled()) {
                return LocalCachesSingleton.getInstance().getDBProps(db);
            } else {
                return dbsDAO.getDbProps(db);
            }
        }
        ).forEach((item) -> {
            data.add(item);
        });

        exchange.setResponseCode(HttpStatus.SC_OK);
        new RootRepresentationFactory().sendHal(exchange, context, data, size);
        exchange.endExchange();
    }
}
