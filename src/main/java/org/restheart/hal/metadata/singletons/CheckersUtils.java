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
package org.restheart.hal.metadata.singletons;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.restheart.handlers.RequestContext;
import static org.restheart.utils.RequestHelper.UPDATE_OPERATORS;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class CheckersUtils {

    private CheckersUtils() {
    }

    public static boolean isBulkRequest(RequestContext context) {
        return context.getType() == RequestContext.TYPE.BULK_DOCUMENTS
                || context.getContent() instanceof BasicDBList;
    }

    public static boolean doesRequestUsesUpdateOperators(DBObject content) {
        if (content instanceof BasicDBObject) {
            BasicDBObject obj = (BasicDBObject) content;
            
            return obj.keySet().stream().anyMatch(key -> {
                return UPDATE_OPERATORS.contains(key);
            });
        } else if (content instanceof BasicDBList) {
            BasicDBList objs = (BasicDBList) content;
            
            return objs.stream().allMatch(obj -> {
                if (obj instanceof BasicDBObject) {
                    return doesRequestUsesUpdateOperators((BasicDBObject)obj);
                } else {
                    return true;
                }
            });
        } else {
            return true;
        }
    }

    public static boolean doesRequestUsesDotNotation(DBObject content) {
        if (content instanceof BasicDBObject) {
            BasicDBObject obj = (BasicDBObject) content;
            
            return obj.keySet().stream().anyMatch(key -> {
                return key.contains(".");
            });
        } else if (content instanceof BasicDBList) {
            BasicDBList objs = (BasicDBList) content;
            
            return objs.stream().anyMatch(obj -> {
                if (obj instanceof BasicDBObject) {
                    return doesRequestUsesDotNotation((BasicDBObject)obj);
                } else {
                    return true;
                }
            });
        } else {
            return true;
        }
    }
}
