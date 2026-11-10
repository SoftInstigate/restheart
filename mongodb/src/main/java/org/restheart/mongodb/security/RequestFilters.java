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

import java.util.ArrayDeque;
import java.util.List;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonValue;
import org.restheart.utils.HttpStatus;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.AclVarsInterpolator;
import org.restheart.security.MongoPermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RegisterPlugin(name = "mongoPermissionFilters",
        description = "enforces the filters according to the mongo.readFilter and mongo.writeFilter ACL permission, and the order of mongo.sort",
        interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH)
public class RequestFilters implements MongoInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestFilters.class);

    private boolean enabled = false;

    @Inject("registry")
    private PluginsRegistry registry;

    @OnInit
    public void init() {
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
        var mongoPermissions = MongoPermissions.of(request);

        // the permission's order, when the client sends none of its own
        if (request.isGet() && request.isCollection() && mongoPermissions.getSort() != null && request.getSortBy() == null) {
            try {
                var sort = resolveSort(request, mongoPermissions.getSort());
                if (sort != null) {
                    request.setSortBy(new ArrayDeque<>(List.of(sort.toJson())));
                }
            } catch (IllegalArgumentException e) {
                response.setInError(HttpStatus.SC_BAD_REQUEST, e.getMessage());
                return;
            }
        }

        if (request.isGet() && mongoPermissions.getReadFilter() != null) {
            LOGGER.debug("read filter: {}", mongoPermissions.getReadFilter());
            addFilter(request, mongoPermissions.getReadFilter());
        } else if ((request.isPatch()
                || request.isPut()
                || request.isPost()
                || request.isDelete())
                && mongoPermissions.getWriteFilter() != null) {
            LOGGER.debug("write filter to add: {}", mongoPermissions.getWriteFilter());
            addFilter(request, mongoPermissions.getWriteFilter());
        } else {
            LOGGER.trace("predicate specifies no filter");
        }
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return enabled
                && request.isHandledBy("mongo")
                && MongoPermissions.of(request) != null;
    }

    /**
     * The order a permission's {@code sort} gives this request, or null when it gives none.
     *
     * <p>A top-level {@code {"$ifvar": [var, alternatives, else]}} picks, by the value of the ACL
     * variable, one of the named alternatives; when the variable is not bound, the optional
     * {@code else} applies. The direction of each field may come from an ACL variable, also with a
     * default ({@code {"$var": [var, default]}}), and must be 1 or -1. When the order does not name
     * {@code _id}, {@code _id} is appended, so that a page is the same page every time.
     *
     * @throws IllegalArgumentException when the request picks an alternative the permission does
     *                                  not declare, or a direction that is not 1 or -1
     */
    static BsonDocument resolveSort(final MongoRequest request, final BsonDocument clause) {
        var chosen = clause;

        if (clause.size() == 1 && clause.containsKey(AclVarsInterpolator.IFVAR)) {
            var args = clause.get(AclVarsInterpolator.IFVAR).asArray();
            var picked = AclVarsInterpolator.interpolatePropValue(request, null, args.get(0).asString().getValue());

            if (!picked.isNull()) {
                var name = picked.isString() ? picked.asString().getValue() : picked.toString();
                var alternatives = args.get(1).asDocument();

                if (!alternatives.containsKey(name) || !alternatives.get(name).isDocument()) {
                    throw new IllegalArgumentException("sort: '" + name + "' is not one of the orders this permission declares: "
                            + String.join(", ", alternatives.keySet()));
                }

                chosen = alternatives.get(name).asDocument();
            } else if (args.size() > 2 && args.get(2).isDocument()) {
                chosen = args.get(2).asDocument();
            } else {
                return null;
            }
        }

        var resolved = new BsonDocument();

        for (var field : chosen.keySet()) {
            var direction = chosen.get(field);

            if (direction.isString() || direction.isDocument()) {
                direction = AclVarsInterpolator.interpolateBson(request, direction);
            }

            resolved.put(field, direction(field, direction));
        }

        if (!resolved.containsKey("_id")) {
            resolved.put("_id", new BsonInt32(1));
        }

        return resolved;
    }

    private static BsonInt32 direction(final String field, final BsonValue value) {
        if (value.isNumber() && (value.asNumber().intValue() == 1 || value.asNumber().intValue() == -1)
                && value.asNumber().doubleValue() == value.asNumber().intValue()) {
            return new BsonInt32(value.asNumber().intValue());
        }

        if (value.isString() && ("1".equals(value.asString().getValue().trim()) || "-1".equals(value.asString().getValue().trim()))) {
            return new BsonInt32(Integer.parseInt(value.asString().getValue().trim()));
        }

        throw new IllegalArgumentException("sort: the direction of " + field + " must be 1 or -1"
                + (value.isNull() ? ", and its variable is not bound: give it, or a default with $var" : ""));
    }

    private void addFilter(final MongoRequest request, final BsonDocument filter) {
        if (filter == null) {
            return;
        }

        var resolvedFilter = AclVarsInterpolator.interpolateFilter(request, filter);

        if (request.getFilter() == null) {
            request.setFilter(new ArrayDeque<>());
        }

        request.getFilter().add(resolvedFilter.toString());
    }
}
