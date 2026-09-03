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
package org.restheart.graphql.mcp;

import java.util.List;
import java.util.Optional;

import graphql.language.FieldDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.restheart.graphql.scalars.BsonScalars;

/**
 * Parses a GraphQL app's SDL (schema) string into the {@code Query}/{@code Mutation} operations
 * it declares — one entry per field on those root types, with its argument names/types and
 * return type exactly as written in the SDL. Used by {@code GraphqlAppMcpResourceBuilder} to
 * document what an agent can put in a GraphQL request's {@code query} string; it does not
 * validate or execute anything, only reads the type declarations.
 *
 * <p>Mirrors {@code GraphQLApp.Builder.build()}: the app's raw SDL is prefixed with
 * {@link BsonScalars#getBsonScalarHeader()} before parsing, since app schemas routinely
 * reference RESTHeart's custom BSON scalar types (e.g. {@code BsonObjectId}) that don't resolve
 * without it.
 */
public final class SdlContextBuilder {

    /** One argument of an {@link Operation}, with its type exactly as declared in the SDL (e.g. {@code "String!"}, {@code "[ID!]"}). */
    public record Arg(String name, String type, boolean required) {
    }

    /** One field of the {@code Query} or {@code Mutation} root type. */
    public record Operation(String name, List<Arg> args, String returnType) {
    }

    private SdlContextBuilder() {
    }

    /** @return the {@code Query} type's fields, or an empty list if the SDL has none/fails to parse */
    public static List<Operation> queries(String sdl) {
        return operations(sdl, "Query");
    }

    /** @return the {@code Mutation} type's fields, or an empty list if the SDL has none/fails to parse */
    public static List<Operation> mutations(String sdl) {
        return operations(sdl, "Mutation");
    }

    private static List<Operation> operations(String sdl, String rootTypeName) {
        if (sdl == null || sdl.isBlank()) {
            return List.of();
        }

        TypeDefinitionRegistry registry;
        try {
            registry = new SchemaParser().parse(BsonScalars.getBsonScalarHeader() + sdl);
        } catch (RuntimeException e) {
            return List.of();
        }

        Optional<ObjectTypeDefinition> rootType;
        try {
            rootType = registry.getType(rootTypeName, ObjectTypeDefinition.class);
        } catch (RuntimeException e) {
            return List.of();
        }

        return rootType.map(t -> t.getFieldDefinitions().stream().map(SdlContextBuilder::toOperation).toList())
                .orElseGet(List::of);
    }

    private static Operation toOperation(FieldDefinition field) {
        var args = field.getInputValueDefinitions().stream()
                .map(SdlContextBuilder::toArg)
                .toList();
        return new Operation(field.getName(), args, typeString(field.getType()));
    }

    private static Arg toArg(InputValueDefinition iv) {
        return new Arg(iv.getName(), typeString(iv.getType()), isRequired(iv.getType()));
    }

    private static boolean isRequired(Type<?> type) {
        return type instanceof NonNullType;
    }

    private static String typeString(Type<?> type) {
        if (type instanceof NonNullType nonNull) {
            return typeString(nonNull.getType()) + "!";
        }
        if (type instanceof ListType list) {
            return "[" + typeString(list.getType()) + "]";
        }
        if (type instanceof TypeName named) {
            return named.getName();
        }
        return type.toString();
    }
}
