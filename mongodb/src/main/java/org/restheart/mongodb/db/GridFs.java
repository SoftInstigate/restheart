/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2022 SoftInstigate
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
import com.google.common.annotations.VisibleForTesting;
import com.mongodb.DuplicateKeyException;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.MongoGridFSException;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
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
import org.restheart.utils.HttpStatus;

import static org.restheart.utils.HttpStatus.*;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class GridFs {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(GridFs.class);

    private static final String FILENAME = "filename";

    private final Collections collections;

    private GridFs() {
        this.collections = Collections.get();
    }

    @VisibleForTesting
    GridFs(Collections collections) {
        this.collections = collections;
    }

    private static GridFs INSTANCE = null;

    public static GridFs get() {
        if (INSTANCE == null) {
            INSTANCE = new GridFs();
        }
        return INSTANCE;
    }

    /**
     *
     * @param db the MongoDatabase
     * @param bucketName
     * @param metadata
     * @param filePath
     * @return the OperationResult
     * @throws IOException
     * @throws DuplicateKeyException
     */
    public OperationResult createFile(
        final MongoDatabase db,
        final String bucketName,
        final BsonDocument metadata,
        final Path filePath)
        throws IOException, DuplicateKeyException {

        final var bucket = extractBucketName(bucketName);

        var gridFSBucket = GridFSBuckets.create(db, bucket);

        var filename = extractFilenameFromProperties(metadata);

        //add etag to metadata
        ObjectId etag = new ObjectId();
        metadata.put("_etag", new BsonObjectId(etag));

        try (InputStream sourceStream = new FileInputStream(filePath.toFile())) {

            if (metadata.get("_id") == null) {
                var options = new GridFSUploadOptions().metadata(Document.parse(metadata.toJson()));

                var _id = gridFSBucket.uploadFromStream(filename, sourceStream, options);

                return new OperationResult(SC_CREATED, new BsonObjectId(etag), new BsonObjectId(_id));
            } else {
                var _id = metadata.remove("_id");

                var options = new GridFSUploadOptions().metadata(Document.parse(metadata.toJson()));

                gridFSBucket.uploadFromStream(_id, filename, sourceStream, options);

                return new OperationResult(SC_CREATED, new BsonObjectId(etag), _id);
            }
        }
    }

    /**
     *
     * @param db the MongoDatabase
     * @param bucketName
     * @param metadata
     * @param filePath
     * @param fileId
     * @param filter
     * @param requestEtag
     * @param checkEtag
     * @return
     * @throws IOException
     */
    public OperationResult upsertFile(
        final MongoDatabase db,
        final String bucketName,
        final BsonDocument metadata,
        final Path filePath,
        final BsonValue fileId,
        final BsonDocument filter,
        final String requestEtag,
        final boolean checkEtag) throws IOException {

        var deletionResult = deleteFile(db, bucketName, fileId, filter, requestEtag, checkEtag);

        //https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.7
        final boolean deleteOperationWasSuccessful = deletionResult.getHttpCode() == SC_NO_CONTENT || deletionResult.getHttpCode() == SC_OK;
        final boolean fileDidntExist = deletionResult.getHttpCode() == SC_NOT_FOUND;
        final boolean fileExisted = !fileDidntExist;

        if (deleteOperationWasSuccessful || fileDidntExist) {
            var creationResult = createFile(db, bucketName, metadata, filePath);

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
     * @param db the MongoDatabase
     * @param bucketName
     * @param fileId
     * @param filter
     * @param requestEtag
     * @param checkEtag
     * @return the OperationResult
     */
    public OperationResult deleteFile(
        final MongoDatabase db,
        final String bucketName,
        final BsonValue fileId,
        final BsonDocument filter,
        final String requestEtag,
        final boolean checkEtag) {

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
     * @param db the MongoDatabase
     * @param bucketName
     */
    public void deleteChunksCollection(final MongoDatabase db, final String bucketName) {
        var chunksCollName = extractBucketName(bucketName).concat(".chunks");
        collections.getCollection(db, chunksCollName).drop();
    }

    /**
     *
     * @param cs the client session
     * @param method the request method
     * @param db the MongoDatabase
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
            final METHOD method,
            final MongoDatabase db,
            final String collName,
            final Optional<BsonValue> documentId,
            final Optional<BsonDocument> filter,
            final Optional<BsonDocument> shardKeys,
            final BsonDocument newContent,
            final String requestEtag,
            final boolean checkEtag) {
        var mcoll = collections.getCollection(db, collName);

        // genereate new etag
        var newEtag = new BsonObjectId();

        final BsonDocument content = DbUtils.validContent(newContent);
        content.get("metadata", new BsonDocument()).asDocument().put("_etag", newEtag);

        var updateResult = DbUtils.updateFileMetadata(
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

    public String extractBucketName(final String collectionName) {
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