/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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
package org.restheart.mongodb.utils;

import java.util.List;
import java.util.Map;

import org.restheart.exchange.MongoRequest;
import org.restheart.mongodb.MongoService.MongoMount;
import org.restheart.mongodb.MongoServiceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of MongoDB mount resolution.
 * Singleton holding instance state and resolution logic.
 */
public final class MongoMountResolverImpl implements MongoMountResolver {

    // Initialization-on-demand holder idiom
    private static class Holder {
        private static final MongoMountResolverImpl INSTANCE = new MongoMountResolverImpl();
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoMountResolverImpl.class);
    private static final String ROOTPATH = "/";

    public static MongoMountResolverImpl getInstance() {
        return Holder.INSTANCE;
    }

    private final List<MongoMount> mounts;

    /**
     * Creates a MongoMountResolver from a map (for testing or specific configurations).
     * 
     * @param mongoConfig map containing "mongo-mounts" key
     */
    @SuppressWarnings("unchecked")
    MongoMountResolverImpl(final Map<String, Object> mongoConfig) {
        this(mongoConfig != null
            ? (List<Map<String, Object>>) mongoConfig.get("mongo-mounts")
            : null);
    }

    /**
     * Creates a MongoMountResolver using MongoServiceConfiguration singleton.
     * This is the recommended way to create instances.
     */
    private MongoMountResolverImpl() {
        this(MongoServiceConfiguration.get().getMongoMounts());
    }

    /**
     * Creates a MongoMountResolver from a list of mount configurations.
     * 
     * @param mountsList list of mount configuration maps
     */
    private MongoMountResolverImpl(final List<Map<String, Object>> mountsList) {
        // Parse mongo-mounts from configuration map and create MongoMount records
        final var parsedMounts = new java.util.ArrayList<MongoMount>();

        if (mountsList != null) {
            for (final var mountMap : mountsList) {
                try {
                    final String what = String.valueOf(mountMap.get("what"));
                    final String where = String.valueOf(mountMap.get("where"));
                    parsedMounts.add(new MongoMount(what, where));
                } catch (final IllegalArgumentException e) {
                    LOGGER.warn("Invalid mongo-mount configuration: {}", e.getMessage());
                }
            }
        }

        this.mounts = parsedMounts;

        if (this.mounts.isEmpty()) {
            LOGGER.debug("No mongo-mounts configured, defaulting to wildcard at root");
        } else {
            LOGGER.debug("Initialized MongoMountResolver with {} mount(s)", mounts.size());
        }
    }

    /**
     * Resolves a request to determine the MongoDB context (supports parametric mounts).
     */
    @Override
    public ResolvedContext resolve(final MongoRequest request) {
        // extractTenantId returns Optional<String>, pass null if not present
        return resolve(request.getPath(), MongoMountResolver.extractTenantId(request).orElse(null));
    }

    /**
     * Resolves a request path to determine the MongoDB context.
     */
    @Override
    public ResolvedContext resolve(final String requestPath) {
        return resolve(requestPath, null);
    }

    /**
     * Checks if a request path has invalid extra segments beyond what MongoDB expects.
     * 
     * <p>
     * A valid MongoDB path can have:
     * <ul>
     * <li>Root: /</li>
     * <li>Database: /db</li>
     * <li>Collection: /db/collection</li>
     * <li>Document: /db/collection/docid (where docid is handled by MongoDB internally)</li>
     * </ul>
     * 
     * Any path with more segments beyond document level (e.g., /db/coll/docid/extra) is invalid.
     * </p>
     *
     * @param requestPath The HTTP request path to validate
     * @return true if the path has invalid extra segments, false if valid
     */
    private boolean hasExtraPathSegments(final String requestPath) {
        final String path = ensureLeadingSlash(requestPath);

        // Try to match against configured mounts
        for (final var mount : mounts) {
            final String where = mount.uri();

            // Check if request path starts with the mount point
            final boolean matches = where.equals(ROOTPATH) || (path.equals(where) || path.startsWith(where + ROOTPATH));

            if (matches) {
                // Calculate relative path from mount point
                return hasExtraSegmentsInRelativePath(mount, extractRelativePath(path, where));
            }
        }

        // Fallback: check wildcard at root
        return hasExtraSegmentsInWildcardPath(path);
    }

