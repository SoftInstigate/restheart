/*
 * RESTHeart - the Web API for MongoDB
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
package org.restheart.handlers.collection.bulk;

import com.mongodb.DBObject;
import com.mongodb.bulk.BulkWriteResult;
import org.restheart.hal.Representation;
import org.restheart.handlers.IllegalQueryParamenterException;
import org.restheart.handlers.RequestContext;
import io.undertow.server.HttpServerExchange;
import java.util.List;
import org.restheart.db.BulkOperationResult;
import org.restheart.hal.AbstractRepresentationFactory;
import org.restheart.hal.Link;
import org.restheart.utils.URLUtils;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class BulkPostRepresentationFactory extends AbstractRepresentationFactory {
    public BulkPostRepresentationFactory() {
    }

    public Representation getRepresentation(HttpServerExchange exchange, RequestContext context, BulkOperationResult result)
            throws IllegalQueryParamenterException {
        final String requestPath = buildRequestPath(exchange);
        final Representation rep = createRepresentation(exchange, context, requestPath);

        addBulkResult(result, context, rep, requestPath);

        // empty curies section. this is needed due to HAL browser issue
        // https://github.com/mikekelly/hal-browser/issues/71
        rep.addLinkArray("curies");

        return rep;
    }

    private void addBulkResult(
            final BulkOperationResult result,
            final RequestContext context,
            final Representation rep,
            final String requestPath) {

        BulkWriteResult wr = result.getWriteResult();

        if (wr.getUpserts() != null) {
            rep.addProperty("_inserted", wr.getUpserts().size());
        } else {
            rep.addProperty("_inserted", 0);
        }
        
        if (wr.isModifiedCountAvailable()) {
            rep.addProperty("_modified", wr.getModifiedCount());
        }

        // add links to new, upserted documents
        result.getWriteResult().getUpserts().stream().
                forEach(update -> {
                    rep.addLink(
                            new Link("rh:newdoc",
                                    URLUtils.getReferenceLink(context, requestPath, update.getId())),
                            true);
                });
    }

    @Override
    public Representation getRepresentation(HttpServerExchange exchange, RequestContext context, List<DBObject> embeddedData, long size) throws IllegalQueryParamenterException {
        throw new UnsupportedOperationException("Not supported."); //To change body of generated methods, choose Tools | Templates.
    }

}
