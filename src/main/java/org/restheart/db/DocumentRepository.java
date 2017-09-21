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

/**
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public interface DocumentRepository {

    OperationResult upsertDocument(final String dbName, final String collName, final Object documentId, final BsonDocument filter, final BsonDocument shardedKeys, final BsonDocument content, final String requestEtag, boolean patching, final boolean checkEtag);

    OperationResult upsertDocumentPost(final String dbName, final String collName, final BsonDocument filter, final BsonDocument shardedKeys, final BsonDocument content, final String requestEtag, final boolean checkEtag);

    OperationResult deleteDocument(final String dbName, String collName, Object documentId, final BsonDocument filter, final BsonDocument shardedKeys, final String requestEtag, final boolean checkEtag);

    BulkOperationResult bulkUpsertDocumentsPost(final String dbName, final String collName, final BsonArray documents, final BsonDocument filter, final BsonDocument shardKeys);

    BulkOperationResult bulkPatchDocuments(final String dbName, final String collName, final BsonDocument filter, final BsonDocument shardKeys, final BsonDocument data);

    BulkOperationResult bulkDeleteDocuments(final String dbName, final String collName, final BsonDocument filter, final BsonDocument shardKeys);

    /**
     * returns the ETag of the document
     * @param dbName
     * @param collName
     * @param documentId
     * @return Document containing _etag property
     */
    Document getDocumentEtag(String dbName, String collName, Object documentId);
}
