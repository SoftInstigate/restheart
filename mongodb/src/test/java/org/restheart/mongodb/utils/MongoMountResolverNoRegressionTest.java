package org.restheart.mongodb.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.restheart.mongodb.utils.MongoMountResolver.ResolvedContext;

/**
 * No-Regression Tests for Current Mongo Mounts Management
 *
 * Purpose: Ensure that the existing, non-parametric mongo-mounts functionality
 * is NOT broken when implementing parametric mounts support.
 *
 * These tests document the CURRENT behavior that MUST be preserved:
 * - Static mount resolution (no {variables} or {*} placeholders)
 * - Prefix-based path matching
 * - Database/collection capability rules
 * - Default fallback behavior
 *
 * If any of these tests fail after parametric mount implementation,
 * it indicates a regression that must be fixed before release.
 *
 * @since 1.0
 */
@DisplayName("No-Regression Tests: Current Mongo Mounts Management")
class MongoMountResolverNoRegressionTest {

    static final String WHERE = "where";
    static final String MYDB_MYCOLL = "/mydb/mycoll";
    static final String API_USERS = "/api/users";
    static final String DATA = "/data";

    /**
     * Helper to create mongo configuration
     */
    private Map<String, Object> createMongoConfig(List<Map<String, String>> mounts) {
        Map<String, Object> config = new HashMap<>();
        config.put("mongo-mounts", mounts);
        return config;
    }

    @Nested
    @DisplayName("Static Mount Resolution (No Parametric Variables)")
    class StaticMountResolution {

