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
package org.restheart.mongodb.handlers.root;

import com.google.common.annotations.VisibleForTesting;
import io.undertow.server.HttpServerExchange;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.bson.BsonArray;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.db.Database;
import org.restheart.mongodb.db.DatabaseImpl;
import org.restheart.mongodb.interceptors.MetadataCachesSingleton;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class GetRootHandler extends PipelinedHandler {
    private Database dbsDAO = new DatabaseImpl();

    /**
     *
     */
    public GetRootHandler() {
        super();
    }

    /**
     *
     * @param next
     */
    public GetRootHandler(PipelinedHandler next) {
        super(next);
    }

    /**
     *
     * @param next
     * @param dbsDAO
     */
    @VisibleForTesting
    public GetRootHandler(PipelinedHandler next, Database dbsDAO) {
        super(next);
        this.dbsDAO = dbsDAO;
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = MongoRequest.of(exchange);
        var response = MongoResponse.of(exchange);

        if (request.isInError()) {
            next(exchange);
            return;
        }

        int size = 0;

        var data = new BsonArray();

        if (request.getPagesize() >= 0) {
            List<String> _dbs = dbsDAO.getDatabaseNames(
                    request.getClientSession());

            // filter out reserved resources
            List<String> dbs = _dbs.stream()
                    .filter(db -> !MongoRequest.isReservedResourceDb(db))
                    .collect(Collectors.toList());

            if (dbs == null) {
                dbs = new ArrayList<>();
            }

            size = dbs.size();
            var page = request.getPage();
            var pagesize = request.getPagesize();

            if (size > 0) {
                float _size = size + 0f;
                float _pagesize = pagesize + 0f;

                var total_pages = Math.max(1, Math.round(Math.ceil(_size / _pagesize)));

                if (page <= total_pages) {

                    if (pagesize > 0) {
                        Collections.sort(dbs); // sort by id

                        // apply page and pagesize
                        dbs = dbs.subList((request.getPage() - 1) * pagesize,
                                (request.getPage() - 1) * pagesize
                                + pagesize > dbs.size()
                                ? dbs.size()
                                : (request.getPage() - 1) * pagesize
                                + pagesize);

                        dbs.stream().map(db -> {
                            if (MetadataCachesSingleton.isEnabled()) {
                                return MetadataCachesSingleton.getInstance()
                                        .getDBProperties(db);
                            } else {
                                return dbsDAO.getDatabaseProperties(
                                        request.getClientSession(),
                                        db);
                            }
                        }).forEachOrdered(db -> data.add(db));
                    }
                }
            }

            response.setCount(size);
            response.setContent(data);

            response.setContentTypeAsJson();
            response.setStatusCode(HttpStatus.SC_OK);

            next(exchange);
        }
    }
}
