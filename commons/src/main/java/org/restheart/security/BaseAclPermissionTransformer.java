package org.restheart.security;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import org.restheart.exchange.Request;

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

/**
 * Provides a mechanism to dynamically modify ACL permissions by composing additional predicates.
 * 
 * <p>This class enables runtime transformation of existing permissions by adding extra conditions
 * to their predicates. This is particularly useful for implementing cross-cutting concerns such as:</p>
 * <ul>
 *   <li>Multi-tenancy restrictions based on user context</li>
 *   <li>Time-based access controls</li>
 *   <li>IP-based restrictions</li>
 *   <li>Dynamic feature flags</li>
 *   <li>Request rate limiting</li>
 * </ul>
 * 
 * <h2>How It Works</h2>
 * <p>The transformer operates in two phases:</p>
 * <ol>
 *   <li><strong>Resolution:</strong> The {@code resolve} predicate identifies which permissions
 *       should be transformed</li>
 *   <li><strong>Transformation:</strong> For matching permissions, the {@code additionalPredicate}
 *       is AND-composed with the existing permission predicate</li>
 * </ol>
 * 
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Create a transformer that adds tenant isolation to all permissions
 * var tenantTransformer = new BaseAclPermissionTransformer(
 *     // Resolve: Apply to all permissions with "tenant-isolated" in raw data
 *     permission -> permission.getRaw() instanceof Map && 
 *                   ((Map<?,?>)permission.getRaw()).containsKey("tenant-isolated"),
 *     
 *     // Additional predicate: Ensure user can only access their tenant's data
 *     (permission, request) -> {
 *         var userTenant = request.getAuthenticatedAccount().getProperty("tenant");
 *         var requestTenant = request.getPathParam("tenant");
 *         return userTenant.equals(requestTenant);
 *     }
 * );
 * 
 * // Apply transformation to a permission
 * tenantTransformer.transform(somePermission);
 * }</pre>
 * 
 * <h2>Thread Safety</h2>
 * <p>Instances of this class are immutable and thread-safe. However, the transformation
 * operation modifies the target permission's predicate, so permissions should not be
 * transformed while they are being evaluated.</p>
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 6.0.0
 * @see BaseAclPermission
 */
public class BaseAclPermissionTransformer {
    /**
     * Predicate that determines which permissions should be transformed.
     */
    Predicate<BaseAclPermission> resolve;
    
    /**
     * The additional predicate to compose with matching permissions.
     */
    BiPredicate<BaseAclPermission, Request<?>> additionalPredicate;

    /**
     * Constructs a new permission transformer with the specified resolution and transformation predicates.
     * 
     * <p>The transformer will apply the {@code additionalPredicate} to any permission for which
     * the {@code resolve} predicate returns true. The additional predicate is AND-composed with
     * the permission's existing predicate, meaning both must return true for the permission to
     * allow a request.</p>
     * 
     * <h3>Resolution Predicate</h3>
     * <p>The resolve predicate should efficiently identify permissions to transform. Common patterns:</p>
     * <ul>
     *   <li>Check permission metadata: {@code p -> p.getRaw() instanceof Map && ...}</li>
     *   <li>Check permission roles: {@code p -> p.getRoles().contains("special")}</li>
     *   <li>Check permission priority: {@code p -> p.getPriority() < 100}</li>
     * </ul>
     * 
     * <h3>Additional Predicate</h3>
     * <p>The additional predicate receives both the permission and request, allowing for
     * context-aware transformations based on:</p>
     * <ul>
     *   <li>Permission metadata combined with request properties</li>
     *   <li>User attributes from the authenticated account</li>
     *   <li>Request headers, path parameters, or body content</li>
     * </ul>
     *
     * @param resolve A predicate that returns true for permissions that should be transformed.
     *                Must not be null
     * @param additionalPredicate The predicate to AND-compose with the permission's existing predicate.
     *                            Must not be null
     * @throws NullPointerException if either parameter is null
     */
    public BaseAclPermissionTransformer(Predicate<BaseAclPermission> resolve, BiPredicate<BaseAclPermission, Request<?>> additionalPredicate) {
        Objects.nonNull(resolve);
        Objects.nonNull(additionalPredicate);

        this.resolve = resolve;
        this.additionalPredicate = additionalPredicate;
    }

    /**
     * Transforms a permission by composing its predicate with the additional predicate if it matches the resolution criteria.
     * 
     * <p>This method first checks if the permission matches the {@code resolve} predicate. If it does,
     * the permission's predicate is replaced with a new predicate that is the logical AND of the
     * existing predicate and the {@code additionalPredicate}.</p>
     * 
     * <p>The transformation is applied in-place, modifying the permission object. After transformation,
     * the permission will only allow requests that satisfy both the original predicate and the
     * additional predicate.</p>
     * 
     * <h3>Example</h3>
     * <pre>{@code
     * // Original permission: allows requests to /api/*
     * // Additional predicate: requires specific header
     * // Result: allows requests to /api/* WITH the required header
     * }</pre>
     * 
     * @param permission The permission to potentially transform. Must not be null
     * @throws NullPointerException if permission is null
     */
    public void transform(BaseAclPermission permission) {
        if (resolve.test(permission)) {
            permission.setPredicate(permission.gePredicate().and(top(additionalPredicate, permission)));
        }
    }

    /**
     * Converts a BiPredicate to a Predicate by partially applying the permission parameter.
     * 
     * <p>This helper method enables the composition of a {@link BiPredicate} (which takes both
     * a permission and a request) with a standard {@link Predicate} (which only takes a request).
     * It creates a closure that captures the permission parameter, resulting in a predicate that
     * can be composed with the permission's existing predicate.</p>
     * 
     * @param bp The BiPredicate to convert
     * @param permission The permission to bind as the first parameter of the BiPredicate
     * @return A Predicate that applies the BiPredicate with the fixed permission parameter
     */
    private static Predicate<Request<?>> top(BiPredicate<BaseAclPermission, Request<?>> bp, BaseAclPermission permission) {
        return r -> bp.test(permission, r);
    }
}
