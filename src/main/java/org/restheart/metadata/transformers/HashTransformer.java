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
package org.restheart.metadata.transformers;

import io.undertow.server.HttpServerExchange;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.mindrot.jbcrypt.BCrypt;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.RequestContext.TYPE;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 *
 * This transformer hashes the properties from the resource representation on
 * write requests using the bcrypt hash function the properties to hash out are
 * passed in the args argument as an array of strings. the bcrypt
 *
 * <br>Example that hashes the property 'password' from the response:
 * <br>{ rts: [{"name": "hashProperties", "phase":"REQUEST",
 * "args": { "props": ["password"], "complexity": 12 }}] }
 *
 */
public class HashTransformer implements Transformer {

    /**
     *
     * @param exchange
     * @param context
     * @param contentToTransform
     * @param args properties to filter out as an array of strings (["prop1",
     * "prop2"]
     */
    @Override
    public void transform(
            final HttpServerExchange exchange,
            final RequestContext context,
            BsonValue contentToTransform,
            final BsonValue args) {
        if (!doesApply(context) || contentToTransform == null) {
            // nothing to do
            return;
        }

        if (!contentToTransform.isDocument()) {
            throw new IllegalStateException(
                    "content to transform is not a document");
        }

        BsonDocument _contentToTransform = contentToTransform.asDocument();

        if (args.isDocument()) {
            BsonValue _tohash = args.asDocument().get("props");

            if (_tohash == null || !_tohash.isArray()) {
                context.addWarning("transformer wrong definition: "
                        + "args must be an object as {'props': [ 'password' ], "
                        + "'complexity': 12 }");
            }

            BsonArray tohash = null;
            if (null != _tohash) {
                tohash = _tohash.asArray();
            }

            BsonValue _complexity = args.asDocument().get("complexity");

            if (_complexity != null && !_complexity.isNumber()) {
                context.addWarning("transformer wrong definition: "
                        + "args must be an object as {'props': [ 'password' ], "
                        + "'complexity': 12 }");
            }

            int complexity = _complexity == null
                    ? 12
                    : _complexity.asNumber().intValue();

            if (null != tohash) {
                tohash.forEach(_prop -> {
                    if (_prop.isString()) {
                        String prop = _prop.asString().getValue();

                        BsonValue _value = _contentToTransform.get(prop);

                        if (_value != null && _value.isString()) {
                            String value = _value.asString().getValue();

                            _contentToTransform.replace(prop,
                                    new BsonString(
                                            BCrypt.hashpw(value,
                                                    BCrypt.gensalt(complexity))));

                        }
                    } else {
                        context.addWarning("property in the args list "
                                + "is not a string: " + _prop);
                    }
                });
            }

        } else {
            context.addWarning("transformer wrong definition: "
                    + "args must be an object as {'props': [ 'password'], "
                    + "'complexity': 12 }");
        }
    }

    private boolean doesApply(RequestContext context) {
        return ( // PUT|PATCH /db/coll/docid
                (context.getType() == TYPE.DOCUMENT
                && (context.getMethod() == RequestContext.METHOD.PATCH
                || context.getMethod() == RequestContext.METHOD.PUT))
                // POST /db/coll { <doc> } and POST /db/coll [ { <doc> }, { <doc> } ]
                || ((context.getType() == TYPE.COLLECTION)
                && (context.getMethod() == RequestContext.METHOD.POST))
                // PATCH /db/coll/*
                || ((context.getType() == TYPE.BULK_DOCUMENTS)
                && context.getMethod() == RequestContext.METHOD.PATCH));
    }
}
