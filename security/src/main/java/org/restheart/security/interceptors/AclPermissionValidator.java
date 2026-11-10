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
package org.restheart.security.interceptors;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.MongoPermissions;
import org.restheart.security.authorizers.MongoAclAuthorizer;
import org.restheart.utils.HttpStatus;

/**
 * Refuses a write to the ACL collection of {@code mongoAclAuthorizer} that carries a permission
 * whose {@code mongo} block is not valid, with {@code 400} and the reason. Without it such a
 * permission is stored, and the first request it should authorize is denied.
 *
 * <p>It reads the {@code mongo} block of each permission written: the document of a
 * {@code POST} or {@code PUT}, every document of a bulk {@code POST}, and the {@code mongo} field
 * of a {@code PATCH}, set directly or with {@code $set}. A {@code PATCH} that changes a single
 * property of the block with dot notation is not checked here: the permission is checked again
 * when a request uses it.
 *
 * @since 9.16.0
 */
@RegisterPlugin(name = "aclPermissionValidator",
        description = "refuses a write to the ACL collection of mongoAclAuthorizer whose permission has a mongo block that is not valid",
        interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH,
        enabledByDefault = true)
public class AclPermissionValidator implements MongoInterceptor {
    @Inject("registry")
    PluginsRegistry registry;

    private MongoAclAuthorizer authorizer;

    @OnInit
    public void init() {
        this.authorizer = registry.getAuthorizers().stream()
                .filter(PluginRecord::isEnabled)
                .map(PluginRecord::getInstance)
                .filter(a -> a instanceof MongoAclAuthorizer)
                .map(a -> (MongoAclAuthorizer) a)
                .findFirst()
                .orElse(null);
    }

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var content = request.getContent();

        try {
            if (content != null && content.isArray()) {
                for (var doc : content.asArray()) {
                    check(doc);
                }
            } else {
                check(content);
            }
        } catch (IllegalArgumentException e) {
            response.setInError(HttpStatus.SC_BAD_REQUEST, e.getMessage());
        }
    }

    private static void check(BsonValue value) {
        if (value == null || !value.isDocument()) {
            return;
        }

        var doc = value.asDocument();
        var mongo = doc.get("mongo");

        if (mongo == null && doc.get("$set") instanceof BsonDocument set) {
            mongo = set.get("mongo");
        }

        if (mongo != null) {
            MongoPermissions.from(new BsonDocument("mongo", mongo));
        }
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return authorizer != null
                && request.isHandledBy("mongo")
                && (request.isPost() || request.isPut() || request.isPatch())
                && request.getCollectionName() != null
                && request.getCollectionName().equals(authorizer.aclCollection())
                && request.getDBName() != null
                && request.getDBName().equals(authorizer.aclDb(request));
    }
}
