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
import org.restheart.security.BaseAclPermissionTransformer;
import org.restheart.security.MongoPermissions;
import org.restheart.utils.BsonUtils;

import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.bson.BsonDocument;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.Request;
import org.restheart.plugins.InitPoint;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.InjectPluginsRegistry;

@RegisterPlugin(name = "mongoPermissionForbidProps",
    description = "Forbids writing properties according to the mongo.forbidProps ACL permission",
    initPoint = InitPoint.BEFORE_STARTUP,
    enabledByDefault = true,
    priority = 10)
public class ForbidProps extends BaseAllowInitializer implements Initializer {
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

    // apply the transformation if the permission contains fobbidden properties
    private Predicate<BaseAclPermission> resolve = p -> {
        try {
            return ! MongoPermissions.from(p).getForbidProps().isEmpty();
        } catch(IllegalArgumentException e) {
            return false;
        }
    };

    private BiPredicate<BaseAclPermission, Request<?>> additionalPredicate = (p, _request) -> {
        if (!isHandledByMongoService(_request)) {
            return true;
        }

        var mr = (MongoRequest) _request;

        if (!mr.isWriteDocument() || mr.getContent() == null) {
            return true;
        }

        var mp = MongoPermissions.from(p);

        var forbidProps = mp.getForbidProps();

        boolean containsForbiddenProps;

        if (mr.getContent().isDocument()) {
            containsForbiddenProps = contains(mr.getContent().asDocument(), forbidProps);
        } else if (mr.getContent().isArray()) {
            containsForbiddenProps = mr.getContent().asArray().stream().map(doc -> doc.asDocument()).anyMatch(doc -> contains(doc, forbidProps));
        } else {
            containsForbiddenProps = false;
        }

        return !containsForbiddenProps;
    };

    private boolean contains(BsonDocument doc, Set<String> forbidProps) {
        var ufdoc = BsonUtils.unflatten(doc).asDocument();

        return forbidProps.stream().anyMatch(hiddenProp -> contains(ufdoc, hiddenProp));
    }

    private boolean contains(BsonDocument doc, String forbidProps) {
        // let's check update operators first, since doc can look like:
        // {
        //    <operator1>: { <field1>: <value1>, ... },
        //    <operator2>: { <field2>: <value2>, ... },
        //    ...
        // }

        if (BsonUtils.containsUpdateOperators(doc)) {
            var updateOperators = doc.keySet().stream().filter(k -> k.startsWith("$")).collect(Collectors.toList());

            return updateOperators.stream().anyMatch(uo -> contains(BsonUtils.unflatten(doc.get(uo)).asDocument(), forbidProps));
        }

        if (forbidProps.contains(".")) {
            var first = forbidProps.substring(0, forbidProps.indexOf("."));
            if (first.length() > 0 && doc.containsKey(first) && doc.get(first).isDocument()) {
                return contains(doc.get(first).asDocument(), forbidProps.substring(forbidProps.indexOf(".")+1));
            } else {
                return false;
            }
        } else {
            return forbidProps.length() > 0 && doc.containsKey(forbidProps);
        }
    }
}
