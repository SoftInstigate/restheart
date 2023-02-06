/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2023 SoftInstigate
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
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.TypeName;
import graphql.schema.idl.TypeDefinitionRegistry;

class InterfacesMappings extends Mappings {
    /**
     *
     * @param doc
     * @param typeDefinitionRegistry
     * @return the interfaces typeResolvers
     * @throws GraphQLIllegalAppDefinitionException
     */
    static Map<String, Map<String, io.undertow.predicate.Predicate>> get(BsonDocument doc, TypeDefinitionRegistry typeDefinitionRegistry) throws GraphQLIllegalAppDefinitionException {
        var ret = new HashMap<String, Map<String, io.undertow.predicate.Predicate>>();

        // check that all interfaces have a mapping with a $typeResolver
         // check that the $typeResolver object, maps all the members of the interface
        var _interfaceWithMissingMapping = typeDefinitionRegistry.types().entrySet().stream()
            .filter(e -> e.getValue() instanceof InterfaceTypeDefinition)
            .filter(e -> !doc.containsKey(e.getKey()) || !doc.get(e.getKey()).isDocument())
            .findFirst();

        if (_interfaceWithMissingMapping.isPresent()) {
            var interfaceWithMissingMapping = _interfaceWithMissingMapping.get().getKey();
            throw new GraphQLIllegalAppDefinitionException("Missing mappings for interface " + interfaceWithMissingMapping);
        }

        var _interfaceWithMissingTypeResolver = typeDefinitionRegistry.types().entrySet().stream()
            .filter(e -> e.getValue() instanceof InterfaceTypeDefinition)
            .filter(e -> !doc.get(e.getKey()).asDocument().containsKey("_$typeResolver"))
            .findFirst();

        if (_interfaceWithMissingTypeResolver.isPresent()) {
            var interfaceWithMissingTypeResolver = _interfaceWithMissingTypeResolver.get().getKey();
            throw new GraphQLIllegalAppDefinitionException("Missing $typeResolver for interface " + interfaceWithMissingTypeResolver);
        }


        // check that all interface mappings are documents
        var _wrongMappingNoDoc = doc.keySet().stream()
            .filter(key -> isInterface(key, typeDefinitionRegistry))
            .filter(key -> !doc.get(key).isDocument())
            .findFirst();

        if (_wrongMappingNoDoc.isPresent()) {
            var wrongMapping = _wrongMappingNoDoc.get();
            throw new GraphQLIllegalAppDefinitionException("Wrong mappings for interface " + wrongMapping + ": mappings must be an Object but was " + doc.get(wrongMapping).getBsonType());
        }

        // check that all interface mappings have a $typeResolver field
        var _wrongMappingMissingTypeResolver = doc.keySet().stream()
            .filter(key -> isInterface(key, typeDefinitionRegistry))
            .filter(key -> !doc.get(key).asDocument().containsKey("_$typeResolver"))
            .findFirst();

        if (_wrongMappingMissingTypeResolver.isPresent()) {
            var wrongMapping = _wrongMappingMissingTypeResolver.get();
            throw new GraphQLIllegalAppDefinitionException("Wrong mappings for interface " + wrongMapping + ": it does not define $typeResolver");
        }

        // check that all interface mappings have a valid $typeResolver predicate
        var _wrongMappingTypeResolverNotDoc = doc.keySet().stream()
            .filter(key -> isInterface(key, typeDefinitionRegistry))
            .filter(key -> !doc.get(key).asDocument().get("_$typeResolver").isDocument())
            .findFirst();

        if (_wrongMappingTypeResolverNotDoc.isPresent()) {
            var wrongMapping = _wrongMappingTypeResolverNotDoc.get();
            throw new GraphQLIllegalAppDefinitionException("Wrong mappings for interface " + wrongMapping + ": the $typeResolver is not an Object");
        }

        // check the predicates of all interfaces
        doc.keySet().stream()
            .filter(type -> isInterface(type, typeDefinitionRegistry))
            .flatMap(type -> doc.get(type).asDocument().get("_$typeResolver").asDocument().entrySet().stream())
            .forEach(e -> {
                try {
                    typeResolverPredicate(e.getValue());
                } catch(Throwable t) {
                    LambdaUtils.throwsSneakyException(t);
                }
            });

        // check that $typeResolver maps all the objects implementing the interfaces
        typeDefinitionRegistry.types().entrySet().stream()
            .filter(e -> e.getValue() instanceof InterfaceTypeDefinition)
            .forEach(e -> {
                var interfaceName = e.getKey();

                var objectsImplementing = typeDefinitionRegistry.types().entrySet().stream()
                    .filter(t -> t.getValue() instanceof ObjectTypeDefinition)
                    .map(t -> (ObjectTypeDefinition) t.getValue())
                    .filter(t -> t.getImplements().stream().map(_t -> ((TypeName)_t).getName()).collect(Collectors.toList()).contains(interfaceName))
                    .map(t -> t.getName())
                    .collect(Collectors.toList());
;
                var mappedObjectsNames = doc.get(interfaceName).asDocument().get("_$typeResolver").asDocument().keySet();

                if (!mappedObjectsNames.containsAll(objectsImplementing)) {
                    LambdaUtils.throwsSneakyException(new GraphQLIllegalAppDefinitionException("$typeResolver for interface " + interfaceName + " does not map all objects implementing it"));
                }
            });


        // all checks done, create the ret
        doc.keySet().stream()
            .filter(key -> isInterface(key, typeDefinitionRegistry))
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
