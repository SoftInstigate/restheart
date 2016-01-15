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
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.DuplicateKeyException;
import com.mongodb.MongoClient;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSDBFile;
import com.mongodb.gridfs.GridFSInputFile;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import org.bson.types.ObjectId;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class GridFsDAO implements GridFsRepository {

    private static final String FILENAME = "filename";
    private static final String _ID = "_id";

    private final MongoClient client;

    public GridFsDAO() {
        client = MongoDBClientSingleton.getInstance().getClient();
    }

    @Override
    public OperationResult createFile(
            final Database db,
            final String dbName,
            final String bucketName,
            final Object fileId,
            final DBObject properties,
            final File data) throws IOException, DuplicateKeyException {

        final String bucket = extractBucketName(bucketName);
        GridFS gridfs = new GridFS(db.getDB(dbName), bucket);
        GridFSInputFile gfsFile = gridfs.createFile(data);

        String filename = extractFilenameFromProperties(properties);
        gfsFile.setFilename(filename);

        // add etag
        ObjectId etag = new ObjectId();
        properties.put("_etag", etag);

        gfsFile.setId(fileId);

        properties.toMap().keySet().stream().forEach(k -> gfsFile.put((String) k, properties.get((String) k)));

        gfsFile.save();

        return new OperationResult(HttpStatus.SC_CREATED, etag);
    }

    private String extractFilenameFromProperties(final DBObject properties) {
        String filename = null;
        if (properties != null && properties.containsField(FILENAME)) {
            filename = (String) properties.get(FILENAME);
        }

        return filename;
    }

    @Override
    public OperationResult deleteFile(
            final Database db,
            final String dbName,
            final String bucketName,
            final Object fileId,
            final String requestEtag,
            final boolean checkEtag) {

        GridFS gridfs = new GridFS(db.getDB(dbName), extractBucketName(bucketName));
        GridFSDBFile dbsfile = gridfs.findOne(new BasicDBObject(_ID, fileId));

        if (dbsfile == null) {
            return new OperationResult(HttpStatus.SC_NOT_FOUND);
        } else if (checkEtag) {
            Object oldEtag = dbsfile.get("_etag");

            if (oldEtag != null) {
                if (requestEtag == null) {
                    return new OperationResult(HttpStatus.SC_CONFLICT, oldEtag);
                } else if (!Objects.equals(oldEtag.toString(), requestEtag)) {
                    return new OperationResult(HttpStatus.SC_PRECONDITION_FAILED, oldEtag);
                }
            }
        }

        gridfs.remove(new BasicDBObject(_ID, fileId));
        return new OperationResult(HttpStatus.SC_NO_CONTENT);
    }

    @Override
    public void deleteChunksCollection(final Database db,
            final String dbName,
            final String bucketName
    ) {
        String chunksCollName = extractBucketName(bucketName).concat(".chunks");
        client.getDB(dbName).getCollection(chunksCollName).drop();
    }

    private static String extractBucketName(final String collectionName) {
        return collectionName.split("\\.")[0];
    }
}
