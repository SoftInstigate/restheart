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

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.Document;
import org.restheart.db.sessions.ClientSessionImpl;

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
     * @param checkEtag
     * @return 
     */
    OperationResult upsertDocument(
            final ClientSessionImpl cs,
            final String dbName, 
            final String collName, 
            final Object documentId, 
            final BsonDocument filter, 
            final BsonDocument shardedKeys, 
            final BsonDocument content, 
            final String requestEtag, 
            boolean patching, 
            final boolean checkEtag);

    /**
     * 
     * @param cs the client session
     * @param dbName
     * @param collName
     * @param filter
     * @param shardedKeys
     * @param content
     * @param requestEtag
     * @param checkEtag
     * @return 
     */
    OperationResult upsertDocumentPost(
            final ClientSessionImpl cs,
            final String dbName, 
            final String collName, 
            final BsonDocument filter,
            final BsonDocument shardedKeys, 
            final BsonDocument content, 
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
     * @return 
     */
    OperationResult deleteDocument(
            final ClientSessionImpl cs,
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
     * @return 
     */
    BulkOperationResult bulkUpsertDocumentsPost(
            final ClientSessionImpl cs,
            final String dbName, 
            final String collName,
            final BsonArray documents,
            final BsonDocument filter, 
            final BsonDocument shardKeys);

    /**
     * 
     * @param cs the client session
     * @param dbName
     * @param collName
     * @param filter
     * @param shardKeys
     * @param data
     * @return 
     */
    BulkOperationResult bulkPatchDocuments(
            final ClientSessionImpl cs,
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
     * @return 
     */
    BulkOperationResult bulkDeleteDocuments(
            final ClientSessionImpl cs,
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
    Document getDocumentEtag(
            final ClientSessionImpl cs,
            final String dbName, 
            final String collName, 
            final Object documentId);
}
