/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2024 SoftInstigate
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
package org.restheart.graphql.models.builder;

import java.util.function.Predicate;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.graphql.GraphQLIllegalAppDefinitionException;
import org.restheart.graphql.scalars.BsonScalars;
import org.restheart.utils.BsonUtils;
import org.restheart.utils.LambdaUtils;

import graphql.language.EnumTypeDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.UnionTypeDefinition;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.errors.SchemaProblem;
import io.undertow.predicate.PredicateParser;

abstract class Mappings {
    protected static TypeDefinitionRegistry typeDefinitionRegistry(String schema) throws SchemaProblem {
        var schemaWithBsonScalars = BsonScalars.getBsonScalarHeader() + schema;
        return new SchemaParser().parse(schemaWithBsonScalars);
    }

    protected static void throwIllegalDefinitionException(String field, String type, String arg, String typeExpected, BsonValue value) {
        LambdaUtils.throwsSneakyException(new GraphQLIllegalAppDefinitionException("The mapping for " + type + "." + field + " requires the property '" + arg + "' to be a " + typeExpected + " but it is a " + value.getBsonType()));
    }

    protected static void throwIllegalDefinitionException(String field, String type, String missingPropertyName) {
        LambdaUtils.throwsSneakyException(new GraphQLIllegalAppDefinitionException("The mapping for " + type + "." + field + " does not specify the property '" + missingPropertyName + "'"));
    }

    protected static boolean hasKeyOfType(BsonDocument source, String key, Predicate<BsonValue> isOfType) {
        Predicate<BsonDocument> containsKey = t -> t.containsKey(key);
        return containsKey.test(source) && isOfType.test(source.get(key));
    }

    protected static boolean isInterface(String key, TypeDefinitionRegistry typeDefinitionRegistry) {
        return typeDefinitionRegistry.types().entrySet().stream()
                    .filter(e -> e.getKey().equals(key))
                    .anyMatch(e -> e.getValue() instanceof InterfaceTypeDefinition);
    }

    protected static boolean isObject(String key, TypeDefinitionRegistry typeDefinitionRegistry) {
        return typeDefinitionRegistry.types().entrySet().stream()
                    .filter(e -> e.getKey().equals(key))
                    .anyMatch(e -> e.getValue() instanceof ObjectTypeDefinition);
    }

    protected static boolean isEnum(String key, TypeDefinitionRegistry typeDefinitionRegistry) {
        return typeDefinitionRegistry.types().entrySet().stream()
                    .filter(e -> e.getKey().equals(key))
                    .anyMatch(e -> e.getValue() instanceof EnumTypeDefinition);
    }

    protected static boolean isUnion(String key, TypeDefinitionRegistry typeDefinitionRegistry) {
        return typeDefinitionRegistry.types().entrySet().stream()
                    .filter(e -> e.getKey().equals(key))
                    .anyMatch(e -> e.getValue() instanceof UnionTypeDefinition);
    }

    protected static io.undertow.predicate.Predicate typeResolverPredicate(BsonValue predicate) throws GraphQLIllegalAppDefinitionException {
        if (predicate == null || predicate.isNull()) {
            throw new GraphQLIllegalAppDefinitionException("null $typeResolver predicate");
        }

        if (!predicate.isString()) {
            throw new GraphQLIllegalAppDefinitionException("$typeResolver predicate is not a String: " + BsonUtils.toJson(predicate));
        }

        var p = predicate.asString().getValue();

        try {
            return PredicateParser.parse(p, AppBuilder.class.getClassLoader());
        } catch(Throwable t) {
            throw new GraphQLIllegalAppDefinitionException("error parsing $typeResolver predicate: " + p, t);
        }
    }

}
