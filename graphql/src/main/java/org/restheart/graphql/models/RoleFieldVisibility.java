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

import java.util.List;
import java.util.Set;

import org.restheart.graphql.scalars.BsonScalars;

import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLSchema;
import graphql.schema.transform.FieldVisibilitySchemaTransformation;
import graphql.schema.transform.VisibleFieldPredicate;

/**
 * Applies {@code @visible(roles: [...])} to a schema, producing the schema one set of roles is
 * allowed to see (restheart#478).
 *
 * <p>A hidden field is <b>removed from the schema</b>, not nulled at execution: introspection does
 * not list it, and asking for it is a validation error saying the field does not exist. That is
 * the property a visibility directive is for — a caller who may not see a field does not learn
 * that it is there.
 *
 * <p>Undecorated fields are visible to everyone. Visibility is opt-in per field, so adding the
 * directive to a schema changes nothing until it is written on a field.
 */
public final class RoleFieldVisibility {

    private RoleFieldVisibility() {
    }

    /**
     * @param schema the app's full schema
     * @param roles  the caller's roles
     * @return the schema without the fields those roles may not see, or {@code schema} itself when
     *         no field is decorated
     */
    public static GraphQLSchema forRoles(GraphQLSchema schema, Set<String> roles) {
        return new FieldVisibilitySchemaTransformation(predicate(roles)).apply(schema);
    }

    /**
     * Whether any field carries the directive. Checked once when the app is built: the whole
     * transformation, and the per-role schema cache behind it, is skipped for the apps — most of
     * them — that never use it.
     */
    public static boolean isUsedIn(GraphQLSchema schema) {
        return schema.getAllTypesAsList().stream()
                .filter(graphql.schema.GraphQLFieldsContainer.class::isInstance)
                .map(graphql.schema.GraphQLFieldsContainer.class::cast)
                .flatMap(container -> container.getFieldDefinitions().stream())
                .anyMatch(field -> field.getAppliedDirective(BsonScalars.VISIBLE_DIRECTIVE) != null);
    }

    private static VisibleFieldPredicate predicate(Set<String> roles) {
        return env -> {
            if (!(env.getSchemaElement() instanceof GraphQLFieldDefinition field)) {
                return true;
            }

            var directive = field.getAppliedDirective(BsonScalars.VISIBLE_DIRECTIVE);
            if (directive == null) {
                return true;
            }

            var argument = directive.getArgument("roles");
            if (argument == null) {
                // the directive declares roles as non-null, so the schema would not have parsed;
                // treat an absent list as naming nobody rather than as naming everybody
                return false;
            }

            List<String> allowed = argument.getValue();

            return allowed != null && allowed.stream().anyMatch(roles::contains);
        };
    }
}
