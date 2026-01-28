/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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
package org.restheart.mongodb.utils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.restheart.configuration.Configuration;
import org.restheart.exchange.MongoRequest;

/**
 * Public contract for MongoDB mount resolution.
 * Holds data types and static utility methods.
 *
 * <p>
 * Handles the mapping between HTTP request paths and actual MongoDB resources
 * based on the configured mongo-mounts from MongoServiceConfiguration.
 * </p>
 * 
 * <p>
 * Example mongo-mounts configurations:
 * 
 * <pre>
 * # Mount all databases at root
 * - what: "*"
 *   where: /
 * 
 * # Mount specific database at root
 * - what: "/restheart"
 *   where: /
 * 
 * # Mount specific collection with prefix
 * - what: "/db/coll"
 *   where: /api
 * </pre>
 * </p>
 */
public interface MongoMountResolver {

    static final String HOST_0 = "{host[0]}";

    /**
     * Result of resolving a request path against mongo-mounts.
     * Public DTO used by handlers and templates to access resolved MongoDB context.
     */
    record ResolvedContext(
            String database,
            String collection,
            boolean canCreateDatabases,
            boolean canCreateCollections,
            boolean canDeleteDatabase,
            boolean canDeleteCollection,
            String mongoResourcePath,
            boolean hasParametricMounts,
            boolean hasExtraPathSegments) {
    }

    /**
     * Information about a configured mongo mount.
     * Used by browser to determine where MongoDB is mounted.
     */
    record MountInfo(String what, String where) {
    }

    /**
     * Resolves a request to determine the MongoDB context (supports parametric mounts).
     *
     * @param request the MongoRequest carrying path template parameters
     * @return resolved context with database, collection, and permission information
     */
    ResolvedContext resolve(final MongoRequest request);

    /**
     * Resolves a request path to determine the MongoDB context.
     *
     * @param requestPath the HTTP request path to resolve
     * @return resolved context with database, collection, and permission information
     */
    ResolvedContext resolve(final String requestPath);

    /**
     * Resolves the MongoDB mount prefix from the configuration.
     * it looks for "mongo-mounts" under the "mongo" section
     * In case of multiple mounts, only the first is considered, so it is
     * recommended to have a single mount when using this method.
     * 
     * @param cfg the RestHeart configuration
     * @return the MongoDB mount prefix, or "/" if none configured
     */
    static String resolveMongoPrefixFromConfig(final Configuration cfg) {
        if (cfg == null) {
            return "/";
        }
        // mongo-mounts live under "mongo" section
        final var mongoSection = cfg.getOrDefault("mongo", null);
        if (mongoSection == null) {
            return "/";
        }
        @SuppressWarnings("unchecked")
        final var mounts = (List<Map<String, Object>>) ((Map<?, ?>) mongoSection).get("mongo-mounts");
        if (mounts == null || mounts.isEmpty()) {
            return "/";
        }
        // Get "where" of first mount
        final Object where = mounts.get(0).get("where");
        return where != null
            ? where.toString()
            : "/";
    }

    /**
     * Validates tenant ID format (alphanumeric + hyphens, 1-63 chars, no leading/trailing hyphens).
     * DNS subdomain constraint to support cloud deployments.
     * 
     * @param tenantId the tenant ID to validate
     * @return true if format is valid
     */
    static boolean isValidTenantId(final String tenantId) {
        if (tenantId == null || tenantId.isEmpty() || tenantId.length() > 63) {
            return false;
        }
        return tenantId.matches("^[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9]$|^[a-zA-Z0-9]$");
    }

    /**
     * Extracts tenant identifier from a MongoRequest using parametric hostname syntax.
     * Safely validates the extracted value using {@link #isValidTenantId(String)}.
     *
     * @param request the MongoRequest carrying path template parameters
     * @return Optional tenantId when present and valid
     */
    static Optional<String> extractTenantId(final MongoRequest request) {
        if (request == null) {
            return Optional.empty();
        }

        final Map<String, String> params = request.getPathTemplateParameters();
        final String tenantId = params != null
            ? params.get("host[0]")
            : null;

        if (isValidTenantId(tenantId)) {
            return Optional.of(tenantId);
        }

        return Optional.empty();
    }

    /**
     * Checks if a mount pattern uses parametric syntax (contains {host[0]}).
     * 
     * @param pattern the "where" field value (e.g., "/{host[0]}/{*}")
     * @return true if pattern contains parametric variables
     */
    static boolean isParametricMount(final String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return false;
        }
        return pattern.contains(HOST_0);
    }

}
