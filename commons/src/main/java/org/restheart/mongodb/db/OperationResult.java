/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2022 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.mongodb.db;

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
    private final Throwable cause;

    /**
     *
     * @param httpCode
     */
    public OperationResult(int httpCode) {
        this.httpCode = httpCode;
        this.etag = null;
        this.newData = null;
        this.oldData = null;
        this.newId = null;
        this.cause = null;
    }

    /**
     *
     * @param httpCode
     * @param oldData
     * @param newData
     */
    public OperationResult(int httpCode, BsonDocument oldData, BsonDocument newData) {
        this.httpCode = httpCode;
        this.etag = null;
        this.newData = newData;
        this.oldData = oldData;
        this.newId = newData == null ? null : newData.get("_id");
        this.cause = null;
    }

    /**
     *
     * @param httpCode
     * @param etag
     */
    public OperationResult(int httpCode, Object etag) {
        this.httpCode = httpCode;
        this.etag = etag;
        this.newData = null;
        this.oldData = null;
        this.newId = null;
        this.cause = null;
    }

    /**
     *
     * @param httpCode
     * @param etag
     * @param newId
     */
    public OperationResult(int httpCode, Object etag, BsonValue newId) {
        this.httpCode = httpCode;
        this.etag = etag;
        this.newId = newId;
        this.newData = null;
        this.oldData = null;
        this.cause = null;
    }

    /**
     *
     * @param httpCode
     * @param etag
     * @param oldData
     * @param newData
     */
    public OperationResult(int httpCode, Object etag, BsonDocument oldData, BsonDocument newData) {
        this.httpCode = httpCode;
        this.etag = etag;
        this.newData = newData;
        this.oldData = oldData;
        this.newId = newData == null ? null : newData.get("_id");
        this.cause = null;
    }

    /**
     *
     * @param httpCode
     * @param etag
     * @param oldData
     * @param newData
     */
    public OperationResult(int httpCode, Object etag, BsonDocument oldData, BsonDocument newData, Throwable cause) {
        this.httpCode = httpCode;
        this.etag = etag;
        this.newData = newData;
        this.oldData = oldData;
        this.newId = newData == null ? null : newData.get("_id");
        this.cause = cause;
    }

    /**
     *
     * @param httpCode
     * @param etag
     * @param oldData
     * @param newData
     */
    public OperationResult(int httpCode, BsonDocument oldData, Throwable cause) {
        this.httpCode = httpCode;
        this.etag = null;
        this.newData = null;
        this.oldData = oldData;
        this.newId = null;
        this.cause = cause;
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

    /**
     * @return the cause
     */
    public Throwable getCause() {
        return cause;
    }
}
