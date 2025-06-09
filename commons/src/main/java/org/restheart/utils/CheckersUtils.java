/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2025 SoftInstigate
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

package org.restheart.utils;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.exchange.MongoRequest;

/**
 * Utility class providing validation and checking methods for MongoDB requests.
 * This class contains methods to analyze request content and determine characteristics
 * such as whether a request is a bulk operation, uses update operators, or employs
 * dot notation in field names.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class CheckersUtils {
    /**
     * Determines if the given MongoDB request is a bulk request.
     * A request is considered bulk if it either explicitly declares bulk documents
     * or if its content is an array of documents.
     *
     * @param request the MongoDB request to check
     * @return true if the request is a bulk request, false otherwise
     */
    public static boolean isBulkRequest(MongoRequest request) {
        return request.isBulkDocuments() || request.getContent().isArray();
    }

    /**
     * Determines if the request content includes MongoDB update operators.
     * This method checks both single documents and arrays of documents for
     * the presence of update operators like $set, $unset, $inc, etc.
     *
     * @param content the BSON content to analyze (document or array)
     * @return true if the content includes update operators, false otherwise
     */
    public static boolean doesRequestUseUpdateOperators(BsonValue content) {
        if (content.isDocument()) {
            return BsonUtils.containsUpdateOperators(content.asDocument());
        } else if (content.isArray()) {
            BsonArray objs = content.asArray();

            return objs.stream().allMatch(obj -> {
                if (obj.isDocument()) {
                    return doesRequestUseUpdateOperators(obj);
                } else {
                    return true;
                }
            });
        } else {
            return true;
        }
    }

    /**
     * Determines if the request content includes properties identified with dot notation.
     * Dot notation is used in MongoDB to reference nested fields (e.g., "address.street").
     * This method checks both single documents and arrays of documents for field names
     * containing dots.
     *
     * @param content the BSON content to analyze (document or array)
     * @return true if the content includes properties with dot notation, false otherwise
     */
    public static boolean doesRequestUseDotNotation(BsonValue content) {
        if (content.isDocument()) {
            BsonDocument obj = content.asDocument();

            return obj.keySet().stream().anyMatch(key -> {
                return key.contains(".");
            });
        } else if (content.isArray()) {
            BsonArray objs = content.asArray();

            return objs.stream().anyMatch(obj -> {
                if (obj.isDocument()) {
                    return doesRequestUseDotNotation(obj);
                } else {
                    return true;
                }
            });
        } else {
            return true;
        }
    }
}