    /**
     * Determines whether configured mongo-mounts include parametric entries.
     *
     * @return true if any mount pattern contains {host[0]}
     */
    private boolean hasParametricMounts() {
        return mounts.stream().anyMatch(m -> MongoMountResolver.isParametricMount(m.uri())
                || MongoMountResolver.isParametricMount(m.resource()));
    }

    private ResolvedContext resolve(final String requestPath, final String tenantId) {
        // Normalize path
        LOGGER.debug("resolve: requestPath='{}', mounts.size()={}", requestPath, mounts.size());

        final String path = ensureLeadingSlash(requestPath);

        // Calculate configuration metadata once for this resolution
        final boolean hasParametricMounts = hasParametricMounts();
        final boolean hasExtraPathSegments = hasExtraPathSegments(requestPath);

        // Try to match against configured mounts
        for (final var mount : mounts) {
            LOGGER.debug("resolve: checking mount - what='{}', where='{}'", mount.resource(), mount.uri());

            // Resolve parametric mount patterns in where/resource using tenantId when present
            final String whereResolved = replaceMountPatternWithTenantId(tenantId, mount.uri());
            final String resourceResolved = replaceMountPatternWithTenantId(tenantId, mount.resource());

            // Treat where patterns ending with "{*}" as prefix matches
            final String wherePrefix = whereResolved.replace("{*}", "");
            final boolean matches = isPathMatchingWherePrefix(path, wherePrefix);

            if (matches) {
                // Calculate relative path from resolved mount point
                // Use the resolved where prefix (without "{*}") so generated mongoResourcePath
                // does not leak parametric placeholders like "{*}".
                return resolveWithMount(resourceResolved, extractRelativePath(path, wherePrefix),
                        hasParametricMounts, hasExtraPathSegments);
            }
        }

        // Fallback: assume wildcard mount at root
        return resolveWildcardAtRoot(path, hasParametricMounts, hasExtraPathSegments);
    }

    /**
     * Replaces tenant ID placeholders in mount patterns.
     */
    private String replaceMountPatternWithTenantId(final String tenantId, final String mountPattern) {
        if (tenantId != null) {
            final String tid = tenantId;
            if (mountPattern.contains(HOST_0) && MongoMountResolver.isValidTenantId(tid)) {
                return mountPattern.replace(HOST_0, tid);
            }
        }
        return mountPattern;
    }

    /**
     * Checks for extra path segments in a wildcard mount at root.
     */
    private boolean isPathMatchingWherePrefix(final String path, final String wherePrefix) {
        return wherePrefix.equals(ROOTPATH)
                || path.equals(wherePrefix)
                || path.startsWith(wherePrefix + (wherePrefix.endsWith(ROOTPATH)
                    ? ""
                    : ROOTPATH));
    }

    /**
     * Extracts relative path from request path based on mount where prefix.
     */
    private String extractRelativePath(final String path, final String wherePrefix) {
        String relativePath;
        if (wherePrefix.equals(ROOTPATH)) {
            relativePath = path.substring(1);
        } else {
            relativePath = path.equals(wherePrefix)
                ? ""
                : path.substring(wherePrefix.length());
            if (relativePath.startsWith(ROOTPATH)) {
                relativePath = relativePath.substring(1);
            }
        }
        return relativePath;
    }

    /**
     * Normalizes request path to ensure it starts with a leading slash.
     */
    private String ensureLeadingSlash(final String requestPath) {
        String path = requestPath == null
            ? ROOTPATH
            : requestPath;
        if (!path.startsWith(ROOTPATH)) {
            path = ROOTPATH + path;
        }
        return path;
    }

