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

import java.util.Map;

import org.bson.BsonValue;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.AclVarsInterpolator;
import org.restheart.security.MongoPermissions;
import org.restheart.utils.BsonUtils;

@RegisterPlugin(name = "mongoPermissionOverrideProps",
    description = "Override properties's values in write requests according to the mongo.overrideProps ACL permission",
    interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH,
    enabledByDefault = true,
    // must be lesser priority than mongoPermissionForbidProps
    priority = 11)
public class OverrideProps implements MongoInterceptor {
    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var overrideProps = MongoPermissions.of(request).getOverrideProps();

        if (request.getContent().isDocument()) {
            override(request, overrideProps);
        } else if (request.getContent().isArray()) {
            request.getContent().asArray().stream().map(doc -> doc.asDocument())
                    .forEachOrdered(doc -> override(request, overrideProps));
        }

        if (request.isPost()) {
            request.setContent(BsonUtils.unflatten(request.getContent()));
        }
    }

    private void override(MongoRequest request, Map<String, BsonValue> overrideProps) {
        overrideProps.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue().isString())
                .forEachOrdered(e -> override(request, e.getKey(), e.getValue().asString().getValue()));
    }

    private void override(MongoRequest request, String key, String value) {
        request.getContent().asDocument().put(key, AclVarsInterpolator.interpolatePropValue(request, key, value));
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        if (!request.isHandledBy("mongo") || request.getContent() == null) {
            return false;
        }

        var mongoPermission = MongoPermissions.of(request);

        if (mongoPermission != null) {
            return !mongoPermission.getOverrideProps().isEmpty();
        } else {
            return false;
        }
    }
}
