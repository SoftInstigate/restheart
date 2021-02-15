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
package org.restheart.security.interceptors.mongo;

import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.authorizers.MongoPermissions;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.BsonUtils;

import java.util.Set;
import java.util.stream.Collectors;

import org.bson.BsonDocument;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.InterceptPoint;

@RegisterPlugin(name = "mongoPermissionProtectedProps",
    description = "Forbids writing properties according to the mongo.protectedProps ACL permission",
    interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH,
    enabledByDefault = true,
    priority = 10)
public class ProtectedProps implements MongoInterceptor {

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var protectedProps = MongoPermissions.of(request).getProtectedProps();

        boolean contains;

        if (request.getContent().isDocument()) {
            contains = contains(request.getContent().asDocument(), protectedProps);
        } else if (request.getContent().isArray()) {
            contains = request.getContent().asArray().stream().map(doc -> doc.asDocument()).anyMatch(doc -> contains(doc, protectedProps));
        } else {
            contains = false;
        }

        if (contains) {
            response.setStatusCode(HttpStatus.SC_FORBIDDEN);
            request.setInError(true);
        }
    }

    private boolean contains(BsonDocument doc, Set<String> protectedProps) {
        var ufdoc = BsonUtils.unflatten(doc).asDocument();

        return protectedProps.stream().anyMatch(hiddenProp -> contains(ufdoc, hiddenProp));
    }

    private boolean contains(BsonDocument doc, String protectedProps) {
        // let's check update operators first, since doc can look like:
        // {
        //    <operator1>: { <field1>: <value1>, ... },
        //    <operator2>: { <field2>: <value2>, ... },
        //    ...
        // }

        if (BsonUtils.containsUpdateOperators(doc)) {
            var updateOperators = doc.keySet().stream().filter(k -> k.startsWith("$")).collect(Collectors.toList());

            return updateOperators.stream().anyMatch(uo -> contains(BsonUtils.unflatten(doc.get(uo)).asDocument(), protectedProps));
        }

        if (protectedProps.contains(".")) {
            var first = protectedProps.substring(0, protectedProps.indexOf("."));
            if (first.length() > 0 && doc.containsKey(first) && doc.get(first).isDocument()) {
                return contains(doc.get(first).asDocument(), protectedProps.substring(protectedProps.indexOf(".")+1));
            } else {
                return false;
            }
        } else {
            return protectedProps.length() > 0 && doc.containsKey(protectedProps);
        }
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        if (!request.isHandledBy("mongo") || request.getContent() == null) {
            return false;
        }

        var mongoPermission = MongoPermissions.of(request);

        if (mongoPermission != null) {
            return !mongoPermission.getProtectedProps().isEmpty();
        } else {
            return false;
        }
    }
}
