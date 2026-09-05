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
package org.restheart.mongodb.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.junit.jupiter.api.Test;
import org.restheart.mongodb.mcp.MongoMcpAwareImpl.MetadataSource;
import org.restheart.mongodb.mcp.MountUriResolver.Mount;
import org.restheart.plugins.mcp.McpContext;

public class MongoMcpAwareImplTest {

    private static final McpContext CTX = new McpContext(null, "https://host", "mongo-service", "/", Map.of());

    private static final class FakeMetadataSource implements MetadataSource {
        private final Map<String, List<String>> collectionsByDb = new HashMap<>();
        private final Map<String, BsonDocument> dbProps = new HashMap<>();
        private final Map<String, BsonDocument> collProps = new HashMap<>();
        private final Map<String, BsonDocument> schemasByPointer = new HashMap<>();

        FakeMetadataSource withCollections(String db, String... colls) {
            collectionsByDb.put(db, List.of(colls));
            return this;
        }

        FakeMetadataSource withDbProps(String db, BsonDocument props) {
            dbProps.put(db, props);
            return this;
        }

        FakeMetadataSource withCollProps(String db, String coll, BsonDocument props) {
            collProps.put(db + "." + coll, props);
            return this;
        }

        /** Registers the schema body returned for a given {@code schemaId} (as resolved via a {@code {schemaId: ...}} pointer). */
        FakeMetadataSource withSchema(String schemaId, BsonDocument body) {
            schemasByPointer.put(schemaId, body);
            return this;
        }

        @Override
        public List<String> databaseNames() {
            return List.copyOf(collectionsByDb.keySet());
        }

        @Override
        public List<String> collectionNames(String dbName) {
            return collectionsByDb.getOrDefault(dbName, List.of());
        }

        @Override
        public BsonDocument databaseProperties(String dbName) {
            return dbProps.get(dbName);
        }

        @Override
        public BsonDocument collectionProperties(String dbName, String collName) {
            return collProps.get(dbName + "." + collName);
        }

        @Override
        public BsonDocument resolveJsonSchema(String dbName, BsonValue jsonSchemaPointer) {
            if (!(jsonSchemaPointer instanceof BsonDocument pointer) || !pointer.containsKey("schemaId")) {
                return null;
            }
            return schemasByPointer.get(pointer.get("schemaId").asString().getValue());
        }
    }

    @Test
    public void mcpEnabledCollection_producesResourceAtMountedUri() {
        var metadata = new FakeMetadataSource()
                .withCollections("warehouse", "inventory")
                .withCollProps("warehouse", "inventory", BsonDocument.parse("{\"mcp\": {\"description\": \"Stock.\"}}"));
        var resolver = new MountUriResolver(List.of(new Mount("*", "/")));

        var resources = new MongoMcpAwareImpl(metadata, resolver).describeMcp(CTX);

        assertEquals(1, resources.size());
        assertEquals("https://host/warehouse/inventory", resources.get(0).uri());
    }

    @Test
    public void collectionWithNoMcpBlock_producesNoResource() {
        var metadata = new FakeMetadataSource()
                .withCollections("warehouse", "inventory")
                .withCollProps("warehouse", "inventory", new BsonDocument());
        var resolver = new MountUriResolver(List.of(new Mount("*", "/")));

        var resources = new MongoMcpAwareImpl(metadata, resolver).describeMcp(CTX);

        assertTrue(resources.isEmpty());
    }

    @Test
    public void collectionUnreachableByAnyMount_isSkippedEvenIfMcpEnabled() {
        var metadata = new FakeMetadataSource()
                .withCollections("warehouse", "inventory")
                .withCollProps("warehouse", "inventory", BsonDocument.parse("{\"mcp\": {\"description\": \"x\"}}"));
        var resolver = new MountUriResolver(List.of(new Mount("otherdb/{*}", "/")));

        var resources = new MongoMcpAwareImpl(metadata, resolver).describeMcp(CTX);

        assertTrue(resources.isEmpty());
    }

