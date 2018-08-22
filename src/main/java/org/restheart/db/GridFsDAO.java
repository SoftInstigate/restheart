package org.restheart.db;

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
import com.mongodb.DuplicateKeyException;
import com.mongodb.MongoClient;
import com.mongodb.MongoGridFSException;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import static com.mongodb.client.model.Filters.eq;
import static org.restheart.utils.HttpStatus.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.restheart.utils.HttpStatus;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class GridFsDAO implements GridFsRepository {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(GridFsDAO.class);

    private static final String FILENAME = "filename";

    private static String extractBucketName(final String collectionName) {
        return collectionName.split("\\.")[0];
    }

    private final MongoClient client;

    private final Lock deleteLock = new ReentrantLock();

    public GridFsDAO() {
        client = MongoDBClientSingleton.getInstance().getClient();
    }

    /**
     *
     * @param db
     * @param dbName
     * @param bucketName
     * @param metadata
     * @param filePath
     * @return the OperationResult
     * @throws IOException
     * @throws DuplicateKeyException
     */
    @Override
    public OperationResult createFile(
            final Database db,
            final String dbName,
            final String bucketName,
            final BsonDocument metadata,
            final Path filePath)
            throws IOException, DuplicateKeyException {

        final String bucket = extractBucketName(bucketName);

        GridFSBucket gridFSBucket = GridFSBuckets.create(
                db.getDatabase(dbName),
                bucket);

        String filename = extractFilenameFromProperties(metadata);

        //add etag to metadata
        ObjectId etag = new ObjectId();
        metadata.put("_etag", new BsonObjectId(etag));

        try (InputStream sourceStream = new FileInputStream(filePath.toFile())) {

            if (metadata.get("_id") == null) {
                GridFSUploadOptions options = new GridFSUploadOptions()
                        .metadata(Document.parse(metadata.toJson()));

                ObjectId _id = gridFSBucket.uploadFromStream(
                        filename,
                        sourceStream,
                        options);

                return new OperationResult(SC_CREATED,
                        new BsonObjectId(etag),
                        new BsonObjectId(_id));
            } else {
                BsonValue _id = metadata.remove("_id");

                GridFSUploadOptions options = new GridFSUploadOptions()
                        .metadata(Document.parse(metadata.toJson()));

                gridFSBucket.uploadFromStream(
                        _id,
                        filename,
                        sourceStream,
                        options);

                return new OperationResult(SC_CREATED,
                        new BsonObjectId(etag),
                        _id);
            }
        }
    }

    @Override
    public OperationResult upsertFile(final Database db,
                                      final String dbName,
                                      final String bucketName,
                                      final BsonDocument metadata,
                                      final Path filePath,
                                      final BsonValue fileId,
                                      final String requestEtag,
                                      final boolean checkEtag) throws IOException {

        OperationResult deletionResult = deleteFile(db, dbName, bucketName, fileId, requestEtag, checkEtag);

        //https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.7
        final boolean deleteOperationWasSuccessful = deletionResult.getHttpCode() == SC_NO_CONTENT || deletionResult.getHttpCode() == SC_OK;
        final boolean fileDidntExist = deletionResult.getHttpCode() == SC_NOT_FOUND;
        final boolean fileExisted = !fileDidntExist;

        if(deleteOperationWasSuccessful || fileDidntExist) {
            OperationResult creationResult = createFile(db, dbName, bucketName, metadata, filePath);

            //https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.5
            final boolean creationOperationWasSuccessful = SC_CREATED == creationResult.getHttpCode() || SC_OK == creationResult.getHttpCode();
            if(creationOperationWasSuccessful) {


                //https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.6
                if(fileExisted) {
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

    private String extractFilenameFromProperties(
            final BsonDocument properties) {
        String filename = null;

        if (properties != null && properties.containsKey(FILENAME)) {
            BsonValue _filename = properties.get(FILENAME);

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
     * @param db
     * @param dbName
     * @param bucketName
     * @param fileId
     * @param requestEtag
     * @param checkEtag
     * @return the OperationResult
     */
    @Override
    public OperationResult deleteFile(
            final Database db,
            final String dbName,
            final String bucketName,
            final BsonValue fileId,
            final String requestEtag,
            final boolean checkEtag) {

        final String bucket = extractBucketName(bucketName);

        GridFSBucket gridFSBucket = GridFSBuckets.create(
                db.getDatabase(dbName),
                bucket);

        // try to avoid concurrent deletions of the same file as much as possible
        // Note: this won't help much if RESTHeart is clustered, as the lock is local
        deleteLock.lock();
        try {
            GridFSFile file = getFileForId(gridFSBucket, fileId);

            if (file == null) {
                return new OperationResult(SC_NOT_FOUND);
            }

            if (checkEtag) {
                Document metadata = file.getMetadata();
                if (metadata != null) {
                    Object oldEtag = metadata.get("_etag");

                    if (oldEtag != null) {
                        if (requestEtag == null) {
                            return new OperationResult(HttpStatus.SC_CONFLICT, oldEtag);
                        } else if (!Objects.equals(oldEtag.toString(), requestEtag)) {
                            return new OperationResult(
                                    HttpStatus.SC_PRECONDITION_FAILED, oldEtag);
                        }
                    }
                }
            }

            try {
                gridFSBucket.delete(fileId);
                LOGGER.info("Succesfully deleted fileId {}", fileId);
            } catch (MongoGridFSException e) {
                LOGGER.error("Can't delete fileId '{}'", fileId, e);
                return new OperationResult(SC_NOT_FOUND);
            }

            return new OperationResult(SC_NO_CONTENT);

        } finally {
            deleteLock.unlock();
        }
    }

    private GridFSFile getFileForId(GridFSBucket gridFSBucket, BsonValue fileId) {
        return gridFSBucket
            .find(eq("_id", fileId))
            .limit(1).iterator().tryNext();
    }

    /**
     *
     * @param db
     * @param dbName
     * @param bucketName
     */
    @Override
    public void deleteChunksCollection(final Database db,
            final String dbName,
            final String bucketName
    ) {
        String chunksCollName = extractBucketName(bucketName).concat(".chunks");
        client.getDatabase(dbName).getCollection(chunksCollName).drop();
    }

}
