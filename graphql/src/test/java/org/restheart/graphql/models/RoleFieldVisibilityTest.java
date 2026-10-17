/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2026 SoftInstigate
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
package org.restheart.graphql.models;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.restheart.graphql.scalars.BsonScalars;

import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;

/**
 * {@code @visible(roles: [...])} removes a field from the schema rather than nulling it at
 * execution (restheart#478). That distinction is the point: a caller who may not see a field must
 * not learn it exists, so the assertions here are about the schema, not about a response.
 */
public class RoleFieldVisibilityTest {

    private static final String SDL = """
            type Person {
                name: String
                salary: Int @visible(roles: ["hr", "admin"])
                ssn: String @visible(roles: ["admin"])
            }

            type Query {
                people: [Person]
                audit: String @visible(roles: ["admin"])
            }
            """;

    private static GraphQLSchema schema(String sdl) {
        var registry = new SchemaParser().parse(BsonScalars.getSchemaHeader() + sdl);

        // the header declares RESTHeart's BSON scalars, so the wiring has to implement them —
        // the same thing GraphQLApp.build() does
        var wiring = RuntimeWiring.newRuntimeWiring();
        BsonScalars.getBsonScalars().forEach((name, scalar) -> wiring.scalar(scalar));

        return new SchemaGenerator().makeExecutableSchema(registry, wiring.build());
    }

    private static boolean hasField(GraphQLSchema schema, String type, String field) {
        var t = schema.getType(type);
        return t instanceof GraphQLObjectType object && object.getFieldDefinition(field) != null;
    }

    @Test
    public void theDirectiveNeedsNoDeclarationInTheAppsOwnSdl() {
        // it comes from the schema header, so an app author just writes it — and cannot declare a
        // conflicting one
        assertNotNull(schema(SDL));
    }

    @Test
    public void anUndecoratedFieldIsVisibleToEveryone() {
        var visible = RoleFieldVisibility.forRoles(schema(SDL), Set.of());

        assertTrue(hasField(visible, "Person", "name"));
    }

    @Test
    public void aDecoratedFieldIsRemovedForARoleItDoesNotName() {
        var visible = RoleFieldVisibility.forRoles(schema(SDL), Set.of("guest"));

        assertFalse(hasField(visible, "Person", "salary"));
        assertFalse(hasField(visible, "Person", "ssn"));
    }

    @Test
    public void aDecoratedFieldSurvivesForARoleItNames() {
        var visible = RoleFieldVisibility.forRoles(schema(SDL), Set.of("hr"));

        assertTrue(hasField(visible, "Person", "salary"));
        // hr is not in ssn's list: naming one role does not grant the others
        assertFalse(hasField(visible, "Person", "ssn"));
    }

    @Test
    public void anyOneOfTheNamedRolesIsEnough() {
        var visible = RoleFieldVisibility.forRoles(schema(SDL), Set.of("guest", "admin"));

        assertTrue(hasField(visible, "Person", "ssn"));
    }

    @Test
    public void aQueryFieldCanBeHiddenToo() {
        assertFalse(hasField(RoleFieldVisibility.forRoles(schema(SDL), Set.of("hr")), "Query", "audit"));
        assertTrue(hasField(RoleFieldVisibility.forRoles(schema(SDL), Set.of("admin")), "Query", "audit"));
    }

    @Test
    public void theFullSchemaIsLeftAlone() {
        // the transformation returns a new schema; the one the app holds must still have everything
        var full = schema(SDL);
        RoleFieldVisibility.forRoles(full, Set.of("guest"));

        assertTrue(hasField(full, "Person", "ssn"));
    }

    @Test
    public void aSchemaThatUsesNoDirectiveIsRecognised() {
        // the fast path: an app that never writes @visible must not pay for any of this
        assertFalse(RoleFieldVisibility.isUsedIn(schema("type Query { a: String }")));
        assertTrue(RoleFieldVisibility.isUsedIn(schema(SDL)));
    }

    @Test
    public void aHiddenFieldIsGoneFromTheSchema_notNulled() {
        // what makes it invisible to introspection as well: there is no field definition left to
        // report, so asking for it fails validation with "field is undefined"
        var visible = RoleFieldVisibility.forRoles(schema(SDL), Set.of("guest"));

        assertNull(((GraphQLObjectType) visible.getType("Person")).getFieldDefinition("ssn"));
    }
}
