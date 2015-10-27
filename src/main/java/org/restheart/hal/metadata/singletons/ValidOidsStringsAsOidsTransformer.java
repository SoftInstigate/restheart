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
import com.mongodb.DBObject;
import io.undertow.server.HttpServerExchange;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.bson.types.ObjectId;
import org.restheart.handlers.RequestContext;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 *
 * On REQUEST phase This transformer replaces strings that are valid ObjectIds
 * with ObjectIds.
 *
 * <br>Example:
 * <br>POST( { "_id": "553f59d2e4b041ceaac64e33", a:2 } -> { "_id": {"$oid":
 * "553f59d2e4b041ceaac64e33" }, a: 1 }
 *
 */
public class ValidOidsStringsAsOidsTransformer implements Transformer {
    /**
     *
     * @param exchange
     * @param context
     * @param contentToTransform
     * @param args names of properties to tranform eventually (if value is valid
     * ObjectId) as an array of strings (["_id", "prop2"]
     */
    @Override
    public void tranform(final HttpServerExchange exchange, final RequestContext context, DBObject contentToTransform, final DBObject args) {
        // this set contains the names of the properties to transform eventually
        Set<String> propertiesToTransform = new HashSet<>();

        if (args instanceof BasicDBList) {
            BasicDBList _ids = (BasicDBList) args;

            _ids.forEach(propertyName -> {
                if (propertyName instanceof String) {
                    propertiesToTransform.add((String) propertyName);

                } else {
                    context.addWarning("element in the args list is not a string: " + propertyName);
                }
            });

        } else {
            context.addWarning("transformer wrong definition: args property must be an arrary of string (properties names).");
        }

        _transform(contentToTransform, propertiesToTransform);
    }

    private void _transform(DBObject data, Set<String> propertiesNames) {
        data.keySet().stream().forEach(key -> {
            Object value = data.get(key);

            if (shouldTransform(key, propertiesNames)) {
                if (value instanceof String && ObjectId.isValid((String) value)) {
                    data.put(key, new ObjectId((String) value));
                }
            }

            if (value instanceof DBObject) {
                _transform((DBObject) value, propertiesNames);
            }
        });
    }

    /**
     * @param key the name of the property to transform (in case of patch can
     * also use the dot notation)
     * @param propertiesToTransform the set of properties names to tranform if
     * their value is a valid ObjectId
     * @return true if the property should be transformed
     */
    private boolean shouldTransform(String key, Set<String> propertiesToTransform) {
        if (key.contains(".")) {
            String keyTokens[] = key.split(Pattern.quote("."));
            
            key = keyTokens[keyTokens.length - 1];
        } 
        
        return propertiesToTransform.contains(key);
    }
}
