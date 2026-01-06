package org.restheart.mongodb.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * Unit tests for MongoMountResolver
 * Documents the behavior of mount resolution with various configurations
 */
class MongoMountResolverImplTest {

    static final String CANNOT_DELETE_DATABASE_FROM_COLLECTION_VIEW = "Cannot delete database from collection view";
    static final String MYCOLLECTION = "mycollection";
    static final String MYDB_MYCOLLECTION = "/mydb/mycollection";
    static final String RESTHEART = "restheart";
    static final String WHERE = "where";
    static final String ORDERS = "orders";
    static final String DATA = "/data";
    static final String COLLECTION = "collection";
    static final String USERS = "users";
    static final String API_ORDERS = "/api/orders";

    /**
     * Helper method to create mongo configuration map
     */
    private Map<String, Object> createMongoConfig(List<Map<String, String>> mounts) {
        Map<String, Object> config = new HashMap<>();
        config.put("mongo-mounts", mounts);
        return config;
    }

    @Nested
    @DisplayName("Wildcard Mount at Root")
    class WildcardAtRoot {

        @Test
        @DisplayName("Should allow all database operations when wildcard is at root")
        void testWildcardAtRoot() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "*", WHERE, "/"));

            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));
            ResolvedContext context = resolver.resolve(MYDB_MYCOLLECTION);

            assertNotNull(context);
            assertEquals("mydb", context.database());
            assertEquals(MYCOLLECTION, context.collection());
            assertFalse(context.canCreateDatabases(), "Cannot create databases at collection level");
            assertFalse(context.canCreateCollections(), "Cannot create collections at collection level");
            assertFalse(context.canDeleteDatabase(), CANNOT_DELETE_DATABASE_FROM_COLLECTION_VIEW);
            assertTrue(context.canDeleteCollection(), "Can delete collection");
            assertEquals(MYDB_MYCOLLECTION, context.mongoResourcePath());
        }

        @Test
        @DisplayName("Should resolve database level path correctly")
        void testWildcardAtRootDatabaseLevel() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "*", WHERE, "/"));

            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));
            ResolvedContext context = resolver.resolve("/mydb");

            assertNotNull(context);
            assertEquals("mydb", context.database());
            assertNull(context.collection());
            assertFalse(context.canCreateDatabases(), "At database level, cannot create sibling databases");
            assertTrue(context.canCreateCollections(), "Can create collections inside this database");
            assertTrue(context.canDeleteDatabase(), "Can delete this database");
            assertFalse(context.canDeleteCollection(), "No collection to delete at database level");
            assertEquals("/mydb", context.mongoResourcePath());
        }

        @Test
        @DisplayName("Should allow database creation at root level")
        void testWildcardAtRootLevel() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "*", WHERE, "/"));

            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));
            ResolvedContext context = resolver.resolve("/");

            assertNotNull(context);
            assertNull(context.database(), "At root, no database selected");
            assertNull(context.collection(), "At root, no collection selected");
            assertTrue(context.canCreateDatabases(), "Can create databases at root level");
            assertFalse(context.canCreateCollections(), "Cannot create collections without a database");
            assertFalse(context.canDeleteDatabase(), "No database to delete at root");
            assertFalse(context.canDeleteCollection(), "No collection to delete at root");
            assertEquals("/", context.mongoResourcePath());
        }
    }

    @Nested
    @DisplayName("Specific Database Mounted at Root")
    class SpecificDatabaseAtRoot {

        @Test
        @DisplayName("Should map root to specific database and only allow collection operations")
        void testDatabaseMountedAtRoot() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", RESTHEART, WHERE, "/"));

            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));

            // Path "/" matches the mount exactly
            ResolvedContext rootContext = resolver.resolve("/");
            assertNotNull(rootContext);
            assertEquals(RESTHEART, rootContext.database(), "Database should be 'restheart'");
            assertNull(rootContext.collection(), "At root, no collection");
            assertFalse(rootContext.canCreateDatabases(), "Cannot create databases when one is mounted at root");
            assertTrue(rootContext.canCreateCollections(), "Can create collections in mounted database");
            assertFalse(rootContext.canDeleteDatabase(), "Cannot delete the mounted database");
            assertFalse(rootContext.canDeleteCollection(), "No collection to delete");
            assertEquals("/", rootContext.mongoResourcePath());

            // Path "/orders" now properly matches mount at "/" after fix
            ResolvedContext ordersContext = resolver.resolve("/orders");
            assertNotNull(ordersContext);
            assertEquals(RESTHEART, ordersContext.database(), "Database should be 'restheart'");
            assertEquals(ORDERS, ordersContext.collection(), "First segment is collection name");
            assertFalse(ordersContext.canCreateDatabases(), "Cannot create databases");
            assertFalse(ordersContext.canCreateCollections(), "At collection level, cannot create collections");
            assertFalse(ordersContext.canDeleteDatabase(), "Cannot delete mounted database");
            assertTrue(ordersContext.canDeleteCollection(), "Can delete this collection");
        }
    }

    @Nested
    @DisplayName("Specific Database Mounted at Prefix")
    class SpecificDatabaseAtPrefix {

        @Test
        @DisplayName("Should map prefix path to specific database")
        void testDatabaseMountedAtPrefix() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "mydb", WHERE, "/api"));

            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));
            ResolvedContext context = resolver.resolve(API_ORDERS);

            assertNotNull(context);
            assertEquals("mydb", context.database());
            assertEquals(ORDERS, context.collection());
            assertFalse(context.canCreateDatabases(), "Cannot create databases when specific one is mounted");
            assertFalse(context.canCreateCollections(), "Cannot create collections - viewing specific collection");
            assertFalse(context.canDeleteDatabase(), CANNOT_DELETE_DATABASE_FROM_COLLECTION_VIEW);
            assertTrue(context.canDeleteCollection(), "Can delete collections");
            assertEquals(API_ORDERS, context.mongoResourcePath());
        }

        @Test
        @DisplayName("Should handle database level at prefix correctly")
        void testDatabaseMountedAtPrefixDatabaseLevel() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "mydb", WHERE, "/api"));

            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));
            ResolvedContext context = resolver.resolve("/api");

            assertNotNull(context);
            assertEquals("mydb", context.database());
            assertNull(context.collection());
            assertFalse(context.canCreateDatabases());
            assertTrue(context.canCreateCollections());
            assertFalse(context.canDeleteDatabase(), "Cannot delete mounted database");
            assertFalse(context.canDeleteCollection());
            assertEquals("/api", context.mongoResourcePath());
        }
    }

    @Nested
    @DisplayName("Specific Collection Mounted")
    class SpecificCollectionMounted {

        @Test
        @DisplayName("Should map path directly to specific collection")
        void testCollectionMountedAtPrefix() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "mydb/mycollection", WHERE, DATA));

            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));
            ResolvedContext context = resolver.resolve(DATA);

            assertNotNull(context);
            assertEquals("mydb", context.database());
            assertEquals(MYCOLLECTION, context.collection());
            assertFalse(context.canCreateDatabases(), "Cannot create databases");
            assertFalse(context.canCreateCollections(), "Cannot create collections when specific one is mounted");
            assertFalse(context.canDeleteDatabase(), "Cannot delete database");
            assertFalse(context.canDeleteCollection(), "Cannot delete directly mounted collection");
            assertEquals(DATA, context.mongoResourcePath());
        }
    }

    @Nested
    @DisplayName("Multiple Mounts")
    class MultipleMounts {

        @Test
        @DisplayName("Should resolve correctly with multiple mount points")
        void testMultipleMounts() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "prod", WHERE, "/api"),
                    Map.of("what", "test", WHERE, "/test"),
                    Map.of("what", "*", WHERE, "/admin"));

            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));

            // Test /api prefix
            ResolvedContext apiContext = resolver.resolve("/api/" + ORDERS);
            assertEquals("prod", apiContext.database());
            assertEquals(ORDERS, apiContext.collection());
            assertFalse(apiContext.canCreateDatabases());

            // Test /test prefix
            ResolvedContext testContext = resolver.resolve("/test/" + USERS);
            assertEquals("test", testContext.database());
            assertEquals(USERS, testContext.collection());
            assertFalse(testContext.canCreateDatabases());

            // Test /admin prefix with wildcard
            ResolvedContext adminContext = resolver.resolve("/admin/mydb/mycoll");
            assertEquals("mydb", adminContext.database());
            assertEquals("mycoll", adminContext.collection());
            assertFalse(adminContext.canCreateDatabases(),
                    "At collection level, cannot create databases even with wildcard");
        }

        @Test
        @DisplayName("Should use first matching mount when multiple could apply")
        void testFirstMatchWins() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "db1", WHERE, DATA),
                    Map.of("what", "db2", WHERE, DATA) // This won't be used
            );

            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));
            ResolvedContext context = resolver.resolve(DATA + "/" + COLLECTION);

            assertEquals("db1", context.database(), "Should use first matching mount");
            assertEquals(COLLECTION, context.collection());
        }
    }

    @Nested
    @DisplayName("No Mounts or Empty Mounts")
    class NoMounts {

        @Test
        @DisplayName("Should return default wildcard context when no mounts configured")
        void testNoMounts() {
            List<Map<String, String>> mounts = new ArrayList<>();
            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));

            ResolvedContext context = resolver.resolve(MYDB_MYCOLLECTION);

            assertNotNull(context, "Implementation provides default wildcard mount");
            assertEquals("mydb", context.database());
            assertEquals(MYCOLLECTION, context.collection());
        }

        @Test
        @DisplayName("Should return default wildcard context for null mounts list")
        void testNullMounts() {
            MongoMountResolver resolver = new MongoMountResolverImpl((Map<String, Object>) null);

            ResolvedContext context = resolver.resolve(MYDB_MYCOLLECTION);

            assertNotNull(context, "Implementation provides default wildcard mount");
            assertEquals("mydb", context.database());
            assertEquals(MYCOLLECTION, context.collection());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle paths with trailing slash")
        void testTrailingSlash() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "*", WHERE, "/"));

            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));
            ResolvedContext context = resolver.resolve("/mydb/mycollection/");

            assertNotNull(context);
            assertEquals("mydb", context.database());
            assertEquals(MYCOLLECTION, context.collection());
        }

        @Test
        @DisplayName("Should handle nested paths correctly")
        void testNestedPaths() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "*", WHERE, "/api/v1"));

            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));
            ResolvedContext context = resolver.resolve("/api/v1/mydb/mycollection");

            assertNotNull(context);
            assertEquals("mydb", context.database());
            assertEquals(MYCOLLECTION, context.collection());
            assertEquals(MYDB_MYCOLLECTION, context.mongoResourcePath());
        }

        @Test
        @DisplayName("Should use wildcard fallback for paths that don't match any mount")
        void testNonMatchingPath() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "mydb", WHERE, "/api"));

            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));
            ResolvedContext context = resolver.resolve("/other/path");

            assertNotNull(context, "Should return wildcard fallback context");
            assertEquals("other", context.database());
            assertEquals("path", context.collection());
            assertFalse(context.canCreateDatabases(), "At collection level, cannot create databases");
            assertFalse(context.canCreateCollections(), "At collection level, cannot create collections");
            assertFalse(context.canDeleteDatabase(), CANNOT_DELETE_DATABASE_FROM_COLLECTION_VIEW);
            assertTrue(context.canDeleteCollection());
        }

        @Test
        @DisplayName("Should handle mount 'where' without leading slash")
        void testMountWithoutLeadingSlash() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "mydb", WHERE, "api"));

            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));
            ResolvedContext context = resolver.resolve(API_ORDERS);

            assertNotNull(context, "Should handle mounts without leading slash");
            // Without slash normalization, mount doesn't match so fallback is used
            assertEquals("api", context.database(), "Uses fallback, first segment is database");
            assertEquals(ORDERS, context.collection());
        }
    }

    @Nested
    @DisplayName("Real-World Scenarios")
    class RealWorldScenarios {

        @Test
        @DisplayName("Multi-tenant setup: Each tenant gets their own prefix")
        void testMultiTenantSetup() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "tenant1_db", WHERE, "/tenant1"),
                    Map.of("what", "tenant2_db", WHERE, "/tenant2"),
                    Map.of("what", "shared_db", WHERE, "/shared"));

            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));

            ResolvedContext tenant1 = resolver.resolve("/tenant1/users");
            assertEquals("tenant1_db", tenant1.database());
            assertEquals(USERS, tenant1.collection());

            ResolvedContext tenant2 = resolver.resolve("/tenant2/users");
            assertEquals("tenant2_db", tenant2.database());
            assertEquals(USERS, tenant2.collection());
            ResolvedContext shared = resolver.resolve("/shared/config");
            assertEquals("shared_db", shared.database());
            assertEquals("config", shared.collection());
        }

        @Test
        @DisplayName("API versioning: Different database per API version")
        void testApiVersioning() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "api_v1", WHERE, "/api/v1"),
                    Map.of("what", "api_v2", WHERE, "/api/v2"));

            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));

            ResolvedContext v1 = resolver.resolve("/api/v1/orders");
            assertEquals("api_v1", v1.database());
            assertEquals("/api/v1/orders", v1.mongoResourcePath(), "Path preserved with slashes");

            ResolvedContext v2 = resolver.resolve("/api/v2/orders");
            assertEquals("api_v2", v2.database());
            assertEquals("/api/v2/orders", v2.mongoResourcePath(), "Path preserved with slashes");
        }

        @Test
        @DisplayName("Development setup: Full access at /dev, restricted at /prod")
        void testDevProdSetup() {
            List<Map<String, String>> mounts = List.of(
                    Map.of("what", "*", WHERE, "/dev"),
                    Map.of("what", "production", WHERE, "/prod"));

            MongoMountResolver resolver = new MongoMountResolverImpl(createMongoConfig(mounts));

            ResolvedContext dev = resolver.resolve("/dev/testdb/testcoll");
            assertFalse(dev.canCreateDatabases(), "At collection level, cannot create databases");
            assertFalse(dev.canDeleteDatabase(), CANNOT_DELETE_DATABASE_FROM_COLLECTION_VIEW);

            ResolvedContext prod = resolver.resolve("/prod/data");
            assertFalse(prod.canCreateDatabases(), "Prod restricts database operations");
            assertEquals("production", prod.database());
        }
    }
}
