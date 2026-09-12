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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.restheart.plugins.mcp.McpScopeProvider;
import org.restheart.mongodb.mcp.MountUriResolver.Mount;

public class MountUriResolverTest {

    @Test
    public void defaultMount_flattensSingleDatabaseAtRoot() {
        var resolver = new MountUriResolver(List.of(new Mount("restheart/{*}", "/")));

        assertEquals("/", resolver.databasePath("restheart", McpScopeProvider.UNPARTITIONED).orElseThrow());
        assertEquals("/users", resolver.collectionPath("restheart", "users", McpScopeProvider.UNPARTITIONED).orElseThrow());
    }

    @Test
    public void wildcardMountAtRoot_exposesEveryDatabaseAsPathSegment() {
        var resolver = new MountUriResolver(List.of(new Mount("*", "/")));

        assertEquals("/warehouse", resolver.databasePath("warehouse", McpScopeProvider.UNPARTITIONED).orElseThrow());
        assertEquals("/warehouse/inventory", resolver.collectionPath("warehouse", "inventory", McpScopeProvider.UNPARTITIONED).orElseThrow());
    }

    @Test
    public void wildcardMountAtCustomPrefix_prependsPrefix() {
        var resolver = new MountUriResolver(List.of(new Mount("*", "/api")));

        assertEquals("/api/warehouse", resolver.databasePath("warehouse", McpScopeProvider.UNPARTITIONED).orElseThrow());
        assertEquals("/api/warehouse/inventory", resolver.collectionPath("warehouse", "inventory", McpScopeProvider.UNPARTITIONED).orElseThrow());
    }

    @Test
    public void wholeDatabaseMountWithoutWildcardSuffix_nestsCollectionsUnderWhere() {
        var resolver = new MountUriResolver(List.of(new Mount("restheart", "/restheart")));

        assertEquals("/restheart", resolver.databasePath("restheart", McpScopeProvider.UNPARTITIONED).orElseThrow());
        assertEquals("/restheart/users", resolver.collectionPath("restheart", "users", McpScopeProvider.UNPARTITIONED).orElseThrow());
    }

    @Test
    public void fixedCollectionMount_exposesExactlyThatCollectionNoDatabaseUrl() {
        var resolver = new MountUriResolver(List.of(new Mount("/restheart/users", "/api/v1/users")));

        assertEquals("/api/v1/users", resolver.collectionPath("restheart", "users", McpScopeProvider.UNPARTITIONED).orElseThrow());
        assertTrue(resolver.databasePath("restheart", McpScopeProvider.UNPARTITIONED).isEmpty());
    }

    @Test
    public void noMatchingMount_resolvesToEmpty() {
        var resolver = new MountUriResolver(List.of(new Mount("restheart/{*}", "/")));

        assertTrue(resolver.databasePath("other", McpScopeProvider.UNPARTITIONED).isEmpty());
        assertTrue(resolver.collectionPath("other", "coll", McpScopeProvider.UNPARTITIONED).isEmpty());
    }

    @Test
    public void firstMatchingMountWins() {
        var resolver = new MountUriResolver(List.of(
                new Mount("/restheart/users", "/api/v1/users"),
                new Mount("*", "/")));

        assertEquals("/api/v1/users", resolver.collectionPath("restheart", "users", McpScopeProvider.UNPARTITIONED).orElseThrow());
        // a different collection in the same db still falls through to the wildcard mount
        assertEquals("/restheart/orders", resolver.collectionPath("restheart", "orders", McpScopeProvider.UNPARTITIONED).orElseThrow());
    }

    @Test
    public void trailingSlashOnWhere_doesNotDoubleUpSlashes() {
        var resolver = new MountUriResolver(List.of(new Mount("*", "/api/")));

        assertEquals("/api/warehouse", resolver.databasePath("warehouse", McpScopeProvider.UNPARTITIONED).orElseThrow());
    }

    @Test
    public void defaultMount_collectionTemplateHasNoDbSegment() {
        // RESTHeart's actual default mongo-mounts: {what: "restheart", where: "/"}
        var resolver = new MountUriResolver(List.of(new Mount("restheart", "/")));

        assertEquals(List.of(), resolver.databasePathTemplates(McpScopeProvider.UNPARTITIONED));
        assertEquals(List.of("/{collection}"), resolver.collectionPathTemplates(McpScopeProvider.UNPARTITIONED));
    }

    @Test
    public void flattenedSingleDatabaseMount_collectionTemplateHasNoDbSegment() {
        var resolver = new MountUriResolver(List.of(new Mount("restheart/{*}", "/")));

        assertEquals(List.of(), resolver.databasePathTemplates(McpScopeProvider.UNPARTITIONED));
        assertEquals(List.of("/{collection}"), resolver.collectionPathTemplates(McpScopeProvider.UNPARTITIONED));
    }

    @Test
    public void wildcardMount_templatesIncludeDbSegment() {
        var resolver = new MountUriResolver(List.of(new Mount("*", "/")));

        assertEquals(List.of("/{db}"), resolver.databasePathTemplates(McpScopeProvider.UNPARTITIONED));
        assertEquals(List.of("/{db}/{collection}"), resolver.collectionPathTemplates(McpScopeProvider.UNPARTITIONED));
    }

    @Test
    public void fixedCollectionMount_contributesNoTemplate() {
        var resolver = new MountUriResolver(List.of(new Mount("/restheart/users", "/api/v1/users")));

        assertEquals(List.of(), resolver.databasePathTemplates(McpScopeProvider.UNPARTITIONED));
        assertEquals(List.of(), resolver.collectionPathTemplates(McpScopeProvider.UNPARTITIONED));
    }

    @Test
    public void parametricMount_isSkippedWithoutAScopeToBindItTo() {
        // The Shared shape: the database is the first hostname label, so with no scope there is
        // nothing to resolve {host[0]} to. This is why such a deployment used to advertise an
        // empty MCP catalogue.
        var resolver = new MountUriResolver(List.of(new Mount("{host[0]}/{*}", "/")));

        assertTrue(resolver.databasePath("f0f0f0", McpScopeProvider.UNPARTITIONED).isEmpty());
        assertTrue(resolver.collectionPath("f0f0f0", "inventory", McpScopeProvider.UNPARTITIONED).isEmpty());
    }

    @Test
    public void parametricMount_resolvesAgainstTheScope() {
        var resolver = new MountUriResolver(List.of(new Mount("{host[0]}/{*}", "/")));

        assertEquals("/", resolver.databasePath("f0f0f0", "f0f0f0").orElseThrow());
        assertEquals("/inventory", resolver.collectionPath("f0f0f0", "inventory", "f0f0f0").orElseThrow());
    }

    @Test
    public void parametricMount_exposesNothingOfAnotherScopesDatabase() {
        // The mount binds to the caller's own scope, so another database does not resolve through
        // it even when it exists and holds a collection of the same name.
        var resolver = new MountUriResolver(List.of(new Mount("{host[0]}/{*}", "/")));

        assertTrue(resolver.collectionPath("f1f1f1", "inventory", "f0f0f0").isEmpty());
    }
}
