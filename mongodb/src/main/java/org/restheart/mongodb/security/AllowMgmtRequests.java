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
import org.restheart.security.MongoPermissions;
import org.restheart.security.BaseAclPermissionTransformer;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import org.bson.BsonDocument;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.Request;
import org.restheart.exchange.ServiceRequest;
import org.restheart.plugins.InitPoint;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.InjectPluginsRegistry;

@RegisterPlugin(name = "mongoPermissionAllowMgmtRequests",
    description = "Allow mongo management requests according to the mongo.allowManagementRequests ACL permission", 
    initPoint = InitPoint.BEFORE_STARTUP,
    enabledByDefault = true)
public class AllowMgmtRequests implements Initializer {
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

    // apply the transformation if the raw permission includes the 'mongo' object
    private Predicate<BaseAclPermission> resolve = p -> {
        var raw = p.getRaw();
        return ((raw instanceof BsonDocument
            && ((BsonDocument)raw).containsKey("mongo")
            && ((BsonDocument)raw).get("mongo").isDocument()))
            ||
            ((raw instanceof Map
            && ((Map<?,?>)raw).containsKey("mongo")
            && ((Map<?,?>)raw).get("mongo") instanceof Map<?,?>));
    };

    private BiPredicate<BaseAclPermission, Request<?>> additionalPredicate = (permission, _request) -> {
        if (_request instanceof ServiceRequest) {
            var request = (ServiceRequest<?>) _request;

            var mongoPermissions = MongoPermissions.from(permission.getRaw());

            if (!request.isHandledBy("mongo") || mongoPermissions == null) {
                return true; // --> allow
            } else if (!mongoPermissions.isAllowManagementRequests()) {
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
            } else {
                return true; // --> allow
            }
        } else {
            return true; // --> allow
        }
    };
}
