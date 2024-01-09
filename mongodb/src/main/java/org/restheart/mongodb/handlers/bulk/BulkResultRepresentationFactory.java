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
package org.restheart.mongodb.handlers.bulk;

import com.mongodb.MongoBulkWriteException;
import com.mongodb.bulk.BulkWriteError;
import com.mongodb.bulk.BulkWriteResult;
import java.util.List;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.restheart.mongodb.db.BulkOperationResult;
import org.restheart.mongodb.utils.ResponseHelper;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.RepresentationUtils;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class BulkResultRepresentationFactory {

    /**
     *
     */
    public BulkResultRepresentationFactory() {
    }

    /**
     *
     * @param requestPath
     * @param result
     * @return
     */
    public BsonDocument getRepresentation(String requestPath, BulkOperationResult result) {
        var rep = new BsonDocument();

        addBulkResult(result, requestPath, rep);

        return rep;
    }

    /**
     *
     * @param requestPath
     * @param mbwe
     * @return
     */
    public BsonDocument getRepresentation(String requestPath, MongoBulkWriteException mbwe) {

        var rep = new BsonDocument();

        addWriteResult(mbwe.getWriteResult(), rep, requestPath);

        addWriteErrors(mbwe.getWriteErrors(), rep);

        return rep;
    }

    private void addBulkResult(final BulkOperationResult result, final String requestPath, final BsonDocument rep) {
        BulkWriteResult wr = result.getBulkResult();

        if (wr.wasAcknowledged()) {
            if (wr.getUpserts() != null || wr.getInserts() != null) {
                rep.put("inserted", new BsonInt32((wr.getUpserts() != null ? wr.getUpserts().size() : 0)
                        + (wr.getInserts() != null ? wr.getInserts().size() : 0)));

                var links = new BsonArray();

                // add links to new, upserted documents
                if (wr.getUpserts() != null) {
                        wr.getUpserts().stream().forEach(update -> links.add(new BsonString(
                                RepresentationUtils.getReferenceLink(requestPath, update.getId()))));
                }

                // add links to new, inserted documents
                if (wr.getInserts() != null) {
                        wr.getInserts().stream().forEach(update -> links.add(new BsonString(
                                        RepresentationUtils.getReferenceLink(requestPath,update.getId()))));
                }

                rep.put("links", links);
            }

            rep.put("deleted",
                    new BsonInt32(wr.getDeletedCount()));

            rep.put("modified",
                    new BsonInt32(wr.getModifiedCount()));

            rep.put("matched",
                    new BsonInt32(wr.getMatchedCount()));
        }
    }

    private void addWriteResult(final BulkWriteResult wr, final BsonDocument rep, final String requestPath) {
        if (wr.wasAcknowledged()) {
            if (wr.getUpserts() != null || wr.getInserts() != null) {
                rep.put("inserted", new BsonInt32((wr.getUpserts() != null ? wr.getUpserts().size() : 0)
                        + (wr.getInserts() != null ? wr.getInserts().size() : 0)));

                var links = new BsonArray();

                // add links to new, upserted documents
                if (wr.getUpserts() != null) {
                        wr.getUpserts().stream().forEach(update -> links.add(new BsonString(
                                RepresentationUtils.getReferenceLink(requestPath, update.getId()))));
                }

                // add links to new, inserted documents
                if (wr.getInserts() != null) {
                        wr.getInserts().stream().forEach(update -> links.add(new BsonString(
                                        RepresentationUtils.getReferenceLink(requestPath,update.getId()))));
                }

                rep.put("links", links);
            }

            rep.put("deleted",
                    new BsonInt32(wr.getDeletedCount()));

            rep.put("modified",
                    new BsonInt32(wr.getModifiedCount()));

            rep.put("matched",
                    new BsonInt32(wr.getMatchedCount()));
        }
    }

    private void addWriteErrors(
            final List<BulkWriteError> wes,
            final BsonDocument rep) {
        var errors = new BsonArray();

        wes.stream().forEach(error -> {
            var errorDoc = new BsonDocument();
            // error 11000 is duplicate key error
            // happens when the _id and a filter are specified,
            // the document exists but does not match the filter
            if (error.getCode() == 11000
                    && error.getMessage().contains("_id_ dup key")) {
                errorDoc.put("index",
                        new BsonInt32(error.getIndex()));
                errorDoc.put("httpStatus",
                        new BsonInt32(
                                ResponseHelper.getHttpStatusFromErrorCode(
                                        error.getCode())));
            } else if (error.getCode() == 2) {
                errorDoc.put("index",
                        new BsonInt32(error.getIndex()));
                errorDoc.put("httpStatus",
                        new BsonInt32(
                                ResponseHelper.getHttpStatusFromErrorCode(
                                        error.getCode())));
                errorDoc.put("message",
                        new BsonString(
                                ResponseHelper.getMessageFromErrorCode(
                                        error.getCode())
                                + ": "
                                + error.getMessage()));
            } else {
                errorDoc.put("index",
                        new BsonInt32(error.getIndex()));
                errorDoc.put("mongodbErrorCode",
                        new BsonInt32(error.getCode()));
                errorDoc.put("httpStatus",
                        new BsonInt32(HttpStatus.SC_NOT_FOUND));
                errorDoc.put("message",
                        new BsonString(
                                ResponseHelper.getMessageFromErrorCode(
                                        error.getCode())));
            }

            errors.add(errorDoc);

        });

        if (errors.size() > 0) {
            rep.put("errors", errors);
        }
    }
}
