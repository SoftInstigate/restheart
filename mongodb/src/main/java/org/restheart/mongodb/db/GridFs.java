/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
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
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;

import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.restheart.exchange.ExchangeKeys.METHOD;
import org.restheart.exchange.ExchangeKeys.WRITE_MODE;
import org.restheart.mongodb.RSOps;
import org.restheart.utils.HttpStatus;
import static org.restheart.utils.HttpStatus.SC_CONFLICT;
import static org.restheart.utils.HttpStatus.SC_CREATED;
import static org.restheart.utils.HttpStatus.SC_NOT_FOUND;
import static org.restheart.utils.HttpStatus.SC_NO_CONTENT;
import static org.restheart.utils.HttpStatus.SC_OK;
import static org.restheart.utils.HttpStatus.SC_PRECONDITION_FAILED;
import org.slf4j.LoggerFactory;

import com.mongodb.DuplicateKeyException;
import com.mongodb.MongoGridFSException;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class GridFs {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(GridFs.class);

    private static final String FILENAME = "filename";

    private final Collections collections = Collections.get();;
    private final Databases dbs = Databases.get();

    private GridFs() {
    }

    private static final GridFs INSTANCE = new GridFs();

    public static GridFs get() {
        return INSTANCE;
    }

    /**
     *
     * @param rsOps the ReplicaSet connection options
     * @param dbName the database name
     * @param bucketName
     * @param metadata
     * @param fileInputStream
     * @return the OperationResult
     * @throws java.io.IOException
     * @throws DuplicateKeyException
     */
    public OperationResult createFile(
        final Optional<RSOps> rsOps,
        final String dbName,
        final String bucketName,
        final BsonDocument metadata,
        final InputStream fileInputStream)
        throws IOException, DuplicateKeyException {
        final var db = dbs.db(rsOps, dbName);
        final var bucket = extractBucketName(bucketName);

        var gridFSBucket = GridFSBuckets.create(db, bucket);

        var filename = extractFilenameFromProperties(metadata);

        //add etag to metadata
        var etag = new ObjectId();
        metadata.put("_etag", new BsonObjectId(etag));

        try (fileInputStream) {
            if (metadata.get("_id") == null) {
                var options = new GridFSUploadOptions().metadata(Document.parse(metadata.toJson()));

                var _id = gridFSBucket.uploadFromStream(filename, fileInputStream, options);

                return new OperationResult(SC_CREATED, new BsonObjectId(etag), new BsonObjectId(_id));
            } else {
                var _id = metadata.remove("_id");

                var options = new GridFSUploadOptions().metadata(Document.parse(metadata.toJson()));

                gridFSBucket.uploadFromStream(_id, filename, fileInputStream, options);

                return new OperationResult(SC_CREATED, new BsonObjectId(etag), _id);
            }
        }
    }

    /**
     *
     * @param rsOps the ReplicaSet connection options
     * @param dbName the database name
     * @param bucketName
     * @param metadata
     * @param fileInputStream
     * @param filter
     * @param requestEtag
     * @param checkEtag
     * @return
     * @throws java.io.IOException
     */
    public OperationResult upsertFile(
        final Optional<RSOps> rsOps,
        final String dbName,
        final String bucketName,
        final BsonDocument metadata,
        final InputStream fileInputStream,
        final BsonDocument filter,
        final String requestEtag,
        final boolean checkEtag) throws IOException {

        final BsonValue id;
        final OperationResult deletionResult;

        if (metadata.containsKey("_id")) {
            id = metadata.get("_id");
            deletionResult = deleteFile(rsOps, dbName, bucketName, id, filter, requestEtag, checkEtag);
        } else {
            deletionResult = null;
        }

        //https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.7
        final boolean deleteOperationWasSuccessful = deletionResult == null ? true: deletionResult.getHttpCode() == SC_NO_CONTENT || deletionResult.getHttpCode() == SC_OK;
        final boolean fileDidntExist = deletionResult == null ? true : deletionResult.getHttpCode() == SC_NOT_FOUND;
        final boolean fileExisted = !fileDidntExist;

        if (deleteOperationWasSuccessful || fileDidntExist) {
            var creationResult = createFile(rsOps, dbName, bucketName, metadata, fileInputStream);

            //https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.5
            final boolean creationOperationWasSuccessful = SC_CREATED == creationResult.getHttpCode() || SC_OK == creationResult.getHttpCode();
            if (creationOperationWasSuccessful) {
                //https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.6
                if (fileExisted) {
                    return new OperationResult(SC_OK, creationResult.getEtag(), creationResult.getOldData(), creationResult.getNewData());
                } else {
                    return new OperationResult(SC_CREATED, creationResult.getEtag(), creationResult.getNewId());
                }
            } else {
                return creationResult;
            }
        } else {
            return deletionResult;
        }
    }

    private String extractFilenameFromProperties(final BsonDocument properties) {
        String filename = null;

        if (properties != null && properties.containsKey(FILENAME)) {
            var _filename = properties.get(FILENAME);

            if (_filename != null && _filename.isString()) {
                filename = _filename.asString().getValue();
            }
        }

        if (filename == null) {
            return "file";
        } else {
            return filename;
        }
    }

    /**
     *
     * @param rsOps the ReplicaSet connection options
     * @param dbName the database name
     * @param bucketName
     * @param fileId
     * @param filter
     * @param requestEtag
     * @param checkEtag
     * @return the OperationResult
     */
    public OperationResult deleteFile(
        final Optional<RSOps> rsOps,
        final String dbName,
        final String bucketName,
        final BsonValue fileId,
        final BsonDocument filter,
        final String requestEtag,
        final boolean checkEtag) {
        final var db = dbs.db(rsOps, dbName);
        final var bucket = extractBucketName(bucketName);

        var gridFSBucket = GridFSBuckets.create(db, bucket);

        var file = getFileForId(gridFSBucket, fileId, filter);

        if (file == null) {
            return new OperationResult(SC_NOT_FOUND);
        }

        if (checkEtag) {
            var metadata = file.getMetadata();
            if (metadata != null) {
                var oldEtag = metadata.get("_etag");

                if (oldEtag != null) {
                    if (requestEtag == null) {
                        return new OperationResult(SC_CONFLICT, oldEtag);
                    } else if (!Objects.equals(oldEtag.toString(), requestEtag)) {
                        return new OperationResult(SC_PRECONDITION_FAILED, oldEtag);
                    }
                }
            }
        }

        try {
            gridFSBucket.delete(file.getId());
            LOGGER.debug("Succesfully deleted fileId {}", file.getId());
        } catch (MongoGridFSException e) {
            LOGGER.error("Can't delete fileId '{}'", file.getId(), e);
            return new OperationResult(SC_NOT_FOUND);
        }

        return new OperationResult(SC_NO_CONTENT);
    }

    private GridFSFile getFileForId(GridFSBucket gridFSBucket, BsonValue fileId, BsonDocument filter) {
        Bson cfilter;

        if (filter != null && !filter.isNull()) {
            cfilter = and(eq("_id", fileId), filter);
        } else {
            cfilter = eq("_id", fileId);
        }

        return gridFSBucket.find(cfilter).limit(1).iterator().tryNext();
    }

    /**
     *
     * @param rsOps the ReplicaSet connection options
     * @param dbName the database name
     * @param bucketName
     */
    public void deleteChunksCollection(final Optional<RSOps> rsOps, final String dbName, final String bucketName) {
        var chunksCollName = extractBucketName(bucketName).concat(".chunks");
        collections.collection(rsOps, dbName, chunksCollName).drop();
    }

    /**
     *
     * @param cs the client session
     * @param rsOps the ReplicaSet connection options
     * @param dbName the database name
     * @param method the request method
     * @param collName
     * @param documentId
     * @param filter
     * @param shardKeys
     * @param newContent
     * @param requestEtag
     * @param checkEtag
     * @return
     */
    public OperationResult updateFileMetadata(
        final Optional<ClientSession> cs,
        final Optional<RSOps> rsOps,
        final String dbName,
        final String collName,
        final METHOD method,
        final Optional<BsonValue> documentId,
        final Optional<BsonDocument> filter,
        final Optional<BsonDocument> shardKeys,
        final BsonDocument newContent,
        final String requestEtag,
        final boolean checkEtag) {
        var mcoll = collections.collection(rsOps, dbName, collName);

        // genereate new _etag
        var newEtag = new BsonObjectId();

        final BsonDocument content = DbUtils.validContent(newContent);

        // add new _etag
        content.get("metadata").asDocument().put("_etag", newEtag);

        var updateResult = DbUtils.writeDocument(
                cs,
                METHOD.PATCH, // need to always use PATCH to use $set update operator
                WRITE_MODE.UPDATE,
                mcoll,
                documentId,
                filter,
                shardKeys,
                DbUtils.getUpdateDocument(content, method == METHOD.PATCH)); // if PATCH, then flatten content to update only passed properties

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
                    var query = eq("_id", documentId.get());
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
                    var query = eq("_id", documentId.get());

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

    public static String extractBucketName(final String collectionName) {
        return collectionName.substring(0, collectionName.lastIndexOf('.'));
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
            DbUtils.restoreDocument(
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
            DbUtils.restoreDocument(
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