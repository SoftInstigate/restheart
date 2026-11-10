/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2026 SoftInstigate
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
package org.restheart.mongodb.security;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

import org.restheart.exchange.ExchangeKeys;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.Request;
import org.restheart.mongodb.MongoServiceConfiguration;
import org.restheart.plugins.InitPoint;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.Inject;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.BaseAclPermission;
import org.restheart.security.BaseAclPermissionTransformer;
import org.restheart.security.MongoPermissions;

/**
 * Allows the {@code filter}, {@code sort} and {@code sort_by} query parameters on reads according
 * to the {@code mongo.allowFilter} ACL permission.
 *
 * <p>A read that carries one of them, from a permission that does not allow it, does not match
 * the permission: another one may allow it, otherwise it gets 403. A permission that does not set
 * {@code allowFilter} takes the {@code default-allow-filter} of the mongo configuration,
 * {@code false} by default.
 * The application queries through aggregations, which bind only the variables they declare.</p>
 *
 * <p>Writes are not affected: a bulk {@code PATCH} or {@code DELETE} needs a filter, and
 * {@code allowBulkPatch} and {@code allowBulkDelete} decide about it. Neither are the root role and
 * the requests allowed through the {@code ACLRegistry}, which do not go through the permissions.</p>
 *
 * @since 9.16.0
 */
@RegisterPlugin(name = "mongoPermissionAllowFilter",
        description = "Allow the filter, sort and sort_by query parameters on reads according to the mongo.allowFilter ACL permission",
        initPoint = InitPoint.BEFORE_STARTUP,
        enabledByDefault = true)
public class AllowFilter extends BaseAllowInitializer implements Initializer {
    @Inject("registry")
    private PluginsRegistry registry;

    @Override
    public void init() {
        this.registry.getPermissionTransformers()
                .add(new BaseAclPermissionTransformer(resolve, additionalPredicate));
    }

    // apply the transformation if the permission does not allow filter for sure: when it does not
    // set allowFilter, the default comes from the mongo configuration, read with the request
    private final Predicate<BaseAclPermission> resolve = p -> {
        try {
            return !Boolean.TRUE.equals(MongoPermissions.from(p).getAllowFilter());
        } catch (IllegalArgumentException e) {
            // a malformed mongo block, e.g. "allowFilter": "no": the check applies, and refuses
            return true;
        }
    };

    private final BiPredicate<BaseAclPermission, Request<?>> additionalPredicate = (p, _request) -> {
        if (!isHandledByMongoService(_request)) {
            return true;
        }

        var mr = (MongoRequest) _request;

        if (!mr.isGet() || !carriesQuery(mr)) {
            return true;
        }

        try {
            return MongoPermissions.from(p).isAllowFilter(MongoServiceConfiguration.get().getDefaultAllowFilter());
        } catch (IllegalArgumentException e) {
            // fail closed: a permission whose allowFilter cannot be read does not allow filter
            return false;
        }
    };

    /**
     * @return true if the request has a filter, sort or sort_by query parameter
     */
    static boolean carriesQuery(MongoRequest request) {
        var qparams = request.getExchange().getQueryParameters();

        return qparams.containsKey(ExchangeKeys.FILTER_QPARAM_KEY)
                || qparams.containsKey(ExchangeKeys.SORT_QPARAM_KEY)
                || qparams.containsKey(ExchangeKeys.SORT_BY_QPARAM_KEY);
    }
}
