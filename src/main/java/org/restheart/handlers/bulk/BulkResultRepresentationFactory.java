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
package org.restheart.handlers.bulk;

import com.mongodb.MongoBulkWriteException;
import com.mongodb.bulk.BulkWriteError;
import com.mongodb.bulk.BulkWriteResult;
import org.restheart.hal.Representation;
import org.restheart.handlers.IllegalQueryParamenterException;
import org.restheart.handlers.RequestContext;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import java.util.List;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.restheart.db.BulkOperationResult;
import org.restheart.hal.AbstractRepresentationFactory;
import org.restheart.hal.Link;
import static org.restheart.hal.Representation.HAL_JSON_MEDIA_TYPE;
import org.restheart.utils.ResponseHelper;
import org.restheart.utils.URLUtils;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class BulkResultRepresentationFactory extends AbstractRepresentationFactory {
    public BulkResultRepresentationFactory() {
    }

    public Representation getRepresentation(HttpServerExchange exchange, RequestContext context, BulkOperationResult result)
            throws IllegalQueryParamenterException {
        final String requestPath = buildRequestPath(exchange);
        final Representation rep = createRepresentation(exchange, context, null);

        addBulkResult(result, context, rep, requestPath);

        return rep;
    }

    public Representation getRepresentation(HttpServerExchange exchange, MongoBulkWriteException mbwe)
            throws IllegalQueryParamenterException {
        final String requestPath = buildRequestPath(exchange);
        final Representation rep = createRepresentation(exchange, null, exchange.getRequestPath());

        addWriteResult(mbwe.getWriteResult(), rep, requestPath);

        addWriteErrors(mbwe.getWriteErrors(), rep);

        return rep;
    }

    private void addBulkResult(
            final BulkOperationResult result,
            final RequestContext context,
            final Representation rep,
            final String requestPath) {
        Representation nrep = new Representation();

        BulkWriteResult wr = result.getBulkResult();

        if (wr.wasAcknowledged()) {
            if (wr.getUpserts() != null) {
                nrep.addProperty("inserted", 
                        new BsonInt32(wr.getUpserts().size()));

                // add links to new, upserted documents
                wr.getUpserts().stream().
                        forEach(update -> {
                            nrep.addLink(
                                    new Link("rh:newdoc",
                                            URLUtils
                                            .getReferenceLink(
                                                    context,
                                                    requestPath,
                                                    update.getId())),
                                    true);
                        });
            }

            nrep.addProperty("deleted", 
                    new BsonInt32(wr.getDeletedCount()));

            if (wr.isModifiedCountAvailable()) {
                nrep.addProperty("modified", 
                        new BsonInt32(wr.getModifiedCount()));
            }

            nrep.addProperty("matched", 
                    new BsonInt32(wr.getMatchedCount()));

            rep.addRepresentation("rh:result", nrep);
        }
    }

    private void addWriteResult(
            final BulkWriteResult wr,
            final Representation rep,
            final String requestPath) {
        Representation nrep = new Representation();

        if (wr.wasAcknowledged()) {
            if (wr.getUpserts() != null) {
                nrep.addProperty("inserted", 
                        new BsonInt32(wr.getUpserts().size()));

                // add links to new, upserted documents
                wr.getUpserts().stream().
                        forEach(update -> {
                            nrep.addLink(
                                    new Link("rh:newdoc",
                                            URLUtils
                                            .getReferenceLink(
                                                    requestPath,
                                                    update.getId())),
                                    true);
                        });
            }

            nrep.addProperty("deleted", 
                    new BsonInt32(wr.getDeletedCount()));

            if (wr.isModifiedCountAvailable()) {
                nrep.addProperty("modified", 
                        new BsonInt32(wr.getModifiedCount()));
            }

            nrep.addProperty("matched", 
                    new BsonInt32(wr.getMatchedCount()));

            rep.addRepresentation("rh:result", nrep);
        }
    }

    private void addWriteErrors(
            final List<BulkWriteError> wes,
            final Representation rep) {
        wes.stream().forEach(error -> {
            Representation nrep = new Representation();

            nrep.addProperty("index",
                    new BsonInt32(error.getIndex()));
            nrep.addProperty("mongodbErrorCode",
                    new BsonInt32(error.getCode()));
            nrep.addProperty("httpStatus",
                    new BsonInt32(
                            ResponseHelper.getHttpStatusFromErrorCode(
                                    error.getCode())));
            nrep.addProperty("message",
                    new BsonString(
                            ResponseHelper.getMessageFromErrorCode(
                                    error.getCode())));

            rep.addRepresentation("rh:error", nrep);
        });
    }

    @Override
    public Representation getRepresentation(HttpServerExchange exchange, RequestContext context, List<BsonDocument> embeddedData, long size) throws IllegalQueryParamenterException {
        throw new UnsupportedOperationException("Not supported."); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     *
     * @param exchange
     * @param context
     * @param rep
     */
    public void sendRepresentation(HttpServerExchange exchange, RequestContext context, Representation rep) {
        if (context != null
                && context.getWarnings() != null) {
            context.getWarnings().forEach(w -> rep.addWarning(w));
        }

        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, HAL_JSON_MEDIA_TYPE);
        exchange.getResponseSender().send(rep.toString());
    }
}
