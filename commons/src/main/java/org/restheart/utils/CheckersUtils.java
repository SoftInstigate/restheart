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

package org.restheart.utils;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.exchange.MongoRequest;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class CheckersUtils {
    /**
     *
     * @param request
     * @return if the request is a bulk request
     */
    public static boolean isBulkRequest(MongoRequest request) {
        return request.isBulkDocuments() || request.getContent().isArray();
    }

    /**
     *
     * @param content
     * @return true if the request content includes update operators
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
     *
     * @param content
     * @return true if the request content includes properties identified with
     * the dot notation
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
