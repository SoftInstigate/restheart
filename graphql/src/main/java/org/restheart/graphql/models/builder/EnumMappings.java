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
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.restheart.graphql.GraphQLIllegalAppDefinitionException;
import graphql.language.EnumTypeDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;

class EnumMappings extends Mappings {
    /**
     *
     * @param doc
     * @param typeDefinitionRegistry
     * @return the enums mappings
     * @throws GraphQLIllegalAppDefinitionException
     */
    static Map<String, Map<String, Object>> get(BsonDocument doc, TypeDefinitionRegistry typeDefinitionRegistry) throws GraphQLIllegalAppDefinitionException {
        var ret = new HashMap<String, Map<String, Object>>();

        var wrongEnumMapping = doc.keySet().stream()
            .filter(key -> isEnum(key, typeDefinitionRegistry))
            .filter(key -> !doc.get(key).isDocument())
            .findFirst();

        if (wrongEnumMapping.isPresent()) {
            var wrongEnum = wrongEnumMapping.get();
            throw new GraphQLIllegalAppDefinitionException("Error with mappings of enum: '" + wrongEnum + "'. Enum mappings must be of type 'DOCUMENT' but was " + doc.get(wrongEnum).getBsonType());
        }

        // enums
        // enums with mappings => use given mappings
        doc.keySet().stream()
            .filter(key -> doc.get(key).isDocument())
            .filter(key -> isEnum(key, typeDefinitionRegistry))
            .forEach(enumKey -> ret.put(enumKey, enumValuesMappings(enumKey, (EnumTypeDefinition) typeDefinitionRegistry.types().get(enumKey), doc.getDocument(enumKey))));

        // enums with no mappings => use default mappings
        typeDefinitionRegistry.types().entrySet().stream()
            .filter(e -> e.getValue() instanceof EnumTypeDefinition)
            .filter(e -> !doc.containsKey(e.getKey())) // mapping doc does not contain a document for the enum
            .forEach(e -> ret.put(e.getKey(), enumValuesMappings(e.getKey(), (EnumTypeDefinition) e.getValue(), new BsonDocument())));
        // end - enums

        return ret;
    }

    private static HashMap<String, Object> enumValuesMappings(String enumKey, EnumTypeDefinition typeDef, BsonDocument enumDoc) {
        var ret = new HashMap<String, Object>();
        enumDoc.entrySet().stream().forEach(e -> ret.put(e.getKey(), e.getValue()));

        // if the mapping is missing a key, then map to itself as default mapping
        // i.e. enum Colors { RED BLUE } are mapped by default to BsonString("BLUE") and BsonString("GREEN")
        typeDef.getEnumValueDefinitions().stream().map(vd -> vd.getName()).filter(key -> !enumDoc.containsKey(key)).forEach(key -> ret.put(key, new BsonString(key)));
        return ret;
    }
}
