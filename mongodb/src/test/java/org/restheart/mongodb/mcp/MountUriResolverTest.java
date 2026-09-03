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
import org.restheart.mongodb.mcp.MountUriResolver.Mount;

public class MountUriResolverTest {

    @Test
    public void defaultMount_flattensSingleDatabaseAtRoot() {
        var resolver = new MountUriResolver(List.of(new Mount("restheart/{*}", "/")));

        assertEquals("/", resolver.databasePath("restheart").orElseThrow());
        assertEquals("/users", resolver.collectionPath("restheart", "users").orElseThrow());
    }

    @Test
    public void wildcardMountAtRoot_exposesEveryDatabaseAsPathSegment() {
        var resolver = new MountUriResolver(List.of(new Mount("*", "/")));

        assertEquals("/warehouse", resolver.databasePath("warehouse").orElseThrow());
        assertEquals("/warehouse/inventory", resolver.collectionPath("warehouse", "inventory").orElseThrow());
    }

    @Test
    public void wildcardMountAtCustomPrefix_prependsPrefix() {
        var resolver = new MountUriResolver(List.of(new Mount("*", "/api")));

        assertEquals("/api/warehouse", resolver.databasePath("warehouse").orElseThrow());
        assertEquals("/api/warehouse/inventory", resolver.collectionPath("warehouse", "inventory").orElseThrow());
    }

    @Test
    public void wholeDatabaseMountWithoutWildcardSuffix_nestsCollectionsUnderWhere() {
        var resolver = new MountUriResolver(List.of(new Mount("restheart", "/restheart")));

        assertEquals("/restheart", resolver.databasePath("restheart").orElseThrow());
        assertEquals("/restheart/users", resolver.collectionPath("restheart", "users").orElseThrow());
    }

    @Test
    public void fixedCollectionMount_exposesExactlyThatCollectionNoDatabaseUrl() {
        var resolver = new MountUriResolver(List.of(new Mount("/restheart/users", "/api/v1/users")));

        assertEquals("/api/v1/users", resolver.collectionPath("restheart", "users").orElseThrow());
        assertTrue(resolver.databasePath("restheart").isEmpty());
    }

    @Test
    public void noMatchingMount_resolvesToEmpty() {
        var resolver = new MountUriResolver(List.of(new Mount("restheart/{*}", "/")));

        assertTrue(resolver.databasePath("other").isEmpty());
        assertTrue(resolver.collectionPath("other", "coll").isEmpty());
    }

    @Test
    public void firstMatchingMountWins() {
        var resolver = new MountUriResolver(List.of(
                new Mount("/restheart/users", "/api/v1/users"),
                new Mount("*", "/")));

        assertEquals("/api/v1/users", resolver.collectionPath("restheart", "users").orElseThrow());
        // a different collection in the same db still falls through to the wildcard mount
        assertEquals("/restheart/orders", resolver.collectionPath("restheart", "orders").orElseThrow());
    }

    @Test
    public void trailingSlashOnWhere_doesNotDoubleUpSlashes() {
        var resolver = new MountUriResolver(List.of(new Mount("*", "/api/")));

        assertEquals("/api/warehouse", resolver.databasePath("warehouse").orElseThrow());
    }
}
