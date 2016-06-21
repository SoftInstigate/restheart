/*
 * RESTHeart - the data Repository API server
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
package org.restheart.db;

import com.mongodb.DuplicateKeyException;
import java.io.IOException;
import java.nio.file.Path;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface GridFsRepository {

    OperationResult createFile(
            Database db, 
            String dbName, 
            String bucketName, 
            BsonDocument metadata, 
            Path filePath) 
            throws IOException, DuplicateKeyException;

    OperationResult deleteFile(
            Database db, 
            String dbName, 
            String bucketName, 
            BsonObjectId fileId, 
            String requestEtag, 
            final boolean checkEtag);

    void deleteChunksCollection(
            Database db, 
            String dbName, 
            String bucketName);
}
