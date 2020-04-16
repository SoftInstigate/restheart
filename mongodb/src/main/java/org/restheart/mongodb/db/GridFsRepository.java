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

import com.mongodb.DuplicateKeyException;
import java.io.IOException;
import java.nio.file.Path;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.exchange.OperationResult;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface GridFsRepository {

    /**
     *
     * @param db
     * @param dbName
     * @param bucketName
     * @param metadata
     * @param filePath
     * @return
     * @throws IOException
     * @throws DuplicateKeyException
     */
    OperationResult createFile(
            Database db,
            String dbName,
            String bucketName,
            BsonDocument metadata,
            Path filePath)
            throws IOException, DuplicateKeyException;

    /**
     *
     * @param db
     * @param dbName
     * @param bucketName
     * @param metadata
     * @param filePath
     * @param fileId
     * @param requestEtag
     * @param checkEtag
     * @return
     * @throws IOException
     */
    OperationResult upsertFile(
            final Database db,
            final String dbName,
            final String bucketName,
            final BsonDocument metadata,
            final Path filePath,
            final BsonValue fileId,
            final String requestEtag,
            final boolean checkEtag)
            throws IOException;

    /**
     *
     * @param db
     * @param dbName
     * @param bucketName
     * @param fileId
     * @param requestEtag
     * @param checkEtag
     * @return
     */
    OperationResult deleteFile(
            Database db, 
            String dbName, 
            String bucketName, 
            BsonValue fileId, 
            String requestEtag, 
            final boolean checkEtag);

    /**
     *
     * @param db
     * @param dbName
     * @param bucketName
     */
    void deleteChunksCollection(
            Database db, 
            String dbName, 
            String bucketName);
}
