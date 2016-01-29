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

import com.mongodb.DBObject;
import org.bson.types.ObjectId;

/**
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public interface Repository {
    
    OperationResult upsertDocument(String dbName, String collName, Object documentId, DBObject content, ObjectId requestEtag, boolean patching);
    
    OperationResult upsertDocumentPost(String dbName, String collName, Object documentId, DBObject content, ObjectId requestEtag);
    
    OperationResult deleteDocument(String dbName, String collName, Object documentId, ObjectId requestEtag);
    
    /**
     * returns the ETag of the document
     * @param dbName
     * @param collName
     * @param documentId
     * @return DBObject containing _etag property
     */
    DBObject getDocumentEtag(String dbName, String collName, Object documentId);
    
}
