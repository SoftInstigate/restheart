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
package org.restheart.graphql.models;

import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;

import java.util.Map;

public abstract class TypeMapping {

    protected final String typeName;
    protected final Map<String, FieldMapping> fieldMappingMap;

    public TypeMapping(String typeName, Map<String, FieldMapping> fieldMappingMap){
        this.typeName = typeName;
        this.fieldMappingMap = fieldMappingMap;
    }

    public Map<String, FieldMapping> getFieldMappingMap() {
        return fieldMappingMap;
    }

    public abstract TypeRuntimeWiring.Builder getTypeWiring(TypeDefinitionRegistry typeRegistry);

}
