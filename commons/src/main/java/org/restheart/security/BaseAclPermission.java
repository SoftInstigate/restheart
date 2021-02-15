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
package org.restheart.security;

import java.util.Set;

import org.restheart.exchange.Request;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

/**
 * ACL Permission that specifies the conditions that are necessary to perform
 * the request
 *
 * The request is authorized if AclPermission.resolve() to true
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public abstract class BaseAclPermission {
    public static final AttachmentKey<BaseAclPermission> MATCHING_ACL_PERMISSION = AttachmentKey.create(BaseAclPermission.class);

    private final String predicate;
    private final Set<String> roles;
    private final int priority;
    private final Object raw;

    public BaseAclPermission(String predicate, Set<String> roles, int priority, Object raw) {
        this.predicate = predicate;
        this.roles = roles;
        this.priority = priority;
        this.raw = raw;
    }

    /**
     * @return the roles
     */
    public Set<String> getRoles() {
        return roles;
    }

    /**
     * @return the predicate
     */
    public String getPredicate() {
        return predicate;
    }

    /**
     * lesser is higher priority
     *
     * @return the priority
     */
    public int getPriority() {
        return priority;
    }

    /**
     *
     * @param exchange
     * @return the acl predicate associated with this request
     */
    public static BaseAclPermission from(final HttpServerExchange exchange) {
        return exchange.getAttachment(MATCHING_ACL_PERMISSION);
    }

    /**
     *
     * @return the raw permission data
     */
    public Object getRaw() {
        return this.raw;
    }

    /**
     *
     * @return the raw permission data bound to the request
     */
    public static Object getRaw(Request<?> request) {
        var permission = BaseAclPermission.from(request.getExchange());
        if (permission != null) {
            return permission.getRaw();
        } else {
            return null;
        }
    }

    /**
     *
     * @param exchange
     * @return true if this acl authorizes the request
     */
    public abstract boolean allow(final HttpServerExchange exchange);
}
