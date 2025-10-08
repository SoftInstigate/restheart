/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2025 SoftInstigate
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

import static org.restheart.utils.BsonUtils.containsUpdateOperators;

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
        var iToMerge = AclVarsInterpolator.interpolateBson(request, toMerge).asDocument();

        var content = request.getContent().asDocument();

        // Check if content contains update operators (e.g., $set, $inc, $push, etc.)
        if (containsUpdateOperators(content)) {
            // When using update operators, we need to merge the fields into the $set operator
            // because MongoDB doesn't allow mixing update operators with regular fields at root level

            if (content.containsKey("$set")) {
                // If $set already exists, merge into it
                var setOperator = content.get("$set");
                if (setOperator.isDocument()) {
                    setOperator.asDocument().putAll(iToMerge);
                }
            } else {
                // If $set doesn't exist, create it with the merge fields
                content.put("$set", iToMerge);
            }
        } else {
            // For regular documents (not using update operators), merge at root level
            content.putAll(iToMerge);
        }
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
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
