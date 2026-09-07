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

import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.MongoPermissions;

import java.util.Set;

import com.google.common.collect.Sets;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.InterceptPoint;

@RegisterPlugin(name = "mongoPermissionProjectResponse",
        description = "Hides properties from the response according to the mongo.projectResponse ACL permission",
        interceptPoint = InterceptPoint.RESPONSE,
        enabledByDefault = true)
public class ProjectResponse implements MongoInterceptor {

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        response.setContent(project(response.getContent(), MongoPermissions.of(request).getProjectResponse()));
    }

    /**
     * Applies a {@code mongo.projectResponse} ACL projection to a document or an array of them.
     *
     * <p>Public and static so that a read performed outside the HTTP pipeline — an MCP
     * documents-mode {@code resources/read} (restheart#722), which never reaches this interceptor
     * — hides exactly the same properties this interceptor hides, rather than a second
     * implementation of the same rule that can drift from it.
     *
     * @return the projected content ({@code content} itself when there is nothing to project)
     */
    public static BsonValue project(BsonValue content, BsonDocument projection) {
        if (content == null || projection == null || projection.isEmpty()) {
            return content;
        }

        var inclusions = projection.get(projection.keySet().stream().findAny().get()).asInt32().getValue() == 1;

        if (content.isDocument()) {
            if (inclusions) {
                return projectInclusions(content.asDocument(), projection);
            }
            projectExclusions(content.asDocument(), projection);
            return content;
        } else if (content.isArray()) {
            if (inclusions) {
                var array = new BsonArray();
                content.asArray().forEach(doc -> array.add(projectInclusions(doc.asDocument(), projection)));
                return array;
            }
            content.asArray().forEach(doc -> projectExclusions(doc.asDocument(), projection));
            return content;
        }

        return content;
    }

    private static void projectExclusions(BsonDocument doc, BsonDocument projection) {
        projection.keySet().stream().forEachOrdered(projectedProp -> projectExlcusions(doc, projectedProp));
    }

    private static void projectExlcusions(BsonDocument doc, String projectedProperty) {
        if (projectedProperty.contains(".")) {
            var first = projectedProperty.substring(0, projectedProperty.indexOf("."));
            if (first.length() > 0 && doc.containsKey(first) && doc.get(first).isDocument()) {
                projectExlcusions(doc.get(first).asDocument(), projectedProperty.substring(projectedProperty.indexOf(".") + 1));
            }
        } else if (projectedProperty.length() > 0) {
            doc.remove(projectedProperty);
        }
    }

    private static BsonDocument projectInclusions(BsonDocument doc, BsonDocument projection) {
        var includedKeys = projection.keySet();

        return projectInclusions(doc, includedKeys);
    }

    private static BsonDocument projectInclusions(BsonDocument doc, Set<String> includedKeys) {
        var ret = new BsonDocument();
        includedKeys.stream().forEachOrdered(includedKey -> {
            if (includedKey.contains(".")) {
                var first = includedKey.substring(0, includedKey.indexOf("."));
                var remaining = includedKey.substring(includedKey.indexOf(".") + 1);
                if (first.length() > 0 && doc.containsKey(first) && doc.get(first).isDocument()) {
                    var partial = projectInclusions(doc.get(first).asDocument(), Sets.newHashSet(remaining));

                    if (partial != null && !partial.isEmpty()) {
                        ret.put(first, partial);
                    }
                }
            } else if (includedKey.length() > 0) {
                if (doc.containsKey(includedKey)) {
                    ret.put(includedKey, doc.get(includedKey));
                }
                ;
            }
        });


        return ret;
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        if (!request.isHandledBy("mongo") || response.getContent() == null || !request.isGet()) {
            return false;
        }

        var mongoPermission = MongoPermissions.of(request);

        if (mongoPermission != null) {
            return mongoPermission.getProjectResponse() != null
                    && !mongoPermission.getProjectResponse().isEmpty();
        } else {
            return false;
        }
    }
}
