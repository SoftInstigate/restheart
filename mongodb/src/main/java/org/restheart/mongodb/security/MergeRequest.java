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

import org.bson.BsonDocument;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.AclVarsInterpolator;
import org.restheart.security.MongoPermissions;

@RegisterPlugin(name = "mongoPermissionMergeRequest",
    description = "Override properties's values in write requests according to the mongo.mergeRequest ACL permission",
    interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH,
    enabledByDefault = true,
    priority = 11)
public class MergeRequest implements MongoInterceptor {
    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var toMerge = MongoPermissions.of(request).getMergeRequest();

        if (request.getContent().isDocument()) {
            merge(request, toMerge);
        } else if (request.getContent().isArray()) {
            request.getContent().asArray().stream().map(doc -> doc.asDocument())
                    .forEachOrdered(doc -> merge(request, toMerge));
        }
    }

    private void merge(MongoRequest request, BsonDocument toMerge) {
        var iToMegere = AclVarsInterpolator.interpolateBson(request, toMerge).asDocument();

        request.getContent().asDocument().putAll(iToMegere);
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        if (!request.isHandledBy("mongo") || request.getContent() == null) {
            return false;
        }

        if (request.isGet() || request.isDelete()) {
            return false;
        }

        var mongoPermission = MongoPermissions.of(request);

        if (mongoPermission != null) {
            return mongoPermission.getMergeRequest() != null;
        } else {
            return false;
        }
    }
}
