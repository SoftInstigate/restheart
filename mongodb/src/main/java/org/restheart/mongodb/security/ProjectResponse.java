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

import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.MongoPermissions;

import java.util.Set;

import com.google.common.collect.Sets;

import org.bson.BsonArray;
import org.bson.BsonDocument;
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
        var projection = MongoPermissions.of(request).getProjectResponse();

        boolean inclusions = projection.get(projection.keySet().stream().findAny().get()).asInt32().getValue() == 1;

        if (response.getContent().isDocument()) {
            if (inclusions) {
                var projected = projectInclusions(response.getContent().asDocument(), projection);
                response.setContent(projected);
            } else {
                projectExclusions(response.getContent().asDocument(), projection);
            }
        } else if (response.getContent().isArray()) {
            var array = new BsonArray();
            if (inclusions) {
                response.getContent().asArray().forEach(doc ->
                    array.add(projectInclusions(doc.asDocument(), projection)));
                    response.setContent(array);
            } else {
                response.getContent().asArray().forEach(doc -> projectExclusions(doc.asDocument(), projection));
            }
        }
    }

    private void projectExclusions(BsonDocument doc, BsonDocument projection) {
        projection.keySet().stream().forEachOrdered(projectedProp ->  projectExlcusions(doc, projectedProp));
    }

    private void projectExlcusions(BsonDocument doc, String projectedProperty) {
        if (projectedProperty.contains(".")) {
            var first = projectedProperty.substring(0, projectedProperty.indexOf("."));
            if (first.length() > 0 && doc.containsKey(first) && doc.get(first).isDocument()) {
                projectExlcusions(doc.get(first).asDocument(), projectedProperty.substring(projectedProperty.indexOf(".")+1));
            }
        } else if (projectedProperty.length() > 0) {
            doc.remove(projectedProperty);
        }
    }

    private BsonDocument projectInclusions(BsonDocument doc, BsonDocument projection) {
        var includedKeys = projection.keySet();

        return projectInclusions(doc, includedKeys);
    }

    private BsonDocument projectInclusions(BsonDocument doc, Set<String> includedKeys) {
        var ret = new BsonDocument();
        includedKeys.stream().forEachOrdered(includedKey -> {
            if (includedKey.contains(".")) {
                var first = includedKey.substring(0, includedKey.indexOf("."));
                var remaining = includedKey.substring(includedKey.indexOf(".")+1);
                if (first.length() > 0 && doc.containsKey(first) && doc.get(first).isDocument()) {
                    var partial = projectInclusions(doc.get(first).asDocument(), Sets.newHashSet(remaining));

                    if (partial != null && !partial.isEmpty()) {
                        ret.put(first, partial);
                    }
                }
            } else if (includedKey.length() > 0) {
                if (doc.containsKey(includedKey)) {
                    ret.put(includedKey, doc.get(includedKey));
                };
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
