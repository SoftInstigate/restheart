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

import com.mongodb.client.ClientSession;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.restheart.exchange.OperationResult;
import org.restheart.exchange.ExchangeKeys.WRITE_MODE;

/**
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public interface DocumentRepository {
    /**
     *
     * @param cs the client session
     * @param dbName
     * @param collName
     * @param documentId
     * @param filter
     * @param shardedKeys
     * @param content
     * @param requestEtag
     * @param patching
     * @param writeMode
     * @param checkEtag
     * @return the OperationResult
     */
    OperationResult writeDocument(
            final ClientSession cs,
            final String dbName,
            final String collName,
            final Object documentId,
            final BsonDocument filter,
            final BsonDocument shardedKeys,
            final BsonDocument content,
            final String requestEtag,
            final boolean patching,
            final WRITE_MODE writeMode,
            final boolean checkEtag);

    /**
     *
     * @param cs the client session
     * @param dbName
     * @param collName
     * @param filter
     * @param shardedKeys
     * @param content
     * @param writeMode
     * @param requestEtag
     * @param checkEtag
     * @return the OperationResult
     */
    OperationResult writeDocumentPost(
            final ClientSession cs,
            final String dbName,
            final String collName,
            final BsonDocument filter,
            final BsonDocument shardedKeys,
            final BsonDocument content,
            final WRITE_MODE writeMode,
            final String requestEtag,
            final boolean checkEtag);

    /**
     *
     * @param cs the client session
     * @param dbName
     * @param collName
     * @param documentId
     * @param filter
     * @param shardedKeys
     * @param requestEtag
     * @param checkEtag
     * @return the OperationResult
     */
    OperationResult deleteDocument(
            final ClientSession cs,
            final String dbName,
            final String collName,
            final Object documentId,
            final BsonDocument filter,
            final BsonDocument shardedKeys,
            final String requestEtag,
            final boolean checkEtag);
    /**
     *
     * @param cs the client session
     * @param dbName
     * @param collName
     * @param documents
     * @param filter
     * @param shardKeys
     * @param writeMode
     * @return the BulkOperationResult
     */
    BulkOperationResult bulkPostDocuments(
            final ClientSession cs,
            final String dbName,
            final String collName,
            final BsonArray documents,
            final BsonDocument filter,
            final BsonDocument shardKeys,
            final WRITE_MODE writeMode);

    /**
     *
     * @param cs the client session
     * @param dbName
     * @param collName
     * @param filter
     * @param shardKeys
     * @param data
     * @return the BulkOperationResult
     */
    BulkOperationResult bulkPatchDocuments(
            final ClientSession cs,
            final String dbName,
            final String collName,
            final BsonDocument filter,
            final BsonDocument shardKeys,
            final BsonDocument data);

    /**
     *
     * @param cs the client session
     * @param dbName
     * @param collName
     * @param filter
     * @param shardKeys
     * @return the BulkOperationResult
     */
    BulkOperationResult bulkDeleteDocuments(
            final ClientSession cs,
            final String dbName,
            final String collName,
            final BsonDocument filter,
            final BsonDocument shardKeys);

    /**
     * returns the ETag of the document
     *
     * @param cs the client session
     * @param dbName
     * @param collName
     * @param documentId
     * @return Document containing _etag property
     */
    BsonDocument getDocumentEtag(
            final ClientSession cs,
            final String dbName,
            final String collName,
            final Object documentId);
}
