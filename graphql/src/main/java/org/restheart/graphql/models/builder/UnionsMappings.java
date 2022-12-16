/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2022 SoftInstigate
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

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.bson.BsonDocument;
import org.restheart.graphql.GraphQLIllegalAppDefinitionException;
import org.restheart.utils.LambdaUtils;

import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;

class UnionsMappings extends Mappings {
    /**
     *
     * @param doc
     * @param typeDefinitionRegistry
     * @return the unions mappings
     * @throws GraphQLIllegalAppDefinitionException
     */
     static Map<String, Map<String, io.undertow.predicate.Predicate>> get(BsonDocument doc, TypeDefinitionRegistry typeDefinitionRegistry) throws GraphQLIllegalAppDefinitionException {
        var ret = new HashMap<String, Map<String, io.undertow.predicate.Predicate>>();

        // check that all union have a mapping with a $typeResolver
         // check that the $typeResolver object, maps all the members of the union
        var _unionWithMissingMapping = typeDefinitionRegistry.types().entrySet().stream()
            .filter(e -> e.getValue() instanceof UnionTypeDefinition)
            .filter(e -> !doc.containsKey(e.getKey()) || !doc.get(e.getKey()).isDocument())
            .findFirst();

        if (_unionWithMissingMapping.isPresent()) {
            var unionWithMissingMapping = _unionWithMissingMapping.get().getKey();
            throw new GraphQLIllegalAppDefinitionException("Missing mappings for union '" + unionWithMissingMapping);
        }

        var _unionWithMissingTypeResolver = typeDefinitionRegistry.types().entrySet().stream()
            .filter(e -> e.getValue() instanceof UnionTypeDefinition)
            .filter(e -> !doc.get(e.getKey()).asDocument().containsKey("_$typeResolver"))
            .findFirst();

        if (_unionWithMissingTypeResolver.isPresent()) {
            var unionWithMissingTypeResolver = _unionWithMissingTypeResolver.get().getKey();
            throw new GraphQLIllegalAppDefinitionException("Missing $typeResolver for union '" + unionWithMissingTypeResolver);
        }


        // check that all union mappings are documents
        var _wrongMappingNoDoc = doc.keySet().stream()
            .filter(key -> isUnion(key, typeDefinitionRegistry))
            .filter(key -> !doc.get(key).isDocument())
            .findFirst();

        if (_wrongMappingNoDoc.isPresent()) {
            var wrongMapping = _wrongMappingNoDoc.get();
            throw new GraphQLIllegalAppDefinitionException("Wrong mappings for union '" + wrongMapping + "': mappings must be of type 'DOCUMENT' but was " + doc.get(wrongMapping).getBsonType());
        }

        // check that all union mappings have a $typeResolver field
        var _wrongMappingMissingTypeResolver = doc.keySet().stream()
            .filter(key -> isUnion(key, typeDefinitionRegistry))
            .filter(key -> !doc.get(key).asDocument().containsKey("_$typeResolver"))
            .findFirst();

        if (_wrongMappingMissingTypeResolver.isPresent()) {
            var wrongMapping = _wrongMappingMissingTypeResolver.get();
            throw new GraphQLIllegalAppDefinitionException("Wrong mappings for union '" + wrongMapping + "': it does not define $typeResolver");
        }

        // check that all union mappings have a valid $typeResolver predicate
        var _wrongMappingTypeResolverNotDoc = doc.keySet().stream()
            .filter(key -> isUnion(key, typeDefinitionRegistry))
            .filter(key -> !doc.get(key).asDocument().get("_$typeResolver").isDocument())
            .findFirst();

        if (_wrongMappingTypeResolverNotDoc.isPresent()) {
            var wrongMapping = _wrongMappingTypeResolverNotDoc.get();
            throw new GraphQLIllegalAppDefinitionException("Wrong mappings for union '" + wrongMapping + "': the $typeResolver is not an Object");
        }

        // check the predicates of all unions
        doc.keySet().stream()
            .filter(type -> isUnion(type, typeDefinitionRegistry))
            .flatMap(type -> doc.get(type).asDocument().get("_$typeResolver").asDocument().entrySet().stream())
            .forEach(e -> {
                try {
                    typeResolverPredicate(e.getValue());
                } catch(Throwable t) {
                    LambdaUtils.throwsSneakyException(t);
                }
            });

        // check that the $typeResolver object, maps all the members of the union
        typeDefinitionRegistry.types().entrySet().stream()
            .filter(e -> e.getValue() instanceof UnionTypeDefinition)
            .forEach(e -> {
                var unionName = e.getKey();
                var unionTypeDef = (UnionTypeDefinition) e.getValue();

                var memberNames = unionTypeDef.getMemberTypes().stream()
                    //Note that members of a union type need to be concrete object type
                    .filter(type -> type instanceof TypeName)
                    .map(type -> (TypeName) type)
                    .map(m -> m.getName()).collect(Collectors.toList());

                var mappedMemberNames = doc.get(unionName).asDocument().get("_$typeResolver").asDocument().keySet();

                if (!mappedMemberNames.containsAll(memberNames)) {
                    LambdaUtils.throwsSneakyException(new GraphQLIllegalAppDefinitionException("$typeResolver for union " + unionName + " does not map all union members"));
                }
            });


        // all checks done, create the ret
        doc.keySet().stream()
            .filter(key -> isUnion(key, typeDefinitionRegistry))
            .forEach(type -> {
                var trm = new HashMap<String, io.undertow.predicate.Predicate>();

                doc.get(type).asDocument().get("_$typeResolver").asDocument().entrySet().stream()
                    .forEach(e -> {
                        try {
                            trm.put(e.getKey(), typeResolverPredicate(e.getValue()));
                        } catch(Throwable t) {
                            // should never happen, already checked
                            LambdaUtils.throwsSneakyException(t);
                        }
                    });

                ret.put(type, trm);
            });

        return ret;
    }
}
