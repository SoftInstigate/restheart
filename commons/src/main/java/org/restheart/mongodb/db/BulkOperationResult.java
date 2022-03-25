/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2022 SoftInstigate
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

import com.mongodb.bulk.BulkWriteResult;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class BulkOperationResult extends OperationResult {
    private final BulkWriteResult bulkResult;

    /**
     *
     * @param httpCode
     * @param etag
     * @param bulkResult
     */
    public BulkOperationResult(int httpCode, Object etag,
            BulkWriteResult bulkResult) {
        super(httpCode, etag);

        this.bulkResult = bulkResult;
    }

    /**
     * @return the writeResult
     */
    public BulkWriteResult getBulkResult() {
        return bulkResult;
    }
}
