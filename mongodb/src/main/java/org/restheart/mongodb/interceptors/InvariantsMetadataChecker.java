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

import org.restheart.exchange.InvalidMetadataException;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.mongodb.MongoServiceConfiguration;
import org.restheart.mongodb.metadata.Invariant;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.AggregationPipelineSecurityChecker;
import org.restheart.utils.HttpStatus;

/**
 * Refuses malformed {@code invariants} metadata when it is declared, rather than when a write first
 * meets it.
 *
 * <p>A collection that looks configured and quietly enforces nothing is the worst of the possible
 * outcomes, and the person writing the metadata is the one who can fix it.
 *
 * <p>The pipelines are held to {@code aggregationSecurity}, unchanged and in full — the same
 * settings that already constrain {@code aggrs}. There is no invariant-specific restriction and no
 * invariant-specific switch: a deployment that has lifted {@code $lookup} has made that decision
 * for its aggregations, and invariants follow it.
 */
@RegisterPlugin(
        name = "invariantsMetadataChecker",
        description = "validates the 'invariants' collection metadata when it is written",
        interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH)
public class InvariantsMetadataChecker implements MongoInterceptor {

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        final var declared = request.getContent().asDocument();

        try {
            final var invariants = Invariant.getFromJson(declared);
            final var security = new AggregationPipelineSecurityChecker(
                    MongoServiceConfiguration.get().getAggregationSecurityConfiguration());

            for (final var invariant : invariants) {
                security.validatePipelineOrThrow(invariant.stages(), request.getDBName());
            }
        } catch (final InvalidMetadataException ime) {
            response.setInError(HttpStatus.SC_BAD_REQUEST, ime.getMessage());
        } catch (final SecurityException se) {
            response.setInError(HttpStatus.SC_BAD_REQUEST,
                    "invariant pipeline refused by aggregationSecurity: " + se.getMessage());
        }
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return request.isHandledBy("mongo")
                && (request.isPut() || request.isPatch())
                && request.isCollection()
                && request.getContent() != null
                && request.getContent().isDocument()
                && request.getContent().asDocument().containsKey(Invariant.INVARIANTS_ELEMENT_NAME);
    }
}
