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
import org.restheart.db.DbsDAO;
import org.restheart.utils.HttpStatus;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import io.undertow.server.HttpServerExchange;
import java.util.List;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class GetDBHandler extends PipedHttpHandler {
    
    private final DbsDAO dbsDAO;

    /**
     * Creates a new instance of GetDBHandler
     */
    public GetDBHandler() {
        this(new DbsDAO());
    }

    public GetDBHandler(DbsDAO dbsDAO) {
        super(null);
        this.dbsDAO = dbsDAO;
    }
    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        List<String> colls = this.dbsDAO.getDbCollections(this.dbsDAO.getDB(context.getDBName()));
        List<DBObject> data = this.dbsDAO.getData(context.getDBName(), colls, context.getPage(), context.getPagesize());
        exchange.setResponseCode(HttpStatus.SC_OK);
        
        new DBRepresentationFactory().sendHal(exchange, context, data, this.dbsDAO.getDBSize(colls));
        exchange.endExchange();
    }
}
