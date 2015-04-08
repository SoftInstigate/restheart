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
package org.restheart.handlers.database;

import com.mongodb.DBObject;
import org.restheart.utils.HttpStatus;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import io.undertow.server.HttpServerExchange;
import java.util.List;
import org.restheart.db.Database;
import org.restheart.db.DbsDAO;
import org.restheart.hal.Representation;
import org.restheart.handlers.IllegalQueryParamenterException;
import org.restheart.handlers.collection.CollectionRepresentationFactory;
import org.restheart.utils.ResponseHelper;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class GetDBHandler extends PipedHttpHandler {

    /**
     * Creates a new instance of GetDBHandler
     */
    public GetDBHandler() {
        super();
    }

    public GetDBHandler(PipedHttpHandler next) {
        super(next, new DbsDAO());
    }

    public GetDBHandler(PipedHttpHandler next, Database dbsDAO) {
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
        List<String> colls = getDatabase().getCollectionNames(getDatabase().getDB(context.getDBName()));

        List<DBObject> data = null;

        if (context.getPagesize() > 0) {
            data = getDatabase().getData(context.getDBName(), colls, context.getPage(), context.getPagesize());
        }

        DBRepresentationFactory repf = new DBRepresentationFactory();
        Representation rep = repf.getRepresentation(exchange, context, data, getDatabase().getDBSize(colls));

        exchange.setResponseCode(HttpStatus.SC_OK);

        // call the ResponseScriptMetadataHanlder if piped in
        if (getNext() != null) {
            DBObject responseContent = rep.asDBObject();
            context.setResponseContent(responseContent);

            getNext().handleRequest(exchange, context);
        }

        repf.sendRepresentation(exchange, context, rep);

        exchange.endExchange();
    }
}
