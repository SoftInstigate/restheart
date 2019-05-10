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
package org.restheart.plugins.checkers;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.JsonUtils;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class CheckersUtils {

    public static boolean isBulkRequest(RequestContext context) {
        return context.getType() == RequestContext.TYPE.BULK_DOCUMENTS
                || context.getContent().isArray();
    }

    public static boolean doesRequestUsesUpdateOperators(BsonValue content) {
        if (content.isDocument()) {
            return JsonUtils.containsUpdateOperators(content.asDocument());
        } else if (content.isArray()) {
            BsonArray objs = content.asArray();

            return objs.stream().allMatch(obj -> {
                if (obj.isDocument()) {
                    return doesRequestUsesUpdateOperators(obj);
                } else {
                    return true;
                }
            });
        } else {
            return true;
        }
    }

    public static boolean doesRequestUsesDotNotation(BsonValue content) {
        if (content.isDocument()) {
            BsonDocument obj = content.asDocument();

            return obj.keySet().stream().anyMatch(key -> {
                return key.contains(".");
            });
        } else if (content.isArray()) {
            BsonArray objs = content.asArray();

            return objs.stream().anyMatch(obj -> {
                if (obj.isDocument()) {
                    return doesRequestUsesDotNotation(obj);
                } else {
                    return true;
                }
            });
        } else {
            return true;
        }
    }

    private CheckersUtils() {
    }
}
