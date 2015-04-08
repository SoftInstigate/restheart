/*
 * RESTHeart - the data REST API server
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
import org.restheart.handlers.RequestContext;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 *
 * This transformer filters out the properties from the resource representation.
 * the properties to filter out are passed in the args argumenet as an array of
 * strings.
 *
 * If added to the REQUEST phase, it avoids properties to be stored, if added to
 * the RESPONSE phase, it hides stored properties.
 *
 * <br>Example that removes the property 'password' from the response:
 * <br>rtl:=[{name:"filterProperties", "phase":"RESPONSE", "scope":"CHILDREN",
 * args:["password"]}]
 *
 */
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
    public void tranform(final HttpServerExchange exchange, final RequestContext context, DBObject contentToTransform, final DBObject args) {
        if (args instanceof BasicDBList) {
            BasicDBList toremove = (BasicDBList) args;

            toremove.forEach(_prop -> {
                if (_prop instanceof String) {
                    String prop = (String) _prop;

                    contentToTransform.removeField(prop);
                } else {
                    context.addWarning("property in the args list is not a string: " + _prop);
                }
            });

        } else {
            context.addWarning("transformer wrong definition: args property must be an arrary of string property names.");
        }
    }
}
