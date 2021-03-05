/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2020 SoftInstigate
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

import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.BaseAclPermission;
import org.restheart.security.BaseAclPermissionTransformer;
import org.restheart.security.MongoPermissions;

import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.Request;
import org.restheart.plugins.InitPoint;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.InjectPluginsRegistry;

@RegisterPlugin(name = "mongoPermissionForbidQueryParams",
    description = "Forbids query parameters according to the mongo.forbidQueryParams ACL permission",
    initPoint = InitPoint.BEFORE_STARTUP,
    enabledByDefault = true,
    priority = 10)
public class ForbidQueryParams extends BaseAllowInitializer implements Initializer {
    private PluginsRegistry registry;

    @InjectPluginsRegistry
    public void initRegistry(PluginsRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void init() {
        this.registry.getPermissionTransformers()
            .add(new BaseAclPermissionTransformer(resolve, additionalPredicate));
    }

    // apply the transformation if the permission contains fobbidden qparams
    private Predicate<BaseAclPermission> resolve = p -> {
        try {
            return ! MongoPermissions.from(p.getRaw()).getForbidQueryParams().isEmpty();
        } catch(IllegalArgumentException e) {
            return false;
        }
    };

    private BiPredicate<BaseAclPermission, Request<?>> additionalPredicate = (p, _request) -> {
        if (!isHandledByMongoService(_request)) {
            return true;
        }

        var mr = (MongoRequest) _request;
        var mp = MongoPermissions.from(p);

        var forbidQueryParams = mp.getForbidQueryParams();

        return !contains(mr.getQueryParameters(), forbidQueryParams);
    };

    private boolean contains(Map<String, Deque<String>> queryParams, Set<String>  forbidQueryParams) {
        return queryParams != null
            && queryParams.keySet().stream().anyMatch(qp -> forbidQueryParams.contains(qp));
    }
}
