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
import java.util.Arrays;
import java.util.List;
import org.restheart.handlers.RequestContext;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class CheckersUtils {
    private static final String _UPDATE_OPERATORS[] = {
        "$inc", "$mul", "$rename", "$setOnInsert", "$set", "$unset", // Field Update Operators
        "$min", "$max", "$currentDate",
        "$", "$addToSet", "$pop", "$pullAll", "$pull", "$pushAll", "$push", // Array Update Operators
        "$bit", // Bitwise Update Operator
        "$isolated" // Isolation Update Operator
    };

    private static final List<String> UPDATE_OPERATORS
            = Arrays.asList(_UPDATE_OPERATORS);


    public static boolean isBulkRequest(RequestContext context) {
        return context.getType() == RequestContext.TYPE.BULK_DOCUMENTS
                || context.getContent() instanceof BasicDBList;
    }

    public static boolean doesRequestContainUpdateOperators(BasicDBObject obj) {
        return obj.keySet().stream().allMatch(key -> {
            return !UPDATE_OPERATORS.contains(key);
        });
    }

    public static boolean doesRequestUsesDotNotation(BasicDBObject obj) {
        return obj.keySet().stream().anyMatch(key -> {
            return key.contains(".");
        });
    }

}
