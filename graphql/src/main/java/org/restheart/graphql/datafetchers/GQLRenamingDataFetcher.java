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
package org.restheart.graphql.datafetchers;

import java.util.Arrays;
import java.util.regex.Pattern;

import org.bson.BsonArray;
import org.bson.BsonValue;
import org.restheart.graphql.models.FieldRenaming;

import graphql.schema.DataFetchingEnvironment;

public class GQLRenamingDataFetcher extends GraphQLDataFetcher {

    public GQLRenamingDataFetcher(FieldRenaming fieldRenaming) {
        super(fieldRenaming);
    }

    @Override
    public Object get(DataFetchingEnvironment env) throws Exception {
        // store the root object in the context
        // this happens when the execution level is 2
        storeRootDoc(env);

        var alias = ((FieldRenaming) this.fieldMapping).getAlias();

        BsonValue source = env.getSource();

        if (source == null || source.isNull()) {
            return null;
        }

        return getValues(source, alias);
    }


    private BsonValue getValues(BsonValue bsonValue, String path) {
        var splitPath = path.split(Pattern.quote("."));
        var current = bsonValue;

        for (int i = 0; i < splitPath.length; i++) {
            if (current.isDocument() && current.asDocument().containsKey(splitPath[i])) {
                current = current.asDocument().get(splitPath[i]);
            } else if (current.isArray()) {
                try {
                    int index = Integer.parseInt(splitPath[i]);
                    current = current.asArray().get(index);
                } catch (NumberFormatException nfe) {
                    var array = new BsonArray();
                    for (var value : current.asArray()) {
                        String[] copy = Arrays.copyOfRange(splitPath, i, splitPath.length);
                        array.add(getValues(value, String.join(".", copy)));
                        current = array;
                    }
                    break;
                } catch (IndexOutOfBoundsException ibe) {
                    return null;
                }

            } else{
                return null;
            }
        }

        return current;
    }
}
