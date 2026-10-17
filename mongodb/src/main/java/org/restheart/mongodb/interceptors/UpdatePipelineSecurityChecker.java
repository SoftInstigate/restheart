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
import org.restheart.mongodb.MongoServiceConfiguration;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.AggregationPipelineSecurityChecker;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks the aggregation pipeline of a PATCH request against the aggregationSecurity blacklists.
 *
 * <p>A PATCH whose body is an array is an update with an aggregation pipeline, which MongoDB
 * evaluates on the server: a {@code $set} stage may hold {@code $function}, and nothing else
 * looks at that body. The pipeline goes through the same {@link AggregationPipelineSecurityChecker}
 * as the aggregations declared on a collection, so the stages and the operators refused there are
 * refused here too, with 403.
 *
 * <p>The filter and keys query parameters, the other places where a client writes operators that
 * MongoDB evaluates on the server, are checked by
 * {@link org.restheart.mongodb.security.FilterOperatorsBlacklist}.
 */
@RegisterPlugin(name = "updatePipelineSecurityChecker",
        description = "refuses a PATCH whose aggregation pipeline uses a stage or an operator forbidden by aggregationSecurity",
        interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH,
        requiresContent = true)
public class UpdatePipelineSecurityChecker implements MongoInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(UpdatePipelineSecurityChecker.class);

    // created on first use: the mongo service configuration is not available when plugins are instantiated
    private volatile AggregationPipelineSecurityChecker checker;

    private AggregationPipelineSecurityChecker checker() {
        var c = checker;

        if (c == null) {
            c = new AggregationPipelineSecurityChecker(MongoServiceConfiguration.get().getAggregationSecurityConfiguration());
            checker = c;
        }

        return c;
    }

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        if (response.isInError()) {
            return;
        }

        var violation = checker().validatePipeline(request.getContent().asArray(), request.getDBName());

        if (violation.isPresent()) {
            LOGGER.warn("Update pipeline blocked for security violation: {}", violation.get());
            response.setInError(HttpStatus.SC_FORBIDDEN, "update pipeline security violation: " + violation.get());
        }
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        // only an update with an aggregation pipeline: a document body is a classic update, where
        // MongoDB evaluates no expression
        return request.isHandledBy("mongo")
                && request.isPatch()
                && request.getContent() != null
                && request.getContent().isArray();
    }
}
