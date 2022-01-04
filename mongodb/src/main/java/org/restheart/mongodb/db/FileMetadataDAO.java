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
package org.restheart.mongodb.db;

import com.mongodb.MongoClient;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import static com.mongodb.client.model.Filters.eq;
import java.util.Objects;
import java.util.Optional;

import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.types.ObjectId;
import org.restheart.exchange.ExchangeKeys.METHOD;
import org.restheart.utils.HttpStatus;

/**
 * This DAO takes care of changes to metadata for binary files that have been
 * created using GridFS.
 *
 * @author Nath Papadacis {@literal <nath@thirststudios.co.uk>}
 */
public class FileMetadataDAO implements FileMetadataRepository {

    private final MongoClient client;
    private final CollectionDAO collectionDAO;

    /**
     *
     */
    public FileMetadataDAO() {
        client = MongoClientSingleton.getInstance().getClient();
        collectionDAO = new CollectionDAO(client);
    }

    /**
     *
     * @param cs the client session
     * @param method the request method
     * @param dbName
     * @param collName
     * @param documentId
     * @param filter
     * @param shardKeys
     * @param newContent
     * @param requestEtag
     * @param checkEtag
     * @return
     */
    @Override
    public OperationResult updateFileMetadata(
            final Optional<ClientSession> cs,
            final METHOD method,
            final String dbName,
            final String collName,
            final Optional<BsonValue> documentId,
            final Optional<BsonDocument> filter,
            final Optional<BsonDocument> shardKeys,
            final BsonDocument newContent,
            final String requestEtag,
            final boolean checkEtag) {
        var mcoll = collectionDAO.getCollection(dbName, collName);

        // genereate new etag
        var newEtag = new BsonObjectId();

        final BsonDocument content = DAOUtils.validContent(newContent);
        content.get("metadata", new BsonDocument()).asDocument().put("_etag", newEtag);

        var updateResult = DAOUtils.updateFileMetadata(
                cs,
                mcoll,
                method,
                documentId,
                filter,
                shardKeys,
                content);

        var oldDocument = updateResult.getOldData();

        switch(method) {
            case PUT -> {
                if (oldDocument != null && checkEtag) { // update
                    // check the old etag (in case restore the old document)
                    return optimisticCheckEtag(
                        cs,
                        mcoll,
                        shardKeys,
                        oldDocument,
                        newEtag,
                        requestEtag,
                        HttpStatus.SC_OK);
                } else if (oldDocument != null) {  // update
                    var query = eq("_id", documentId);
                    var newDocument = cs.isPresent() ? mcoll.find(cs.get(), query).first() : mcoll.find(query).first();
                    return new OperationResult(updateResult.getHttpCode() > 0 ? updateResult.getHttpCode() : HttpStatus.SC_OK, newEtag, oldDocument, newDocument);
                } else { // Attempted an insert of a new doc.
                    return new OperationResult(updateResult.getHttpCode() > 0 ? updateResult.getHttpCode() : HttpStatus.SC_CONFLICT, newEtag, null, updateResult.getNewData());
                }
            }

            case PATCH -> {
                if (oldDocument == null) { // Attempted an insert of a new doc.
                    return new OperationResult(updateResult.getHttpCode() > 0
                        ? updateResult.getHttpCode()
                        : HttpStatus.SC_CONFLICT, newEtag, null, updateResult.getNewData());
                } else if (checkEtag) {
                    // check the old etag (in case restore the old document version)
                    return optimisticCheckEtag(
                        cs,
                        mcoll,
                        shardKeys,
                        oldDocument,
                        newEtag,
                        requestEtag,
                        HttpStatus.SC_OK);
                } else {
                    var query = eq("_id", documentId);

                    var newDocument = cs.isPresent()
                        ? mcoll.find(cs.get(), query).first()
                        : mcoll.find(query).first();

                    return new OperationResult(updateResult.getHttpCode() > 0
                            ? updateResult.getHttpCode()
                            : HttpStatus.SC_OK, newEtag, oldDocument, newDocument);
                }
            }

            default -> throw new UnsupportedOperationException("method not supported " + method == null ? "null" : method.name());
        }
    }

    private OperationResult optimisticCheckEtag(
            final Optional<ClientSession> cs,
            final MongoCollection<BsonDocument> coll,
            final Optional<BsonDocument> shardKeys,
            final BsonDocument oldDocument,
            final Object newEtag,
            final String requestEtag,
            final int httpStatusIfOk) {
        var oldEtag = oldDocument.get("metadata", new BsonDocument()).asDocument().get("_etag");

        if (oldEtag != null && requestEtag == null) {
            // oops, we need to restore old document
            DAOUtils.restoreDocument(
                    cs,
                    coll,
                    oldDocument.get("_id"),
                    shardKeys,
                    oldDocument,
                    newEtag,
                    "metadata._etag");

            return new OperationResult(HttpStatus.SC_CONFLICT, oldEtag, oldDocument, null);
        }

        BsonValue _requestEtag;

        if (ObjectId.isValid(requestEtag)) {
            _requestEtag = new BsonObjectId(new ObjectId(requestEtag));
        } else {
            // restheart generates ObjectId etags, but here we support
            // strings as well
            _requestEtag = new BsonString(requestEtag);
        }

        if (Objects.equals(_requestEtag, oldEtag)) {
            var query = eq("_id", oldDocument.get("_id"));
            var newDocument = cs.isPresent()
                ? coll.find(cs.get(), query).first()
                : coll.find(query).first();

            return new OperationResult(httpStatusIfOk, newEtag, oldDocument, newDocument);
        } else {
            // oops, we need to restore old document
            DAOUtils.restoreDocument(
                cs,
                coll,
                oldDocument.get("_id"),
                shardKeys,
                oldDocument,
                newEtag,
                "metadata._etag");

            return new OperationResult(HttpStatus.SC_PRECONDITION_FAILED, oldEtag, oldDocument, null);
        }
    }
}
