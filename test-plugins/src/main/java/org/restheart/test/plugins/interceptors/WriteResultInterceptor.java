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

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonNull;
import org.restheart.handlers.exchange.BsonRequest;
import org.restheart.handlers.exchange.BsonResponse;
import org.restheart.plugins.BsonInterceptor;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.RegisterPlugin;

@RegisterPlugin(name = "writeResult",
        description = "Adds a body to write responses with "
        + "updated and old version of the written document.",
        interceptPoint = InterceptPoint.RESPONSE,
        enabledByDefault = false)
public class WriteResultInterceptor implements BsonInterceptor {

    @Override
    public void handle(BsonRequest request, BsonResponse response) throws Exception {
        var responseContent = response.getContent();

        final BsonDocument resp;

        if (responseContent != null && responseContent.isDocument()) {
            resp = responseContent.asDocument();
        } else {
            resp = new BsonDocument();
        }

        resp.append("oldData", response.getDbOperationResult().getOldData()
                == null
                        ? new BsonNull()
                        : response.getDbOperationResult().getOldData());

        resp.append("newData", response.getDbOperationResult().getNewData()
                == null
                        ? new BsonNull()
                        : response.getDbOperationResult().getNewData());

        // this to deal with POST collection
        if (request.isCollection() && request.isPost()) {
            BsonDocument hal = new BsonDocument();
            BsonDocument embedded = new BsonDocument();
            BsonArray rhdoc = new BsonArray();

            rhdoc.add(resp);
            embedded.put("rh:result", rhdoc);
            hal.put("_embedded", embedded);
            response.setContent(hal);
        } else {
            response.setContent(resp);
        }
    }

    @Override
    public boolean resolve(BsonRequest request, BsonResponse response) {
        return "xcoll".equals(request.getCollectionName())
                && response.getDbOperationResult() != null;
    }
}
