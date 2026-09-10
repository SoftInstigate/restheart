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
import org.restheart.mongodb.metadata.Constraint;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;

/**
 * Runs in a transaction every write to a collection that declares constraints.
 *
 * <p>{@link ConstraintsChecker} judges the state the write would leave and has to be able to undo
 * it, which is only free of traces if it was never committed. A transaction cannot be opened around
 * a write that already happened, so the decision is taken here, before it.
 *
 * <p>Where transactions are not available — no replica set — the write is refused rather than
 * allowed through unchecked. A rule believed to be enforced and silently not enforced is worse than
 * no rule, and this is the only moment a deployment can be told: constraints live in collection
 * metadata, so they cannot be known at startup.
 */
@RegisterPlugin(
        name = "constraintsTxn",
        description = "runs in a transaction the writes to a collection declaring constraints",
        interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH)
public class ConstraintsTxn implements MongoInterceptor {

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        if (Constraint.getFromJson(request.getCollectionProps()).stream().noneMatch(Constraint::enabled)) {
            return;
        }

        if (!ClientSessionInjector.transactionsAvailable()) {
            response.setInError(HttpStatus.SC_NOT_IMPLEMENTED,
                    "collection '" + request.getCollectionName() + "' declares constraints, which need "
                            + "MongoDB configured as a replica set: without transactions a write that "
                            + "breaks one could not be undone");
            return;
        }

        request.startTxn();
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return request.isHandledBy("mongo")
                && ConstraintsChecker.isDocumentWrite(request)
                && request.getCollectionProps() != null
                && request.getCollectionProps().containsKey(Constraint.CONSTRAINTS_ELEMENT_NAME);
    }
}
