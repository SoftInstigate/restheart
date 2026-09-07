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

import java.util.List;

import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

public class DatabaseMcpResourceBuilderTest {

    private static final String DATABASE_URI = "https://host/warehouse";

    @Test
    public void noMcpBlock_notBuilt() {
        assertTrue(DatabaseMcpResourceBuilder.build(DATABASE_URI, null, List.of()).isEmpty());
    }

    @Test
    public void explicitlyDisabled_notBuilt() {
        var mcp = BsonDocument.parse("{\"enabled\": false, \"description\": \"Warehouse.\"}");
        assertTrue(DatabaseMcpResourceBuilder.build(DATABASE_URI, mcp, List.of()).isEmpty());
    }

    @Test
    public void missingDescription_notBuilt() {
        assertTrue(DatabaseMcpResourceBuilder.build(DATABASE_URI, new BsonDocument(), List.of()).isEmpty());
    }

    @Test
    public void notExposedJustBecauseCollectionsAreEnabled_stillRequiresOwnMcpBlock() {
        var enabledCollections = List.of(DATABASE_URI + "/inventory");
        assertTrue(DatabaseMcpResourceBuilder.build(DATABASE_URI, null, enabledCollections).isEmpty());
    }

    @Test
    public void enabledWithDescription_buildsResourceWithNoTransports() {
        var mcp = BsonDocument.parse("{\"description\": \"Warehouse management.\"}");

        var resource = DatabaseMcpResourceBuilder.build(DATABASE_URI, mcp, List.of()).orElseThrow();

        assertEquals(DATABASE_URI, resource.uri());
        assertEquals("database", resource.kind());
        assertEquals("Warehouse management.", resource.description());
        assertTrue(resource.actions().isEmpty());
        assertEquals(List.of(), resource.toMap().get("transports"));
    }

    @Test
    public void enabledCollectionUris_referencedInExtra() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");
        var enabledCollections = List.of(DATABASE_URI + "/inventory", DATABASE_URI + "/orders");

        var resource = DatabaseMcpResourceBuilder.build(DATABASE_URI, mcp, enabledCollections).orElseThrow();

        assertEquals(enabledCollections, resource.extra().get("collections"));
    }

    @Test
    public void noEnabledCollections_extraOmitsKey() {
        var mcp = BsonDocument.parse("{\"description\": \"x\"}");
        var resource = DatabaseMcpResourceBuilder.build(DATABASE_URI, mcp, List.of()).orElseThrow();
        assertNull(resource.extra().get("collections"));
    }
}
