/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2024 SoftInstigate
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
package org.restheart.mongodb.hal;

import com.mongodb.MongoBulkWriteException;
import com.mongodb.bulk.BulkWriteError;
import com.mongodb.bulk.BulkWriteResult;
import io.undertow.server.HttpServerExchange;
import java.util.List;
import org.bson.BsonArray;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.restheart.exchange.IllegalQueryParameterException;
import org.restheart.exchange.MongoResponse;
import org.restheart.mongodb.db.BulkOperationResult;
import org.restheart.mongodb.utils.ResponseHelper;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.RepresentationUtils;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
class BulkResultRepresentationFactory extends AbstractRepresentationFactory {

    /**
     *
     */
    public BulkResultRepresentationFactory() {
    }

    /**
     *
     * @param exchange
     * @param result
     * @return
     * @throws IllegalQueryParameterException
     */
    public Resource getRepresentation(HttpServerExchange exchange, BulkOperationResult result)
            throws IllegalQueryParameterException {
        final String requestPath = buildRequestPath(exchange);
        final Resource rep = createRepresentation(exchange, null);

        addBulkResult(result, MongoResponse.of(exchange), rep, requestPath);

        return rep;
    }

    /**
     *
     * @param exchange
     * @param mbwe
     * @return
     * @throws IllegalQueryParameterException
     */
    public Resource getRepresentation(HttpServerExchange exchange, MongoBulkWriteException mbwe)
            throws IllegalQueryParameterException {
        final String requestPath = buildRequestPath(exchange);
        final Resource rep = createRepresentation(exchange, exchange.getRequestPath());

        addWriteResult(mbwe.getWriteResult(), rep, requestPath);

        addWriteErrors(mbwe.getWriteErrors(), rep);

        return rep;
    }

    private void addBulkResult(
            final BulkOperationResult result,
            final MongoResponse response,
            final Resource rep,
            final String requestPath) {
        Resource nrep = new Resource();

        BulkWriteResult wr = result.getBulkResult();

        if (wr.wasAcknowledged()) {
            if (wr.getUpserts() != null || wr.getInserts() != null) {
                    nrep.addProperty("inserted", new BsonInt32(
                            (wr.getUpserts() != null ? wr.getUpserts().size() : 0) +
                            (wr.getInserts() != null ? wr.getInserts().size() : 0)));

                    // add links to new, upserted documents
                    if (wr.getUpserts() != null) {
                            wr.getUpserts().stream().forEach(update -> nrep.addLink(new Link("rh:newdoc",
                                            RepresentationUtils.getReferenceLink(response, requestPath, update.getId())), true));
                    }

                    // add links to new, inserted documents
                    if (wr.getInserts() != null) {
                            wr.getInserts().stream().forEach(insert -> nrep.addLink(new Link("rh:newdoc",
                                    RepresentationUtils.getReferenceLink(response, requestPath, insert.getId())), true));
                    }
            }

            nrep.addProperty("deleted",
                    new BsonInt32(wr.getDeletedCount()));

            nrep.addProperty("modified",
                    new BsonInt32(wr.getModifiedCount()));

            nrep.addProperty("matched",
                    new BsonInt32(wr.getMatchedCount()));

            rep.addChild("rh:result", nrep);
        }
    }

    private void addWriteResult(
            final BulkWriteResult wr,
            final Resource rep,
            final String requestPath) {
        Resource nrep = new Resource();

        if (wr.wasAcknowledged()) {
            if (wr.getUpserts() != null || wr.getInserts() != null) {
                nrep.addProperty("inserted", new BsonInt32(
                        (wr.getUpserts() != null ? wr.getUpserts().size() : 0) +
                        (wr.getInserts() != null ? wr.getInserts().size() : 0)));

                // add links to new, upserted documents
                if (wr.getUpserts() != null) {
                        wr.getUpserts().stream().forEach(update -> nrep.addLink(new Link("rh:newdoc",
                                    RepresentationUtils.getReferenceLink(requestPath, update.getId())), true));
                }

                // add links to new, inserted documents
                if (wr.getInserts() != null) {
                        wr.getInserts().stream().forEach(insert -> nrep.addLink(new Link("rh:newdoc",
                                RepresentationUtils.getReferenceLink(requestPath, insert.getId())), true));
                }
            }

            nrep.addProperty("deleted",
                    new BsonInt32(wr.getDeletedCount()));

            nrep.addProperty("modified",
                    new BsonInt32(wr.getModifiedCount()));

            nrep.addProperty("matched",
                    new BsonInt32(wr.getMatchedCount()));

            rep.addChild("rh:result", nrep);
        }
    }

    private void addWriteErrors(
            final List<BulkWriteError> wes,
            final Resource rep) {
        wes.stream().forEach(error -> {
            Resource nrep = new Resource();

            // error 11000 is duplicate key error
            // happens when the _id and a filter are specified,
            // the document exists but does not match the filter
            if (error.getCode() == 11000
                    && error.getMessage().contains("_id_ dup key")) {
                nrep.addProperty("index",
                        new BsonInt32(error.getIndex()));
                nrep.addProperty("httpStatus",
                        new BsonInt32(
                                ResponseHelper.getHttpStatusFromErrorCode(
                                        error.getCode())));
            } else if (error.getCode() == 2) {
                nrep.addProperty("index",
                        new BsonInt32(error.getIndex()));
                nrep.addProperty("httpStatus",
                        new BsonInt32(
                                ResponseHelper.getHttpStatusFromErrorCode(
                                        error.getCode())));
                nrep.addProperty("message",
                        new BsonString(
                                ResponseHelper.getMessageFromErrorCode(
                                        error.getCode())
                                + ": "
                                + error.getMessage()));
            } else {
                nrep.addProperty("index",
                        new BsonInt32(error.getIndex()));
                nrep.addProperty("mongodbErrorCode",
                        new BsonInt32(error.getCode()));
                nrep.addProperty("httpStatus",
                        new BsonInt32(HttpStatus.SC_NOT_FOUND));
                nrep.addProperty("message",
                        new BsonString(
                                ResponseHelper.getMessageFromErrorCode(
                                        error.getCode())));
            }

            rep.addChild("rh:error", nrep);
        });
    }

    /**
     *
     * @param exchange
     * @param embeddedData
     * @param size
     * @return
     * @throws IllegalQueryParameterException
     */
    @Override
    public Resource getRepresentation(HttpServerExchange exchange,
            BsonArray embeddedData, long size)
            throws IllegalQueryParameterException {
        throw new UnsupportedOperationException("Not supported."); //To change body of generated methods, choose Tools | Templates.
    }
}
