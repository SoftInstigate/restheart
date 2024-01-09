/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2024 SoftInstigate
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

import org.restheart.exchange.ExchangeKeys;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.Request;
import org.restheart.plugins.InitPoint;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.Inject;

@RegisterPlugin(name = "mongoPermissionAllowWriteMode",
    description = "Allow clients to specify the write mode according to the mongo.allowWriteMode ACL permission",
    initPoint = InitPoint.BEFORE_STARTUP,
    enabledByDefault = true)
public class AllowWriteMode extends BaseAllowInitializer implements Initializer {
    @Inject("registry")
    private PluginsRegistry registry;

    @Override
    public void init() {
        this.registry.getPermissionTransformers()
            .add(new BaseAclPermissionTransformer(resolve, additionalPredicate));
    }

    // apply the transformation if the permission does not allow write mode
    private Predicate<BaseAclPermission> resolve = p -> {
        try {
            return ! MongoPermissions.from(p.getRaw()).isAllowWriteMode();
        } catch(IllegalArgumentException e) {
            return false;
        }
    };

    private BiPredicate<BaseAclPermission, Request<?>> additionalPredicate = (permission, _request) -> {
        if (!isHandledByMongoService(_request)) {
            return true;
        }

        var mr = (MongoRequest) _request;

        return !mr.isWriteDocument()
        || (mr.getQueryParameterOrDefault(ExchangeKeys.WRITE_MODE_QPARAM_KEY, null) == null
            && mr.getQueryParameterOrDefault(ExchangeKeys.WRITE_MODE_SHORT_QPARAM_KEY, null) == null);
    };
}
