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

import static org.restheart.utils.BsonUtils.containsUpdateOperators;
import static org.restheart.utils.BsonUtils.isUpdateOperator;
import static org.restheart.utils.BsonUtils.unescapeKeys;

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
        // unescapeKeys converts _$push -> $push, _$set -> $set, etc.
        // so that update operators in mergeRequest config are recognized
        var iToMerge = unescapeKeys(AclVarsInterpolator.interpolateBson(request, toMerge)).asDocument();

        var content = request.getContent().asDocument();

        if (containsUpdateOperators(iToMerge)) {
            // mergeRequest config contains update operators (e.g. $push).
            // Separate them from regular fields: operators go at root level,
            // regular fields go into $set.

            var updateOps = new BsonDocument();
            var regularFields = new BsonDocument();

            iToMerge.keySet().forEach(key -> {
                if (isUpdateOperator(key)) {
                    updateOps.put(key, iToMerge.get(key));
                } else {
                    regularFields.put(key, iToMerge.get(key));
                }
            });

            // Place update operators at root level
            updateOps.keySet().forEach(key -> {
                if (content.containsKey(key)) {
                    var existing = content.get(key);
                    var incoming = updateOps.get(key);
                    if (existing.isDocument() && incoming.isDocument()) {
                        existing.asDocument().putAll(incoming.asDocument());
                    }
                } else {
                    content.put(key, updateOps.get(key));
                }
            });

            // Remove from $set and root any keys that array operators manage,
            // to avoid ConflictingUpdateOperators (e.g. $set.consents + $push.consents)
            var arrayOps = new String[]{"$push", "$addToSet", "$pushAll"};
            for (var op : arrayOps) {
                if (updateOps.containsKey(op)) {
                    var targets = updateOps.get(op);
                    if (targets.isDocument()) {
                        targets.asDocument().keySet().forEach(targetKey -> {
                            content.remove(targetKey);
                            if (content.containsKey("$set")) {
                                content.get("$set").asDocument().remove(targetKey);
                            }
                        });
                    }
                }
            }

            // Merge regular fields into $set
            // Remove keys that mergeRequest manages from both $set and root,
            // so the client cannot override them
            if (!regularFields.isEmpty()) {
                regularFields.keySet().forEach(key -> content.remove(key));

                if (content.containsKey("$set")) {
                    var setOperator = content.get("$set");
                    if (setOperator.isDocument()) {
                        regularFields.keySet().forEach(key -> setOperator.asDocument().remove(key));
                        setOperator.asDocument().putAll(regularFields);
                    }
                } else {
                    content.put("$set", regularFields);
                }
            }
        } else if (containsUpdateOperators(content)) {
            // Client body has update operators, mergeRequest has only regular fields.
            // Merge into $set.

            if (content.containsKey("$set")) {
                var setOperator = content.get("$set");
                if (setOperator.isDocument()) {
                    setOperator.asDocument().putAll(iToMerge);
                }
            } else {
                content.put("$set", iToMerge);
            }
        } else {
            // Neither has update operators — merge at root level
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
