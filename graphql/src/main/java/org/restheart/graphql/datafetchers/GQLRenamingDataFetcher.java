/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2021 SoftInstigate
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

import graphql.schema.DataFetchingEnvironment;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.graphql.models.FieldRenaming;

import java.util.Arrays;
import java.util.regex.Pattern;

public class GQLRenamingDataFetcher extends GraphQLDataFetcher {

    public GQLRenamingDataFetcher(FieldRenaming fieldRenaming) {
        super(fieldRenaming);
    }

    @Override
    public BsonValue get(DataFetchingEnvironment dataFetchingEnvironment) throws Exception {

        String alias = ((FieldRenaming) this.fieldMapping).getAlias();

        BsonDocument parentDocument = dataFetchingEnvironment.getSource();
        return getValues(parentDocument, alias);
    }


    private BsonValue getValues(BsonValue bsonValue, String path) {

        String[] splitPath = path.split(Pattern.quote("."));
        BsonValue current = bsonValue;

        for (int i = 0; i < splitPath.length; i++) {
            if (current.isDocument() && current.asDocument().containsKey(splitPath[i])) {
                current = current.asDocument().get(splitPath[i]);
            } else if (current.isArray()) {
                try {
                    Integer index = Integer.parseInt(splitPath[i]);
                    current = current.asArray().get(index);
                } catch (NumberFormatException nfe) {
                    BsonArray array = new BsonArray();
                    for (BsonValue value : current.asArray()) {
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
