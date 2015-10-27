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
    public int createFile(
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
        properties.put("_etag", new ObjectId());

        gfsFile.setId(fileId);

        properties.toMap().keySet().stream().forEach(k -> gfsFile.put((String) k, properties.get((String) k)));

        gfsFile.save();

        return HttpStatus.SC_CREATED;
    }

    private String extractFilenameFromProperties(final DBObject properties) {
        String filename = null;
        if (properties != null && properties.containsField(FILENAME)) {
            filename = (String) properties.get(FILENAME);
        }

        return filename;
    }

    @Override
    public int deleteFile(
            final Database db,
            final String dbName,
            final String bucketName,
            final Object fileId,
            final ObjectId requestEtag) {

        GridFS gridfs = new GridFS(db.getDB(dbName), extractBucketName(bucketName));
        GridFSDBFile dbsfile = gridfs.findOne(new BasicDBObject(_ID, fileId));

        if (dbsfile == null) {
            return HttpStatus.SC_NOT_FOUND;
        } else {
            int code = checkEtag(requestEtag, dbsfile);
            if (code == HttpStatus.SC_NO_CONTENT) {
                // delete file
                gridfs.remove(new BasicDBObject(_ID, fileId));
            }

            return code;
        }
    }

    @Override
    public void deleteChunksCollection(final Database db, final String dbName, final String bucketName) {
        String chunksCollName = extractBucketName(bucketName).concat(".chunks");
        client.getDB(dbName).getCollection(chunksCollName).drop();
    }

    /**
     *
     * @param requestEtag
     * @param dbsfile
     * @return HttpStatus.SC_NO_CONTENT if check is ok
     */
    private int checkEtag(final ObjectId requestEtag, final GridFSDBFile dbsfile) {
        if (dbsfile != null) {
            Object etag = dbsfile.get("_etag");

            if (etag == null) {
                return HttpStatus.SC_NO_CONTENT;
            }

            if (requestEtag == null) {
                return HttpStatus.SC_CONFLICT;
            }

            if (etag.equals(requestEtag)) {
                return HttpStatus.SC_NO_CONTENT;
            } else {
                return HttpStatus.SC_PRECONDITION_FAILED;
            }
        }

        return HttpStatus.SC_NO_CONTENT;
    }

    private static String extractBucketName(final String collectionName) {
        return collectionName.split("\\.")[0];
    }
}