        @Test
        @DisplayName("REGRESSION: Wildcard mount at root should work without changes")
        void testWildcardMountStatic() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "*", WHERE, "/"));
            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));

            ResolvedContext context = resolver.resolve(MYDB_MYCOLL);

            assertEquals("mydb", context.database());
            assertEquals("mycoll", context.collection());
            assertEquals(MYDB_MYCOLL, context.mongoResourcePath());
        }

        @Test
        @DisplayName("REGRESSION: Specific database mount should work without changes")
        void testSpecificDatabaseMountStatic() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "restheart", WHERE, "/"));
            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));

            ResolvedContext context = resolver.resolve("/orders");

            assertEquals("restheart", context.database());
            assertEquals("orders", context.collection());
            // Note: mongoResourcePath may have double slash due to path stripping behavior
            assertEquals("//orders", context.mongoResourcePath());
        }

        @Test
        @DisplayName("REGRESSION: Prefixed mount should work without changes")
        void testPrefixedMountStatic() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "mydb", WHERE, "/api"));
            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));

            ResolvedContext context = resolver.resolve(API_USERS);

            assertEquals("mydb", context.database());
            assertEquals("users", context.collection());
            assertEquals(API_USERS, context.mongoResourcePath());
        }

        @Test
        @DisplayName("REGRESSION: Collection-level mount should work without changes")
        void testCollectionMountStatic() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "db/coll", WHERE, DATA));
            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));

            ResolvedContext context = resolver.resolve(DATA);

            assertEquals("db", context.database());
            assertEquals("coll", context.collection());
        }
    }

    @Nested
    @DisplayName("Prefix-Based Path Matching (No Regex or Template Matching)")
    class PrefixMatching {

        @Test
        @DisplayName("REGRESSION: Simple prefix matching should work")
        void testSimplePrefixMatching() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "db", WHERE, "/api"));
            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));

            // Exact match
            ResolvedContext exact = resolver.resolve("/api");
            assertEquals("db", exact.database());

            // Prefix + more path
            ResolvedContext withPath = resolver.resolve("/api/collection");
            assertEquals("db", withPath.database());
            assertEquals("collection", withPath.collection());

            // Prefix + nested path
            ResolvedContext nested = resolver.resolve("/api/a/b/c");
            assertEquals("db", nested.database());
        }

        @Test
        @DisplayName("REGRESSION: Similar prefixes should not match (exact prefix matching)")
        void testPrefixExactness() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "db", WHERE, "/api"));
            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));

            // /apiv2 should NOT match /api mount
            ResolvedContext context = resolver.resolve("/apiv2/data");
            assertEquals("apiv2", context.database());
            assertNotEquals("db", context.database());
        }

        @Test
        @DisplayName("REGRESSION: Nested prefixes should be handled correctly")
        void testNestedPrefixes() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "db", WHERE, "/api/v1/data"));
            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));

            ResolvedContext context = resolver.resolve("/api/v1/data/mydb");
            assertEquals("db", context.database());
            assertEquals("mydb", context.collection());
        }
    }

    @Nested
    @DisplayName("Permission & Capability Rules (Preserved)")
    class PermissionRules {


        @Test
        @DisplayName("REGRESSION: Root level should allow database creation")
        void testRootCanCreateDatabases() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "*", WHERE, "/"));
            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));

            ResolvedContext root = resolver.resolve("/");
            assertTrue(root.canCreateDatabases(), "Root should allow DB creation");
        }

        @Test
        @DisplayName("REGRESSION: Database level should allow collection creation")
        void testDatabaseCanCreateCollections() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "*", WHERE, "/"));
            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));

            ResolvedContext db = resolver.resolve("/mydb");
            assertTrue(db.canCreateCollections(), "Database level should allow collection creation");
        }

        @Test
        @DisplayName("REGRESSION: Collection level should NOT allow database creation")
        void testCollectionCannotCreateDatabases() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "*", WHERE, "/"));
            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));

            ResolvedContext coll = resolver.resolve(MYDB_MYCOLL);
            assertFalse(coll.canCreateDatabases(), "Collection level should NOT allow DB creation");
        }

        @Test
        @DisplayName("REGRESSION: Collection level should allow collection deletion")
        void testCollectionCanDelete() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "*", WHERE, "/"));
            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));

            ResolvedContext coll = resolver.resolve(MYDB_MYCOLL);
            assertTrue(coll.canDeleteCollection(), "Collection level should allow deletion");
        }

        @Test
        @DisplayName("REGRESSION: Specific mounted database should restrict operations")
        void testSpecificMountRestrictions() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "prod", WHERE, "/api"));
            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));

            ResolvedContext coll = resolver.resolve(API_USERS);
            assertFalse(coll.canCreateDatabases(), "Cannot create DBs when DB is mounted");
            assertFalse(coll.canDeleteDatabase(), "Cannot delete mounted DB from collection view");
        }
    }

    @Nested
    @DisplayName("Multiple Mounts Behavior (Preserved)")
    class MultipleMountsBehavior {

        @Test
        @DisplayName("REGRESSION: Multiple mounts should work in order")
        void testMultipleMountsOrder() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "db1", WHERE, "/api"),
                    Map.of("what", "db2", WHERE, "/api") // Won't be reached
            );
            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));

            ResolvedContext context = resolver.resolve("/api/data");
            assertEquals("db1", context.database(), "First matching mount should win");
        }

        @Test
        @DisplayName("REGRESSION: Multiple distinct mounts should each work")
        void testMultipleDistinctMounts() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "db1", WHERE, "/api"),
                    Map.of("what", "db2", WHERE, "/admin"),
                    Map.of("what", "*", WHERE, "/public"));
            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));

            assertEquals("db1", resolver.resolve("/api/data").database());
            assertEquals("db2", resolver.resolve("/admin/users").database());
            assertEquals("mydb", resolver.resolve("/public/mydb/coll").database());
        }
    }

    @Nested
    @DisplayName("Default Behavior (No Parametric Variables)")
    class DefaultBehavior {

        @Test
        @DisplayName("REGRESSION: Empty mounts list should use default wildcard")
        void testEmptyMountsDefault() {
            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(List.of()));

            ResolvedContext context = resolver.resolve(MYDB_MYCOLL);
            assertNotNull(context);
            assertEquals("mydb", context.database());
        }

        @Test
        @DisplayName("REGRESSION: Null mounts should use default wildcard")
        void testNullMountsDefault() {
            MongoMountResolver resolver = new MongoMountResolverImpl((Map<String, Object>) null);

            ResolvedContext context = resolver.resolve(MYDB_MYCOLL);
            assertNotNull(context);
            assertEquals("mydb", context.database());
        }

        @Test
        @DisplayName("REGRESSION: Non-matching path should use fallback resolution")
        void testNonMatchingPathFallback() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "db", WHERE, "/api"));
            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));

            // Path that doesn't match /api should use fallback
            ResolvedContext context = resolver.resolve("/other/path");
            assertEquals("other", context.database());
            assertEquals("path", context.collection());
        }
    }

    @Nested
    @DisplayName("Edge Cases - Backward Compatibility")
    class EdgeCasesBackwardCompat {

        @Test
        @DisplayName("REGRESSION: Paths with trailing slash should be normalized")
        void testTrailingSlashNormalization() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "*", WHERE, "/"));
            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));

            ResolvedContext context = resolver.resolve("/mydb/coll/");
            assertEquals("mydb", context.database());
            assertEquals("coll", context.collection());
        }

        @Test
        @DisplayName("REGRESSION: Root path should be handled correctly")
        void testRootPathHandling() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "*", WHERE, "/"));
            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));

            ResolvedContext root = resolver.resolve("/");
            assertEquals("/", root.mongoResourcePath());
            assertNull(root.database());
            assertNull(root.collection());
        }

        @Test
        @DisplayName("REGRESSION: Null path should be handled as root")
        void testNullPathHandling() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "*", WHERE, "/"));
            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));

            ResolvedContext context = resolver.resolve((String) null);
            assertNotNull(context);
        }

        @Test
        @DisplayName("REGRESSION: Complex paths should be preserved")
        void testComplexPathPreservation() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "*", WHERE, "/api/v1"));
            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));

            ResolvedContext context = resolver.resolve("/api/v1/db/coll");
            assertEquals("/db/coll", context.mongoResourcePath());
        }
    }

    @Nested
    @DisplayName("Real-World Scenarios - Backward Compatibility")
    class RealWorldBackwardCompat {

        @Test
        @DisplayName("REGRESSION: Multi-tenant setup (static prefixes)")
        void testMultiTenantStaticSetup() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "acme_db", WHERE, "/acme"),
                    Map.of("what", "widgets_db", WHERE, "/widgets"),
                    Map.of("what", "shared", WHERE, "/shared"));
            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));

            assertEquals("acme_db", resolver.resolve("/acme/data").database());
            assertEquals("widgets_db", resolver.resolve("/widgets/items").database());
            assertEquals("shared", resolver.resolve("/shared/config").database());
        }

        @Test
        @DisplayName("REGRESSION: API versioning (static prefixes)")
        void testApiVersioningStaticSetup() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "api_v1", WHERE, "/api/v1"),
                    Map.of("what", "api_v2", WHERE, "/api/v2"),
                    Map.of("what", "api_v3", WHERE, "/api/v3"));
            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));

            assertEquals("api_v1", resolver.resolve("/api/v1/data").database());
            assertEquals("api_v2", resolver.resolve("/api/v2/data").database());
            assertEquals("api_v3", resolver.resolve("/api/v3/data").database());
        }

        @Test
        @DisplayName("REGRESSION: Dev/Prod separation (static setup)")
        void testDevProdSeparationStaticSetup() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "*", WHERE, "/dev"),
                    Map.of("what", "production", WHERE, "/prod"));
            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));

            assertEquals("testdb", resolver.resolve("/dev/testdb/coll").database());
            assertEquals("production", resolver.resolve("/prod/data").database());
        }

        @Test
        @DisplayName("REGRESSION: Legacy setup (root mount)")
        void testLegacyRootMountSetup() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "*", WHERE, "/"));
            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));

            assertEquals("legacy_db", resolver.resolve("/legacy_db/legacy_coll").database());
            assertTrue(resolver.resolve("/").canCreateDatabases());
        }
    }

    @Nested
    @DisplayName("Performance & Scale - Backward Compatibility")
    class PerformanceBackwardCompat {

        @Test
        @DisplayName("REGRESSION: Many mounts should resolve correctly (first match wins)")
        void testManyMountsPerformance() {
            List<Map<String, String>> mounts = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                mounts.add(Map.of(
                        "what", "db" + i,
                        WHERE, "/api/v" + i));
            }

            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));

            // First mount should be found quickly
            ResolvedContext context = resolver.resolve("/api/v0/data");
            assertEquals("db0", context.database());

            // Non-matching should fall back
            ResolvedContext fallback = resolver.resolve("/other");
            assertEquals("other", fallback.database());
        }

        @Test
        @DisplayName("REGRESSION: Deep nesting should be handled")
        void testDeepNestingPerformance() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "*", WHERE, "/a/b/c/d/e/f/g"));
            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));

            ResolvedContext context = resolver.resolve("/a/b/c/d/e/f/g/db1/db2/db3");
            assertNotNull(context);
            assertEquals("db1", context.database());
        }

        @Test
        @DisplayName("REGRESSION: Long database names should be handled")
        void testLongDatabaseNames() {
            String longDbName = "a".repeat(256);
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", longDbName, WHERE, DATA));
            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));

            ResolvedContext context = resolver.resolve("/data/coll");
            assertEquals(longDbName, context.database());
        }
    }

    @Nested
    @DisplayName("Configuration Consistency - No Breaking Changes")
    class ConfigurationConsistency {

        private static final String MYDB_COLL = "/mydb/coll";

        @Test
        @DisplayName("REGRESSION: Mount configuration keys should be exactly 'what' and 'where'")
        void testMountConfigurationKeys() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "db", WHERE, "/api"));
            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));

            ResolvedContext context = resolver.resolve("/api/coll");
            assertEquals("db", context.database());
        }

        @Test
        @DisplayName("REGRESSION: Mount resolution should return consistent context")
        void testContextConsistency() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "*", WHERE, "/"));
            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));

            ResolvedContext context1 = resolver.resolve(MYDB_COLL);
            ResolvedContext context2 = resolver.resolve(MYDB_COLL);

            // Same path should resolve to same database/collection
            assertEquals(context1.database(), context2.database());
            assertEquals(context1.collection(), context2.collection());
        }

        @Test
        @DisplayName("REGRESSION: ResolvedContext fields should be non-null when appropriate")
        void testContextNullHandling() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "*", WHERE, "/"));
            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));

            ResolvedContext root = resolver.resolve("/");
            assertNull(root.database());
            assertNull(root.collection());
            assertNotNull(root.mongoResourcePath());

            ResolvedContext db = resolver.resolve("/mydb");
            assertNotNull(db.database());
            assertNull(db.collection());

            ResolvedContext coll = resolver.resolve(MYDB_COLL);
            assertNotNull(coll.database());
            assertNotNull(coll.collection());
        }
    }
}
