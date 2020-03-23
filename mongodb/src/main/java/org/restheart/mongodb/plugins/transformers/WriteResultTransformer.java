/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.mongodb.plugins.transformers;

import io.undertow.server.HttpServerExchange;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonNull;
import org.bson.BsonValue;
import org.restheart.handlers.exchange.RequestContext;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.mongodb.Transformer;
@RegisterPlugin(name = "writeResult",
        description = "Adds a body to write responses with "
                + "updated and old version of the written document.")
@SuppressWarnings("deprecation")
public class WriteResultTransformer implements Transformer {

    @Override
    public void transform(
            HttpServerExchange exchange,
            RequestContext context,
            BsonValue contentToTransform,
            BsonValue args) {
        if (context.getDbOperationResult() == null) {
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
            
            // this to deal with POST collection
            if (context.isCollection() && context.isPost()) {
                BsonDocument body = new BsonDocument();
                BsonDocument embedded = new BsonDocument();
                BsonArray rhdoc = new BsonArray();
                
                rhdoc.add(resp);
                embedded.put("rh:result", rhdoc);
                body.put("_embedded", embedded);
                context.setResponseContent(body);
            }
        }
    }
}
