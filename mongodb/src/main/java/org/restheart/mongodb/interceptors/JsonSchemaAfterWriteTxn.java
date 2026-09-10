/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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
package org.restheart.mongodb.interceptors;

import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.mongodb.handlers.injectors.ClientSessionInjector;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;

/**
 * Asks for a transaction around the requests {@link JsonSchemaAfterWriteChecker} validates.
 *
 * <p>A {@code PATCH} carrying update operators has no document to validate until the update has
 * been applied, so it is checked at {@code RESPONSE} and undone when it does not pass. The same is
 * true of a bulk {@code PATCH}, which additionally could not be undone at all before there was a
 * transaction to abort. The undo is
 * only free of traces if the write was never committed, and a transaction cannot be opened around a
 * write that already happened — hence a separate interceptor here, at request time.
 *
 * <p>The decision is taken on the shape of the request, not on its outcome, so a transaction is
 * opened for every such {@code PATCH} and most commit with nothing to undo. That costs one commit
 * round trip. Where MongoDB is not a replica set {@code startTxn()} is a no-op and the check falls
 * back to the compensating write in {@code MongoResponse.rollback()}.
 */
@RegisterPlugin(
        name = "jsonSchemaAfterWriteTxn",
        description = "runs in a transaction the PATCH requests that jsonSchemaAfterWrite validates",
        interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH)
public class JsonSchemaAfterWriteTxn implements MongoInterceptor {

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        request.startTxn();
    }

    /**
     * The request half of {@link JsonSchemaAfterWriteChecker#resolve}: same requests, minus the
     * conditions on the write's result, which does not exist yet.
     *
     * <p>A bulk {@code PATCH} is included only where transactions are actually available. Without
     * one it cannot be validated at all — {@code rollback()} has never supported undoing a bulk
     * write — and {@code jsonSchemaBeforeWrite} refuses it with {@code 501} as it always has.
     */
    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return request.isHandledBy("mongo")
                && request.isPatch()
                && request.isWriteDocument()
                && (!request.isBulkDocuments() || ClientSessionInjector.transactionsAvailable())
                && request.getCollectionProps() != null
                && request.getCollectionProps().containsKey("jsonSchema")
                && request.getCollectionProps().get("jsonSchema").isDocument();
    }
}
