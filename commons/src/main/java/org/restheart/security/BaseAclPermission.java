/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2025 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.security;

import java.util.Set;
import java.util.function.Predicate;

import org.restheart.exchange.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.util.AttachmentKey;

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
     * @param request
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
     * @param request
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
