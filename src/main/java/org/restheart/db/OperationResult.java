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
package org.restheart.db;

import org.bson.BsonDocument;
import org.bson.BsonValue;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class OperationResult {
    private final int httpCode;
    private final Object etag;
    private final BsonDocument newData;
    private final BsonDocument oldData;
    private final BsonValue newId;
    
    public OperationResult(int httpCode) {
        this.httpCode = httpCode;
        this.etag = null;
        this.newData = null;
        this.oldData = null;
        this.newId = null;
    }
    
    public OperationResult(int httpCode, BsonDocument oldData, BsonDocument newData) {
        this.httpCode = httpCode;
        this.etag = null;
        this.newData = newData;
        this.oldData = oldData;
        this.newId = newData == null ? null : newData.get("_id");
    }
    
    public OperationResult(int httpCode, Object etag) {
        this.httpCode = httpCode;
        this.etag = etag;
        this.newData = null;
        this.oldData = null;
        this.newId = null;
    }
    
    public OperationResult(int httpCode, Object etag, BsonValue newId) {
        this.httpCode = httpCode;
        this.etag = etag;
        this.newId = newId;
        this.newData = null;
        this.oldData = null;
    }
    
    public OperationResult(int httpCode, Object etag, BsonDocument oldData, BsonDocument newData) {
        this.httpCode = httpCode;
        this.etag = etag;
        this.newData = newData;
        this.oldData = oldData;
        this.newId = newData == null ? null : newData.get("_id");
    }

    /**
     * @return the httpCode
     */
    public int getHttpCode() {
        return httpCode;
    }

    /**
     * @return the etag
     */
    public Object getEtag() {
        return etag;
    }

    /**
     * @return the newData
     */
    public BsonDocument getNewData() {
        return newData;
    }
    
    /**
     * @return the oldData
     */
    public BsonDocument getOldData() {
        return oldData;
    }

    /**
     * @return the newId
     */
    public BsonValue getNewId() {
        return newId;
    }
}