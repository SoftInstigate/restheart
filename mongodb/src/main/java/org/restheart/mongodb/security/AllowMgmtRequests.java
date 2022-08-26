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
import org.restheart.security.MongoPermissions;
import org.restheart.security.BaseAclPermissionTransformer;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.Request;
import org.restheart.plugins.InitPoint;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.Inject;

@RegisterPlugin(name = "mongoPermissionAllowMgmtRequests",
    description = "Allow mongo management requests according to the mongo.allowManagementRequests ACL permission",
    initPoint = InitPoint.BEFORE_STARTUP,
    enabledByDefault = true)
public class AllowMgmtRequests extends BaseAllowInitializer implements Initializer  {
    @Inject("registry")
    private PluginsRegistry registry;

    @Override
    public void init() {
        this.registry.getPermissionTransformers()
            .add(new BaseAclPermissionTransformer(resolve, additionalPredicate));
    }

    // apply the transformation if the permission does not allow mgmt requests
    private Predicate<BaseAclPermission> resolve = p -> {
        try {
            return ! MongoPermissions.from(p.getRaw()).isAllowManagementRequests();
        } catch(IllegalArgumentException e) {
            return false;
        }
    };

    private BiPredicate<BaseAclPermission, Request<?>> additionalPredicate = (permission, _request) -> {
        if (!isHandledByMongoService(_request)) {
            return true;
        }

        var mongoRequest = (MongoRequest) _request;

        return !(
            (mongoRequest.isDb() && !mongoRequest.isGet()) || // create/delete dbs
            (mongoRequest.isCollection() && (!mongoRequest.isGet() && !mongoRequest.isPost())) || // create/update/delete collections
            (mongoRequest.isIndex()) || // indexes
            (mongoRequest.isCollectionIndexes()) || // indexes

            (mongoRequest.isFilesBucket() && !mongoRequest.isGet() && !mongoRequest.isPost()) || // create/update/delete file buckets

            (mongoRequest.isSchema()) || // schema store
            (mongoRequest.isSchemaStore()) || // schema store
            (mongoRequest.isSchemaStoreSize()) || // schema store size

            (mongoRequest.isDbMeta()) || // db metadata
            (mongoRequest.isCollectionMeta()) || // collection metadata
            (mongoRequest.isFilesBucketMeta()) || // file bucket metadata
            (mongoRequest.isSchemaStoreMeta()));
    };
}
