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
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import static com.mongodb.client.model.Filters.eq;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class GridFsDAO implements GridFsRepository {

    private static final String FILENAME = "filename";

    private final MongoClient client;

    public GridFsDAO() {
        client = MongoDBClientSingleton.getInstance().getClient();
    }

    @Override
    @SuppressWarnings("unchecked")
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

        GridFSUploadOptions options = new GridFSUploadOptions()
                .metadata(Document.parse(metadata.toJson()));

        InputStream sourceStream = new FileInputStream(filePath.toFile());
        
        ObjectId _id = gridFSBucket.uploadFromStream(
                filename, 
                sourceStream, 
                options);

        return new OperationResult(HttpStatus.SC_CREATED,
                new BsonObjectId(etag),
                new BsonObjectId(_id));
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

    @Override
    public OperationResult deleteFile(
            final Database db,
            final String dbName,
            final String bucketName,
            final BsonObjectId fileId,
            final String requestEtag,
            final boolean checkEtag) {
        final String bucket = extractBucketName(bucketName);
        
        GridFSBucket gridFSBucket = GridFSBuckets.create(
                db.getDatabase(dbName),
                bucket);
        
        GridFSFile file = gridFSBucket
                .find(eq("_id", fileId))
                .limit(1).iterator().tryNext();
        
        if (file == null) {
            return new OperationResult(HttpStatus.SC_NOT_FOUND);
        } else if (checkEtag) {
            Object oldEtag = file.getMetadata().get("_etag");

            if (oldEtag != null) {
                if (requestEtag == null) {
                    return new OperationResult(HttpStatus.SC_CONFLICT, oldEtag);
                } else if (!Objects.equals(oldEtag.toString(), requestEtag)) {
                    return new OperationResult(
                            HttpStatus.SC_PRECONDITION_FAILED, oldEtag);
                }
            }
        }

        gridFSBucket.delete(fileId.asObjectId().getValue());
        
        return new OperationResult(HttpStatus.SC_NO_CONTENT);
    }

    @Override
    public void deleteChunksCollection(final Database db,
            final String dbName,
            final String bucketName
    ) {
        String chunksCollName = extractBucketName(bucketName).concat(".chunks");
        client.getDatabase(dbName).getCollection(chunksCollName).drop();
    }

    private static String extractBucketName(final String collectionName) {
        return collectionName.split("\\.")[0];
    }
}
