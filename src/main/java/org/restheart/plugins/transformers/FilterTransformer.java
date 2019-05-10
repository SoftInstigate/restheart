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
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.handlers.RequestContext;
import org.restheart.plugins.RegisterPlugin;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 *
 * This transformer filters out the properties from the resource representation.
 * the properties to filter out are passed in the args argumenet as an array of
 * strings.
 *
 * When applied to the REQUEST phase, it avoids properties to be stored, when
 * applied to the RESPONSE phase, it hides stored properties.
 *
 * <br>Example that removes the property 'password' from the response:
 * <br>rts:=[{name:"filterProperties", "phase":"RESPONSE", "scope":"CHILDREN",
 * args:["password"]}]
 *
 */
@RegisterPlugin(name = "filterProperties",
        description = "Filters out a the properties specified "
                + "by the args property of the transformer metadata object.")
public class FilterTransformer implements Transformer {

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
        if (contentToTransform == null) {
            // nothing to do
            return;
        }

        if (!contentToTransform.isDocument()) {
            throw new IllegalStateException(
                    "content to transform is not a document");
        }

        BsonDocument _contentToTransform = contentToTransform.asDocument();

        if (args.isArray()) {
            BsonArray toremove = args.asArray();

            toremove.forEach(_prop -> {
                if (_prop.isString()) {
                    String prop = _prop.asString().getValue();

                    _contentToTransform.remove(prop);
                } else {
                    context.addWarning("property in the args list "
                            + "is not a string: " + _prop);
                }
            });

        } else {
            context.addWarning("transformer wrong definition: "
                    + "args property must be an arrary "
                    + "of string property names.");
        }
    }
}
