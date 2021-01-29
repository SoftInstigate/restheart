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

import java.util.ArrayDeque;
import org.bson.BsonDocument;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.InjectPluginsRegistry;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.authorizers.AclPermission;
import org.restheart.security.authorizers.AclPermissionsVarsInterpolator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RegisterPlugin(name = "mongoPermissionFilters",
        description = "enforces the filters according to the mongo.readFilter and mongo.writeFilter ACL permission",
        interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH)
public class RequestFilters implements MongoInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestFilters.class);

    private boolean enabled = false;

    @InjectPluginsRegistry
    public void init(PluginsRegistry registry) {
        var __maa = registry.getAuthorizers()
                .stream()
                .filter(a -> "mongoAclAuthorizer".equals(a.getName())
                    || "fileAclAuthorizer".equals(a.getName()))
                .findFirst();

        if (__maa == null || !__maa.isPresent()) {
            enabled = false;
        } else if (__maa.get().isEnabled()) {
            enabled = true;
        } else {
            enabled = false;
        }
    }

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var exchange = request.getExchange();
        var predicate = AclPermission.from(exchange);

        if (request.isGet()
                && predicate.getMongoPermissions().getReadFilter() != null) {
            LOGGER.debug("read filter: {}", predicate.getMongoPermissions().getReadFilter());
            addFilter(request, predicate.getMongoPermissions().getReadFilter());
        } else if ((request.isPatch()
                || request.isPut()
                || request.isPost()
                || request.isDelete())
                && predicate.getMongoPermissions().getWriteFilter() != null) {
            LOGGER.debug("write filter to add: {}", predicate.getMongoPermissions().getWriteFilter());
            addFilter(request, predicate.getMongoPermissions().getWriteFilter());
        } else {
            LOGGER.trace("predicate specifies no filter");
        }
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        var permission = AclPermission.from(request.getExchange());
        return enabled
                && request.isHandledBy("mongo")
                && permission != null
                && permission.getMongoPermissions() != null;
    }

    private void addFilter(final MongoRequest request, final BsonDocument filter) {
        if (filter == null) {
            return;
        }

        var resolvedFilter = AclPermissionsVarsInterpolator.interpolateBson(request, filter);

        if (request.getFilter() == null) {
            request.setFilter(new ArrayDeque<>());
        }

        request.getFilter().add(resolvedFilter.toString());
    }
}
