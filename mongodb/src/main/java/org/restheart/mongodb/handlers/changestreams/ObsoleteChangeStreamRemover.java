/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2024 SoftInstigate
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
package org.restheart.mongodb.handlers.changestreams;

import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;

@RegisterPlugin(name = "obsoleteChangeStreamRemover", description = "removes obsolete change stream and WebSocket sessions (due to deleted db/collection, or updated change stream definition)", interceptPoint = InterceptPoint.RESPONSE)
public class ObsoleteChangeStreamRemover implements MongoInterceptor {
    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        if (request.isDelete() && request.isDb()) {
            closeAllOnDb(request.getDBName());
        } else if (request.isDelete() && request.isCollection()) {
            closeAllOnCollection(request.getDBName(), request.getCollectionName());
        } else if ((request.isPut() || request.isPatch()) && request.isCollection()) {
            // here we need to check if the collection stream definitions got updated
            var oldStreams = response.getDbOperationResult() != null
                ? response.getDbOperationResult().getOldData() != null
                ? response.getDbOperationResult().getOldData().containsKey("streams")
                ? response.getDbOperationResult().getOldData().get("streams")
                : null : null : null;

            var newStreams = response.getDbOperationResult() != null
                ? response.getDbOperationResult().getNewData() != null
                ? response.getDbOperationResult().getNewData().containsKey("streams")
                ? response.getDbOperationResult().getNewData().get("streams")
                : null : null : null;

            if ((oldStreams == null && newStreams != null)
            || (oldStreams != null && newStreams == null)
            || (oldStreams != null && newStreams != null
                    && !oldStreams.equals(newStreams))) { // <- NEED TO CHECK THIS EQUALS
                closeAllOnCollection(request.getDBName(), request.getCollectionName());
            }
        }
    }

    private void closeAllOnDb(String db) {
        ChangeStreamWorkers.getInstance().getWorkersOnDb(db).stream().forEach(csw -> csw.getDbName());
    }

    private void closeAllOnCollection(String db, String collection) {
        ChangeStreamWorkers.getInstance().getWorkersOnCollection(db, collection);
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return (request.isDelete() && request.isCollection())
        || (request.isPut() && request.isCollection())
        || (request.isPatch() && request.isCollection())
        || (request.isDelete() && request.isDb());
    }
}
