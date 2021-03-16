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
import java.util.function.Predicate;

import org.restheart.exchange.Request;
import io.undertow.util.AttachmentKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ACL Permission that specifies the conditions that are necessary to perform
 * the request
 *
 * The request is authorized if BaseAclPermission.allow() returns true
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public abstract class BaseAclPermission {
    private static final Logger LOGGER = LoggerFactory.getLogger(BaseAclPermission.class);

    public static final AttachmentKey<BaseAclPermission> MATCHING_ACL_PERMISSION = AttachmentKey
            .create(BaseAclPermission.class);

    private Predicate<Request<?>> predicate;
    private final Set<String> roles;
    private final int priority;
    private final Object raw;

    public BaseAclPermission(Predicate<Request<?>> predicate, Set<String> roles, int priority, Object raw) {
        this.predicate = predicate;
        this.roles = roles;
        this.priority = priority;
        this.raw = raw;
    }

    /**
     *
     * @param exchange
     * @return the acl predicate associated with this request
     */
    public static BaseAclPermission of(Request<?> request) {
        return request.getExchange().getAttachment(MATCHING_ACL_PERMISSION);
    }

    /**
     *
     * @param request
     * @return true if this acl authorizes the request
     */
    public boolean allow(Request<?> request) {
        try {
            return this.predicate.test(request);
        } catch(Throwable t) {
            LOGGER.error("Error testing predicate {}", t);
            return false;
        }
    }

    /**
     *
     * @return the predicate
     */
    public Predicate<Request<?>> gePredicate() {
        return this.predicate;
    }

    /**
     *
     * sets the predicate. A permission can be programmatically extended with
     * additional condition predicates using PermissionTransformer
     */
    void setPredicate(Predicate<Request<?>> predicate) {
        this.predicate = predicate;
    }

    /**
     * @return the roles
     */
    public Set<String> getRoles() {
        return roles;
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
        var permission = BaseAclPermission.of(request);
        if (permission != null) {
            return permission.getRaw();
        } else {
            return null;
        }
    }
}
