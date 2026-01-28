/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2026 SoftInstigate
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
 * Abstract base class representing an Access Control List (ACL) permission with conditions for request authorization.
 * 
 * <p>This class encapsulates the core components of a permission rule in RESTHeart's security framework.
 * Each permission consists of a predicate that evaluates whether a request should be allowed, a set of
 * roles that can execute the request, and a priority for ordering multiple permissions.</p>
 * 
 * <h2>Permission Model</h2>
 * <p>A permission is authorized when:</p>
 * <ol>
 *   <li>The predicate evaluates to {@code true} for the given request</li>
 *   <li>The authenticated user has at least one of the required roles (if specified)</li>
 *   <li>No higher priority permissions have denied the request</li>
 * </ol>
 * 
 * <h2>Priority System</h2>
 * <p>Permissions are evaluated in priority order (lower values = higher priority). This allows
 * for fine-grained control where specific rules can override more general ones.</p>
 * 
 * <h2>Request Attachment</h2>
 * <p>When a permission matches and allows a request, it is attached to the request's exchange
 * for downstream components to access via {@link #MATCHING_ACL_PERMISSION}.</p>
 * 
 * <h2>Example Implementation</h2>
 * <pre>{@code
 * public class CustomPermission extends BaseAclPermission {
 *     public CustomPermission() {
 *         super(
 *             request -> request.getPath().startsWith("/api"),  // predicate
 *             Set.of("user", "admin"),                         // roles
 *             100,                                              // priority
 *             Map.of("description", "API access permission")    // raw data
 *         );
 *     }
 * }
 * }</pre>
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 5.0.0
 * @see BaseAclPermissionTransformer
 */
public abstract class BaseAclPermission {
    private static final Logger LOGGER = LoggerFactory.getLogger(BaseAclPermission.class);

    /**
     * Attachment key for storing the matching ACL permission on the request exchange.
     * 
     * <p>When a permission successfully authorizes a request, it is attached to the exchange
     * using this key. Downstream handlers and services can retrieve the matching permission
     * to access its metadata or implement permission-specific logic.</p>
     */
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
     * Retrieves the ACL permission that authorized the given request.
     * 
     * <p>This static utility method extracts the permission that was attached to the request's
     * exchange during the authorization process. It provides a convenient way for downstream
     * components to access permission metadata without needing direct access to the exchange.</p>
     * 
     * @param request The request to retrieve the permission from
     * @return The BaseAclPermission that authorized this request, or null if no permission
     *         was attached (e.g., if the request was not subject to ACL authorization)
     */
    public static BaseAclPermission of(Request<?> request) {
        return request.getExchange().getAttachment(MATCHING_ACL_PERMISSION);
    }

    /**
     * Evaluates whether this permission allows the given request.
     * 
     * <p>This method applies the permission's predicate to the request. If the predicate
     * evaluation throws any exception, the request is denied (returns false) and the
     * error is logged. This fail-safe behavior ensures that errors in permission logic
     * default to denying access rather than inadvertently allowing it.</p>
     * 
     * <p>Note that this method only evaluates the predicate condition. Role-based
     * authorization is typically handled by the authorization framework before or after
     * calling this method.</p>
     * 
     * @param request The request to evaluate against this permission's predicate
     * @return {@code true} if the predicate allows the request, {@code false} if the
     *         predicate denies it or if an error occurs during evaluation
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
     * Returns the predicate used to evaluate requests.
     * 
     * <p>The predicate encapsulates the permission's authorization logic. It receives
     * a request and returns true if the request should be allowed based on this
     * permission's rules.</p>
     * 
     * @return The predicate for this permission, never null
     */
    public Predicate<Request<?>> gePredicate() {
        return this.predicate;
    }

    /**
     * Sets a new predicate for this permission.
     * 
     * <p>This method is package-private and intended for use by {@link BaseAclPermissionTransformer}
     * to modify permissions by composing additional conditions with the existing predicate.
     * This allows for dynamic enhancement of permissions without modifying their original
     * definition.</p>
     * 
     * <p>Example use case: Adding tenant-specific restrictions to existing permissions
     * based on runtime configuration.</p>
     * 
     * @param predicate The new predicate to set. Should typically be a composition of
     *                  the existing predicate with additional conditions
     * @see BaseAclPermissionTransformer
     */
    void setPredicate(Predicate<Request<?>> predicate) {
        this.predicate = predicate;
    }

    /**
     * Returns the roles authorized by this permission.
     * 
     * <p>Only authenticated users possessing at least one of these roles can be
     * authorized by this permission. An empty set typically means the permission
     * applies regardless of roles (though authentication may still be required).</p>
     * 
     * @return The set of authorized roles, never null but may be empty
     */
    public Set<String> getRoles() {
        return roles;
    }

    /**
     * Returns the priority of this permission.
     * 
     * <p>Priority determines the order in which permissions are evaluated. Lower values
     * indicate higher priority (evaluated first). This allows specific permissions to
     * override more general ones.</p>
     * 
     * <p>Common priority ranges:</p>
     * <ul>
     *   <li>0-99: System-level permissions (highest priority)</li>
     *   <li>100-999: Application-specific permissions</li>
     *   <li>1000+: Default/fallback permissions (lowest priority)</li>
     * </ul>
     * 
     * @return The priority value, where lower numbers = higher priority
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Returns the raw permission data associated with this permission.
     * 
     * <p>The raw data typically contains the original configuration or metadata used
     * to create this permission. This can include the original configuration map,
     * description, or any other contextual information that might be useful for
     * debugging or dynamic behavior.</p>
     * 
     * @return The raw permission data, may be null
     */
    public Object getRaw() {
        return this.raw;
    }

    /**
     * Retrieves the raw permission data from the permission that authorized the given request.
     * 
     * <p>This is a convenience method that combines {@link #of(Request)} and {@link #getRaw()}
     * to directly access the raw data of the permission that authorized a request. Useful
     * for handlers that need to access permission metadata without storing a reference to
     * the permission object.</p>
     * 
     * @param request The request to retrieve the raw permission data from
     * @return The raw data from the authorizing permission, or null if no permission
     *         is attached to the request
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