    @Test
    public void aggregationAndStreamSubEntries_buildAtDerivedUris() {
        var collProps = BsonDocument.parse("""
                {
                  "mcp": { "description": "Stock." },
                  "aggrs": [ { "uri": "byLoc", "stages": [{"$match": {}}], "mcp": {"description": "By location"} } ],
                  "streams": [ { "uri": "lowStock", "stages": [{"$match": {}}], "mcp": {"description": "Low stock"} } ]
                }
                """);
        var metadata = new FakeMetadataSource()
                .withCollections("warehouse", "inventory")
                .withCollProps("warehouse", "inventory", collProps);
        var resolver = new MountUriResolver(List.of(new Mount("*", "/")));

        var resources = new MongoMcpAwareImpl(metadata, resolver).describeMcp(CTX);

        var uris = resources.stream().map(r -> r.uri()).toList();
        assertTrue(uris.contains("https://host/warehouse/inventory"));
        assertTrue(uris.contains("https://host/warehouse/inventory/_aggrs/byLoc"));
        assertTrue(uris.contains("https://host/warehouse/inventory/_streams/lowStock"));
    }

    @Test
    public void mcpEnabledDatabase_referencesEnabledCollectionsOnly() {
        var metadata = new FakeMetadataSource()
                .withCollections("warehouse", "inventory", "scratch")
                .withCollProps("warehouse", "inventory", BsonDocument.parse("{\"mcp\": {\"description\": \"x\"}}"))
                .withCollProps("warehouse", "scratch", new BsonDocument())
                .withDbProps("warehouse", BsonDocument.parse("{\"mcp\": {\"description\": \"Warehouse db.\"}}"));
        var resolver = new MountUriResolver(List.of(new Mount("*", "/")));

        var resources = new MongoMcpAwareImpl(metadata, resolver).describeMcp(CTX);

        var database = resources.stream().filter(r -> "database".equals(r.kind())).findFirst().orElseThrow();
        assertEquals(List.of("https://host/warehouse/inventory"), database.extra().get("collections"));
    }

    @Test
    public void jsonSchemaPointer_resolvedIntoActualBodySchema() {
        var collProps = BsonDocument.parse("""
                { "mcp": { "description": "x" }, "jsonSchema": { "schemaId": "orders" } }
                """);
        var schemaBody = BsonDocument.parse("""
                { "type": "object", "properties": { "sku": { "type": "string" } } }
                """);
        var metadata = new FakeMetadataSource()
                .withCollections("warehouse", "orders")
                .withCollProps("warehouse", "orders", collProps)
                .withSchema("orders", schemaBody);
        var resolver = new MountUriResolver(List.of(new Mount("*", "/")));

        var resources = new MongoMcpAwareImpl(metadata, resolver).describeMcp(CTX);

        var collection = resources.stream().filter(r -> "collection".equals(r.kind())).findFirst().orElseThrow();
        assertEquals("object", collection.actions().get("create").bodySchema().get("type"));
    }

    @Test
    public void unresolvableJsonSchemaPointer_leavesBodySchemaAbsent() {
        var collProps = BsonDocument.parse("""
                { "mcp": { "description": "x" }, "jsonSchema": { "schemaId": "missing" } }
                """);
        var metadata = new FakeMetadataSource()
                .withCollections("warehouse", "orders")
                .withCollProps("warehouse", "orders", collProps);
        var resolver = new MountUriResolver(List.of(new Mount("*", "/")));

        var resources = new MongoMcpAwareImpl(metadata, resolver).describeMcp(CTX);

        var collection = resources.stream().filter(r -> "collection".equals(r.kind())).findFirst().orElseThrow();
        assertNull(collection.actions().get("create").bodySchema());
    }

    @Test
    public void databaseWithNoMcpBlock_producesNoDatabaseResourceButKeepsCollections() {
        var metadata = new FakeMetadataSource()
                .withCollections("warehouse", "inventory")
                .withCollProps("warehouse", "inventory", BsonDocument.parse("{\"mcp\": {\"description\": \"x\"}}"));
        var resolver = new MountUriResolver(List.of(new Mount("*", "/")));

        var resources = new MongoMcpAwareImpl(metadata, resolver).describeMcp(CTX);

        assertTrue(resources.stream().noneMatch(r -> "database".equals(r.kind())));
        assertTrue(resources.stream().anyMatch(r -> "collection".equals(r.kind())));
    }
}
