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
package org.restheart.db.entity;

import com.mongodb.DBObject;
import org.bson.types.ObjectId;

/**
 *
 * @author Maurizio Turatti <info@maurizioturatti.com>
 */
public final class PutDocumentEntity implements Entity {
    
    public final String dbName;
    public final String collName;
    public final String documentId;
    public final DBObject content;
    public final ObjectId requestEtag;
    public final boolean patching;
    
    public PutDocumentEntity(String dbName, String collName, String documentId, DBObject content, ObjectId requestEtag, boolean patching) {
        assert dbName != null && collName != null && documentId != null && content != null && requestEtag != null;
        this.collName = collName;
        this.content = content;
        this.dbName = dbName;
        this.documentId = documentId;
        this.patching = patching;
        this.requestEtag = requestEtag;
    }
    
}