    /**
     * Resolves context for a specific mount and relative path.
     */
    private ResolvedContext resolveWithMount(
            final String resource,
            final String relativePath,
            final boolean hasParametricMounts,
            final boolean hasExtraPathSegments) {

        // Wildcard mount
        if ("*".equals(resource)) {
            return resolveWildcardAtRoot(ROOTPATH + relativePath, hasParametricMounts, hasExtraPathSegments);
        }

        // Normalize resource for analysis
        final String normalizedResource = resource.startsWith(ROOTPATH)
            ? resource.substring(1)
            : resource;

        // Special case: resource ends with "/*" or "/{*}" -> database mount with wildcard collections
        if (normalizedResource.endsWith("/{*}")) {
            return resolveCollectionFromPath(relativePath, normalizedResource, hasParametricMounts,
                    hasExtraPathSegments);
        }

        if (normalizedResource.contains(ROOTPATH)) {
            return resolveFixedCollectionMountContext(relativePath, normalizedResource, hasParametricMounts,
                    hasExtraPathSegments);
        }

        return resolveDatabaseMountAsCollectionContext(relativePath, normalizedResource, hasParametricMounts,
                hasExtraPathSegments);

    }

    /**
     * Handles mounts whose resource is a database with wildcard collections (i.e., ends with "/{*}").
     */
    private ResolvedContext resolveCollectionFromPath(
            final String relativePath,
            final String normalizedResource,
            final boolean hasParametricMounts,
            final boolean hasExtraPathSegments) {

        final String database = normalizedResource.substring(0, normalizedResource.length() - 4); // strip "/{*}"
        final String[] parts = relativePath.isEmpty()
            ? new String[0]
            : relativePath.split(ROOTPATH, 2);
        final String collection = parts.length > 0 && !parts[0].isEmpty()
            ? parts[0]
            : null;

        // Build mongoResourcePath as canonical MongoDB path: /database/collection/documentId
        final StringBuilder mongoPath = new StringBuilder();
        mongoPath.append(ROOTPATH).append(database);
        if (collection != null) {
            mongoPath.append(ROOTPATH).append(collection);
            // Include any remaining path segments (e.g., document ID)
            if (parts.length > 1 && !parts[1].isEmpty()) {
                mongoPath.append(ROOTPATH).append(parts[1]);
            }
        }

        return new ResolvedContext(
                database,
                collection,
                false, // Cannot create sibling databases
                true, // Database mount: can create collections
                false, // Do not allow deleting mounted database
                collection != null, // Allow delete collection when inside one
                mongoPath.toString(),
                hasParametricMounts,
                hasExtraPathSegments);
    }

    /**
     * Handles mounts whose resource explicitly contains both database and collection (i.e., resource includes a
     * slash). The name emphasizes this is a fixed collection mount (database+collection) and returns its resolved
     * context.
     */
    private ResolvedContext resolveFixedCollectionMountContext(
            final String relativePath,
            final String normalizedResource,
            final boolean hasParametricMounts,
            final boolean hasExtraPathSegments) {

        // Mount: resource="/db/coll", uri="/api"
        final String[] parts = normalizedResource.split(ROOTPATH, 2);
        final String database = parts.length > 0
            ? parts[0]
            : null;
        final String collection = parts.length > 1
            ? parts[1]
            : null;

        // Build mongoResourcePath as canonical MongoDB path: /database/collection/documentId
        final StringBuilder mongoPath = new StringBuilder();
        if (database != null) {
            mongoPath.append(ROOTPATH).append(database);
            if (collection != null) {
                mongoPath.append(ROOTPATH).append(collection);
                if (!relativePath.isEmpty()) {
                    mongoPath.append(ROOTPATH).append(relativePath);
                }
            }
        }

        return new ResolvedContext(
                database,
                collection,
                false,
                false,
                false,
                false,
                mongoPath.toString(),
                hasParametricMounts,
                hasExtraPathSegments);
    }

