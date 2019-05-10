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
package org.restheart.plugins.transformers;

import org.restheart.plugins.Transformer;
import io.undertow.server.HttpServerExchange;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.restheart.handlers.RequestContext;
import org.restheart.plugins.RegisterPlugin;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 *
 * On RESPONSE phase this transformer replaces ObjectId with strings.
 *
 * <br>Example, document { "_id": {"$oid": "553f59d2e4b041ceaac64e33" }, a: 1 } 
 * <br>GET -> { "_id": "553f59d2e4b041ceaac64e33", a:1 } 
 *
 */
@RegisterPlugin(name = "oidsToStrings",
        description = "Replaces ObjectId with strings.")
public class OidsAsStringsTransformer implements Transformer {
    /**
     *
     * @param exchange
     * @param context
     * @param contentToTransform
     * @param args
     */
    @Override
    public void transform(
            final HttpServerExchange exchange, 
            final RequestContext context, 
            BsonValue contentToTransform, 
            final BsonValue args) {
        if (contentToTransform == null) {
            // nothing to do
            return;
        }

        if (!contentToTransform.isDocument()) {
            throw new IllegalStateException(
                    "content to transform is not a document");
        }
        
        BsonDocument _contentToTransform = contentToTransform.asDocument();
        
        _transform(_contentToTransform);
    }
    
    private void _transform(BsonDocument data) {
        data.keySet().stream().forEach(key -> {
            BsonValue value = data.get(key);
            
            if (value.isDocument()) {
                _transform(value.asDocument());
            } else if (value.isObjectId()) {
                data.put(key, 
                        new BsonString(value
                                .asObjectId()
                                .getValue()
                                .toString()));
            }
        });
    }
}