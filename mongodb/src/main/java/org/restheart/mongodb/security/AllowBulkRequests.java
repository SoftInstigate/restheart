/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2022 SoftInstigate
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

import java.util.function.BiPredicate;
import java.util.function.Predicate;

import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.Request;
import org.restheart.plugins.InitPoint;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.InjectPluginsRegistry;

@RegisterPlugin(name = "mongoPermissionAllowBulkRequests",
    description = "Allow bulk PATCH and bulk DELETE according to the mongo.allowBulkPatch and mongo.allowBulkDelete ACL permissions",
    initPoint = InitPoint.BEFORE_STARTUP,
    enabledByDefault = true)
public class AllowBulkRequests extends BaseAllowInitializer implements Initializer {
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

    // apply the transformation if the permission does not allow bulks requests
    private Predicate<BaseAclPermission> resolve = p -> {
        try {
            var mp = MongoPermissions.from(p);
            return !(mp.isAllowBulkDelete() && mp.isAllowBulkPatch());
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

        return !((!mp.isAllowBulkDelete() && mr.isBulkDocuments() && mr.isDelete())
              || (!mp.isAllowBulkPatch()  && mr.isBulkDocuments() && mr.isPatch()));
    };
}