    /**
     * The mount's resource is a database (no slash) and resolves an optional collection
     * from the relative path. The name highlights that the mount is a database mount and we produce a collection
     * context.
     */
    private ResolvedContext resolveDatabaseMountAsCollectionContext(
            final String relativePath,
            final String normalizedResource,
            final boolean hasParametricMounts,
            final boolean hasExtraPathSegments) {

        // Mount: resource="/db" or resource="db", uri="/" or other
        final String database = normalizedResource;
        final String[] parts = relativePath.isEmpty()
            ? new String[0]
            : relativePath.split(ROOTPATH, 2);
        final String collection = parts.length > 0 && !parts[0].isEmpty()
            ? parts[0]
            : null;

        // Build mongoResourcePath as canonical MongoDB path: /database/collection/documentId
        // This ensures template resolution works correctly regardless of mount configuration
        final StringBuilder mongoPath = new StringBuilder();
        mongoPath.append(ROOTPATH).append(database);
        if (collection != null) {
            mongoPath.append(ROOTPATH).append(collection);
            // Include any remaining path segments (e.g., document ID)
            if (parts.length > 1 && !parts[1].isEmpty()) {
                mongoPath.append(ROOTPATH).append(parts[1]);
            }
        }

        return new ResolvedContext(
                database,
                collection,
                false,
                collection == null,
                false,
                collection != null,
                mongoPath.toString(),
                hasParametricMounts,
                hasExtraPathSegments);
    }

    /**
     * The mount is a wildcard at root (i.e., resource="*", uri="/") and extracts
     * database/collection from the path
     */
    private ResolvedContext resolveWildcardAtRoot(final String path, final boolean hasParametricMounts,
            final boolean hasExtraPathSegments) {
        final String[] parts = path.substring(1).split(ROOTPATH, 3); // Remove leading / and split
        final String database = parts.length > 0 && !parts[0].isEmpty()
            ? parts[0]
            : null;
        final String collection = parts.length > 1 && !parts[1].isEmpty()
            ? parts[1]
            : null;

        return new ResolvedContext(
                database,
                collection,
                database == null, // Can create databases when at root
                database != null && collection == null, // Can create collections when viewing database
                database != null && (collection == null), // Allow delete database when browsing a db via wildcard
                database != null && collection != null, // Allow delete collection via wildcard when inside collection
                path,
                hasParametricMounts,
                hasExtraPathSegments);
    }

    /**
     * Checks if relative path has extra segments for a specific mount.
     */
    private boolean hasExtraSegmentsInRelativePath(final MongoMount mount, final String relativePath) {
        final String resource = mount.resource();

        if ("*".equals(resource)) {
            // For wildcard mounts, extra segments are anything beyond /db/collection/docid
            final String[] parts = relativePath.isEmpty()
                ? new String[0]
                : relativePath.split(ROOTPATH);
            // parts[0] = database, parts[1] = collection, parts[2] = docid, parts[3]+ = INVALID
            return parts.length > 3;
        } else {
            final String normalizedResource = resource.startsWith(ROOTPATH)
                ? resource.substring(1)
                : resource;

            final String[] parts = getParts(relativePath);
            if (normalizedResource.contains(ROOTPATH)) {
                // Collection mount: /api maps to specific /db/coll
                // Relative path can be empty or document ID, but nothing beyond that
                return parts.length > 1;
            } else {
                // Database mount: /api maps to /db
                // Relative path can be empty (db level), collection name, or docid under collection
                return parts.length > 2;
            }
        }
    }

    private String[] getParts(final String relativePath) {
        return relativePath.isEmpty()
            ? new String[0]
            : relativePath.split(ROOTPATH);
    }

    /**
     * Checks if wildcard path at root has extra segments.
     */
    private boolean hasExtraSegmentsInWildcardPath(final String path) {
        final String[] parts = path.substring(1).split(ROOTPATH); // Remove leading / and split on ALL /
        // Example paths:
        // / -> parts = [""] -> length 1 -> valid
        // /db -> parts = ["db"] -> length 1 -> valid
        // /db/coll -> parts = ["db", "coll"] -> length 2 -> valid
        // /db/coll/docid -> parts = ["db", "coll", "docid"] -> length 3 -> valid
        // /db/coll/docid/extra -> parts = ["db", "coll", "docid", "extra"] -> length 4 -> INVALID
        return parts.length > 3;
    }
}
