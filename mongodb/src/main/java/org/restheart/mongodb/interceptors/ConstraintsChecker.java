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

import static com.mongodb.client.model.Filters.eq;

import java.util.ArrayList;
import java.util.List;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.mongodb.MongoServiceConfiguration;
import org.restheart.mongodb.RHMongoClients;
import org.restheart.mongodb.metadata.Constraint;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.model.UpdateOptions;

/**
 * Enforces the integrity rules a collection declares as {@code constraints} metadata.
 *
 * <p>Each rule is an aggregation that must return nothing (or something — see
 * {@link Constraint.HoldsWhen}). They run after the write and inside its transaction, so what they
 * see is the state the write would leave behind; if any of them is broken the transaction is
 * aborted and nothing was ever committed.
 *
 * <p>This is a property of the data, not of the caller: it runs whatever permission authorized the
 * write, whatever the role, {@code admin} included.
 */
@RegisterPlugin(
        name = "constraints",
        description = "checks a collection's declared constraints after a write, and undoes the write when one is broken",
        interceptPoint = InterceptPoint.RESPONSE,
        // after jsonSchemaAfterWrite (10), well before txnCloser (MAX_VALUE) which ends the transaction
        priority = 20)
public class ConstraintsChecker implements MongoInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConstraintsChecker.class);

    /** How many violating documents are read and reported. Enough to diagnose, bounded by design. */
    private static final int MAX_VIOLATIONS = 10;

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        // resolve() is evaluated for every interceptor up front, in one pass, before any of them
        // runs — so a write already refused by one that ran earlier (jsonSchemaAfterWrite, say) is
        // not visible there. Checking here is what stops us from replacing its error with ours.
        if (response.isInError()) {
            return;
        }

        final var constraints = enabled(request);

        if (constraints.isEmpty()) {
            return;
        }

        try {
            serialize(request);
        } catch (final com.mongodb.MongoException me) {
            LOGGER.debug("Could not take the constraints guard for {}", request.getCollectionName(), me);

            response.setInError(HttpStatus.SC_CONFLICT,
                    "the write conflicted with a concurrent one and was not applied, retry the request");
            response.rollback(RHMongoClients.mclient());
            return;
        }

        for (final var constraint : constraints) {
            final var violations = evaluate(request, constraint);

            if (constraint.violatedBy(violations.size())) {
                refuse(request, response, constraint, violations);
                return;
            }
        }
    }

    /**
     * Takes the collection's guard document, in the write's own transaction.
     *
     * <p>This is what makes an constraint a guarantee rather than a hope. MongoDB transactions give
     * snapshot isolation: the engine detects two transactions writing the same document, and
     * nothing else. Two writes that are each valid against their own snapshot and invalid together
     * would both commit — the classic write skew, and exactly the case an constraint exists to stop.
     * Having every write to the collection touch one document turns that undetectable read-write
     * conflict into a write-write conflict the engine does detect: one of the two aborts.
     *
     * <p>The document's content is irrelevant; it exists to be contended. The cost is that writes
     * to a collection declaring constraints serialize, which is why constraints are opt-in per
     * collection.
     */
    private void serialize(MongoRequest request) {
        RHMongoClients.mclient()
                .getDatabase(request.getDBName())
                .getCollection(MongoServiceConfiguration.get().getConstraintsGuardCollection(), BsonDocument.class)
                .updateOne(
                        request.getClientSession(),
                        eq("_id", new BsonString(request.getCollectionName())),
                        new BsonDocument("$inc", new BsonDocument("v", new BsonInt64(1))),
                        new UpdateOptions().upsert(true));
    }

    /**
     * Runs one rule's pipeline over the collection, in the request's session so it sees the write
     * that is not committed yet — the whole point, since what is being judged is the state the
     * write would leave.
     */
    private List<BsonDocument> evaluate(MongoRequest request, Constraint constraint) {
        final var pipeline = new ArrayList<BsonDocument>();

        constraint.stages().forEach(stage -> pipeline.add(stage.asDocument()));
        pipeline.add(new BsonDocument("$limit", new BsonInt32(MAX_VIOLATIONS)));

        final var rows = new ArrayList<BsonDocument>();

        RHMongoClients.mclient()
                .getDatabase(request.getDBName())
                .getCollection(request.getCollectionName(), BsonDocument.class)
                .aggregate(request.getClientSession(), pipeline)
                .into(rows);

        return rows;
    }

    /**
     * 409, not 400: the request is well formed, it is the resulting state that is not admissible.
     * The rows are the useful half of the answer — which documents break the rule, not merely that
     * something does — and a notEmpty rule has none by definition, where {@code message} is the
     * whole diagnostic.
     */
    private void refuse(MongoRequest request, MongoResponse response, Constraint constraint, List<BsonDocument> violations)
            throws Exception {
        final var message = constraint.message() == null
                ? "constraint '" + constraint.name() + "' is not satisfied"
                : constraint.message();

        response.setInError(HttpStatus.SC_CONFLICT, message);

        if (response.getContent() != null && response.getContent().isDocument()) {
            final var error = response.getContent().asDocument();

            error.put("constraint", new BsonString(constraint.name()));

            if (!violations.isEmpty()) {
                error.put("violations", new BsonArray(List.copyOf(violations)));
            }
        }

        LOGGER.debug("Constraint '{}' broken by the write to {}, rolling it back",
                constraint.name(), request.getCollectionName());

        response.rollback(RHMongoClients.mclient());
    }

    private static List<Constraint> enabled(MongoRequest request) throws Exception {
        return Constraint.getFromJson(request.getCollectionProps()).stream().filter(Constraint::enabled).toList();
    }

    /**
     * Every shape of document write: insert, update, bulk update, and delete — a delete breaks
     * "this parent exists" as readily as an insert breaks "no balance is negative", because the
     * rule is a property of the state and not of the operation.
     */
    static boolean isDocumentWrite(MongoRequest request) {
        return request.isWriteDocument()
                || request.isBulkDocuments()
                || (request.isDelete() && request.isDocument());
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return request.isHandledBy("mongo")
                && isDocumentWrite(request)
                && request.getCollectionProps() != null
                && request.getCollectionProps().containsKey(Constraint.CONSTRAINTS_ELEMENT_NAME)
                && request.getClientSession() != null
                && request.getClientSession().hasActiveTransaction()
                && response.getDbOperationResult() != null
                && response.getDbOperationResult().getHttpCode() < 300;
    }
}
