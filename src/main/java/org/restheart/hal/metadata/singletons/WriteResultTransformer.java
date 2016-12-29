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

import io.undertow.server.HttpServerExchange;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonNull;
import org.bson.BsonValue;
import org.restheart.hal.Representation;
import org.restheart.handlers.RequestContext;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class WriteResultTransformer implements Transformer {
    @Override
    public void transform(
            HttpServerExchange exchange,
            RequestContext context,
            BsonValue contentToTransform,
            BsonValue args) {
        if (context.getDbOperationResult() == null) {
            return;
        } else {
            BsonDocument resp = null;

            if (contentToTransform == null || !contentToTransform.isDocument()) {
                resp = new BsonDocument();
                context.setResponseContent(resp);
            } else if (contentToTransform.isDocument()) {
                resp = contentToTransform.asDocument();
            }

            if (resp != null) {
                resp.append("oldData", context.getDbOperationResult().getOldData()
                        == null
                                ? new BsonNull()
                                : context.getDbOperationResult().getOldData());
                
                resp.append("newData", context.getDbOperationResult().getNewData()
                        == null
                                ? new BsonNull()
                                : context.getDbOperationResult().getNewData());
            }
        }
    }
}
