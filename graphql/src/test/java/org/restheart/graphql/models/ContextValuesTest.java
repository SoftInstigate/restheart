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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;
import org.restheart.graphql.datafetchers.GraphQLDataFetcher;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;

/**
 * Which variables an app definition's {@code $arg} references can resolve.
 *
 * <p>These had drifted: an aggregation mapping could write {@code {"$arg": "@user._id"}} and a
 * query mapping could not, so "restrict this query to the caller's own documents" was expressible
 * in one kind of mapping and not in the other, with nothing in the docs to say so.
 */
public class ContextValuesTest {

    /** The helper is protected on FieldMapping; this reaches it the way a real mapping does. */
    private static class Probe extends FieldMapping {
        Probe() {
            super("probe");
        }

        @Override
        public GraphQLDataFetcher getDataFetcher() {
            return null;
        }

        BsonDocument values(DataFetchingEnvironment env) {
            return contextValues(env);
        }
    }

    private static DataFetchingEnvironment env(Map<String, Object> arguments, BsonDocument localContext) {
        return DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
                .arguments(arguments)
                .localContext(localContext)
                .build();
    }

    @Test
    public void theFieldsOwnArgumentsAreThere() {
        var values = new Probe().values(env(Map.of("author", "Calvino"), new BsonDocument()));

        assertEquals(new BsonString("Calvino"), values.get("author"));
    }

    @Test
    public void atVariablesAreNotSuppliedHere() {
        // They are resolved from the request by their registered VarResolver, one source for all
        // of them. Putting @user here as well would give a caller-supplied value a chance to stand
        // in for a server-resolved one — see VarsInterpolatorResolversTest.
        var user = BsonDocument.parse("{ \"_id\": \"alice\" }");
        var values = new Probe().values(env(Map.of(), new BsonDocument("@user", user)));

        assertFalse(values.containsKey("@user"), values.toJson());
        assertFalse(values.containsKey("@user._id"), values.toJson());
    }

    @Test
    public void rootDocIsPassedThroughWhenThereIsOne() {
        var root = BsonDocument.parse("{ \"_id\": 1 }");
        var values = new Probe().values(env(Map.of(), new BsonDocument("rootDoc", root)));

        assertEquals(root, values.get("rootDoc"));
    }

    @Test
    public void rootDocIsAbsentAtTheTopLevel() {
        // it only exists from path level 2 down
        assertTrue(!new Probe().values(env(Map.of(), new BsonDocument())).containsKey("rootDoc"));
    }

    @Test
    public void aMissingLocalContextIsNotAFailure() {
        var values = new Probe().values(env(Map.of("a", 1), null));

        assertEquals(1, values.getNumber("a").intValue());
    }
}
