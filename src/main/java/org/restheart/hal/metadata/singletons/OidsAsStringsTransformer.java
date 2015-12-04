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

import com.mongodb.DBObject;
import io.undertow.server.HttpServerExchange;
import org.bson.types.ObjectId;
import org.restheart.handlers.RequestContext;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 *
 * On RESPONSE phase this transformer replaces ObjectId with strings.
 *
 * <br>Example, document { "_id": {"$oid": "553f59d2e4b041ceaac64e33" }, a: 1 } 
 * <br>GET -> { "_id": "553f59d2e4b041ceaac64e33", a:1 } 
 *
 */
public class OidsAsStringsTransformer implements Transformer {
    /**
     *
     * @param exchange
     * @param context
     * @param contentToTransform
     * @param args
     */
    @Override
    public void tranform(final HttpServerExchange exchange, final RequestContext context, DBObject contentToTransform, final DBObject args) {
        _transform(contentToTransform);
    }
    
    private void _transform(DBObject data) {
        data.keySet().stream().forEach(key -> {
            Object value = data.get(key);
            
            if (value instanceof DBObject) {
                _transform((DBObject)value);
            } else if (value instanceof ObjectId) {
                data.put(key, ((ObjectId)value).toString());
            }
        });
    }
}