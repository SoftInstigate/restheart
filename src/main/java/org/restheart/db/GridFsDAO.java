package org.restheart.db;

/*
 * RESTHeart - the data REST API server
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class GridFsDAO implements GridFsRepository {
    private final MongoClient client;

    public GridFsDAO() {
        client = MongoDBClientSingleton.getInstance().getClient();
    }

    @Override
    public int createFile(Database db, String dbName, String bucketName, Object fileId, DBObject properties, File data) throws IOException, DuplicateKeyException {
        final String bucket = extractBucketName(bucketName);
        GridFS gridfs = new GridFS(db.getDB(dbName), bucket);
        GridFSInputFile gfsFile = gridfs.createFile(data);

        // remove from the properties the fields that are managed directly by the GridFs
        properties.removeField("_id");
        Object _fileName = properties.removeField("filename");
        properties.removeField("chunkSize");
        properties.removeField("uploadDate");
        properties.removeField("length");
        properties.removeField("md5");

        String fileName;
        if (_fileName != null && _fileName instanceof String) {
            fileName = (String) _fileName;
        } else {
            fileName = null;
        }

        // add etag
        properties.put("_etag", new ObjectId());

        gfsFile.setId(fileId);
        gfsFile.setFilename(fileName);

        properties.toMap().keySet().stream().forEach(k -> gfsFile.put((String) k, properties.get((String) k)));

        gfsFile.save();

        return HttpStatus.SC_CREATED;
    }

    @Override
    public int deleteFile(Database db, String dbName, String bucketName, Object fileId, ObjectId requestEtag) {
        GridFS gridfs = new GridFS(db.getDB(dbName), extractBucketName(bucketName));
        GridFSDBFile dbsfile = gridfs.findOne(new BasicDBObject("_id", fileId));

        if (dbsfile == null) {
            return HttpStatus.SC_NOT_FOUND;
        } else {
            int code = checkEtag(requestEtag, dbsfile);
            if (code == HttpStatus.SC_NO_CONTENT) {
                // delete file
                gridfs.remove(new BasicDBObject("_id", fileId));
            }

            return code;
        }
    }

    @Override
    public void deleteChunksCollection(Database db, String dbName, String bucketName) {
        String chunksCollName = extractBucketName(bucketName).concat(".chunks");
        client.getDB(dbName).getCollection(chunksCollName).drop();
    }

    /**
     *
     * @param requestEtag
     * @param dbsfile
     * @return HttpStatus.SC_NO_CONTENT if check is ok
     */
    private int checkEtag(ObjectId requestEtag, GridFSDBFile dbsfile) {
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
