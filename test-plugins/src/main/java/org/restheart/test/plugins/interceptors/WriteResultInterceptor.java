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
package org.restheart.test.plugins.interceptors;

import org.bson.BsonDocument;
import org.bson.BsonNull;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;

@RegisterPlugin(name = "writeResult",
        description = "Adds a body to write responses with "
        + "updated and old version of the written document.",
        interceptPoint = InterceptPoint.RESPONSE,
        enabledByDefault = false)
public class WriteResultInterceptor implements MongoInterceptor {

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        final BsonDocument resp;

        if (response.getContent() != null && response.getContent().isDocument()) {
            resp = response.getContent().asDocument();
        } else {
            resp = new BsonDocument();
            response.setContent(resp);
        }

        resp.append("oldData", response.getDbOperationResult().getOldData()
                == null
                        ? new BsonNull()
                        : response.getDbOperationResult().getOldData());

        resp.append("newData", response.getDbOperationResult().getNewData()
                == null
                        ? new BsonNull()
                        : response.getDbOperationResult().getNewData());
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return request.isHandledBy("mongo")
                && !request.isGet()
                && "xcoll".equals(request.getCollectionName())
                && response.getDbOperationResult() != null;
    }
}
