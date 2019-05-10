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
package org.restheart.handlers.transformers;

import io.undertow.server.HttpServerExchange;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.restheart.handlers.RequestContext;
import static org.restheart.handlers.RequestContext._META;
import org.restheart.plugins.Transformer;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 *
 * During request phase, this transformer just sets the pagesize to 0 to avoid
 * retrieving data for count request. During response phase set the content to
 * just contain the _size property
 *
 */
public class MetaRequestTransformer implements Transformer {

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
        // for response phase
        if (context.getResponseContent() != null
                && context.getResponseContent().isDocument()
                && context.getResponseContent().asDocument().containsKey("_id")) {
            context.getResponseContent().asDocument().put("_id", new BsonString(_META));
        }
    }
}
