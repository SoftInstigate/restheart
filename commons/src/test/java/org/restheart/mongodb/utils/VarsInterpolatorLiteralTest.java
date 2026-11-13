/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
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
package org.restheart.mongodb.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;
import org.restheart.mongodb.utils.StagesInterpolator.STAGE_OPERATOR;
import org.restheart.mongodb.utils.VarsInterpolator.VAR_OPERATOR;

/**
 * A string variable starting with '$' is bound in {@code $literal} where MongoDB would read it as
 * a field path or a variable, i.e. everywhere but in a {@code $match} stage outside {@code $expr}.
 */
public class VarsInterpolatorLiteralTest {
    private static List<BsonDocument> interpolate(String stages, String values, boolean literalStrings) throws Exception {
        return StagesInterpolator.interpolate(VAR_OPERATOR.$var, STAGE_OPERATOR.$ifvar,
                BsonArray.parse(stages), BsonDocument.parse(values), null, literalStrings);
    }

    private static void assertStages(String expected, List<BsonDocument> actual) {
        assertEquals(BsonArray.parse(expected), new BsonArray(actual));
    }

    @Test
    public void inMatchTheValueIsLeftAsItIs() throws Exception {
        assertStages("[{\"$match\": {\"status\": \"$status\"}}]",
                interpolate("[{\"$match\": {\"status\": {\"$var\": \"s\"}}}]", "{\"s\": \"$status\"}", true));
    }

    @Test
    public void inExprTheValueIsLiteral() throws Exception {
        assertStages("[{\"$match\": {\"$expr\": {\"$eq\": [\"$status\", {\"$literal\": \"$status\"}]}}}]",
                interpolate("[{\"$match\": {\"$expr\": {\"$eq\": [\"$status\", {\"$var\": \"s\"}]}}}]", "{\"s\": \"$status\"}", true));
    }

    @Test
    public void inOtherStagesTheValueIsLiteral() throws Exception {
        assertStages("[{\"$addFields\": {\"label\": {\"$literal\": \"$5\"}}}, {\"$group\": {\"_id\": {\"$literal\": \"$$ROOT\"}}}]",
                interpolate("[{\"$addFields\": {\"label\": {\"$var\": \"l\"}}}, {\"$group\": {\"_id\": {\"$var\": \"g\"}}}]",
                        "{\"l\": \"$5\", \"g\": \"$$ROOT\"}", true));
    }

    @Test
    public void aDocumentWithAFieldPathStringIsLiteralAsAWhole() throws Exception {
        assertStages("[{\"$addFields\": {\"x\": {\"$literal\": {\"a\": [1, \"$secret\"]}}}}]",
                interpolate("[{\"$addFields\": {\"x\": {\"$var\": \"v\"}}}]", "{\"v\": {\"a\": [1, \"$secret\"]}}", true));
    }

    @Test
    public void valuesWithoutFieldPathStringsAreNotWrapped() throws Exception {
        assertStages("[{\"$addFields\": {\"label\": \"A\", \"n\": {\"a\": 1}}}, {\"$limit\": 10}]",
                interpolate("[{\"$addFields\": {\"label\": {\"$var\": \"l\"}, \"n\": {\"$var\": \"n\"}}}, {\"$limit\": {\"$var\": \"max\"}}]",
                        "{\"l\": \"A\", \"n\": {\"a\": 1}, \"max\": 10}", true));
    }

    @Test
    public void nestedPipelinesFollowTheSameRule() throws Exception {
        assertStages("""
                [{"$lookup": {"from": "c", "let": {"x": {"$literal": "$a"}}, "pipeline": [
                    {"$match": {"k": "$a"}},
                    {"$match": {"$expr": {"$eq": ["$k", {"$literal": "$a"}]}}}
                ], "as": "r"}},
                {"$facet": {"f": [{"$match": {"k": "$a"}}, {"$project": {"p": {"$literal": "$a"}}}]}}]
                """,
                interpolate("""
                [{"$lookup": {"from": "c", "let": {"x": {"$var": "v"}}, "pipeline": [
                    {"$match": {"k": {"$var": "v"}}},
                    {"$match": {"$expr": {"$eq": ["$k", {"$var": "v"}]}}}
                ], "as": "r"}},
                {"$facet": {"f": [{"$match": {"k": {"$var": "v"}}}, {"$project": {"p": {"$var": "v"}}}]}}]
                """, "{\"v\": \"$a\"}", true));
    }

    @Test
    public void aVariableTheDefinitionAlreadyWrapsInLiteralIsNotWrappedTwice() throws Exception {
        assertStages("[{\"$addFields\": {\"label\": {\"$literal\": \"$5\"}}}]",
                interpolate("[{\"$addFields\": {\"label\": {\"$literal\": {\"$var\": \"l\"}}}}]", "{\"l\": \"$5\"}", true));
    }

    @Test
    public void aDefaultValueComesFromTheDefinitionAndIsNotWrapped() throws Exception {
        assertStages("[{\"$group\": {\"_id\": \"$category\"}}]",
                interpolate("[{\"$group\": {\"_id\": {\"$var\": [\"g\", \"$category\"]}}}]", "{}", true));
    }

    @Test
    public void withoutLiteralStringsNothingIsWrapped() throws Exception {
        assertStages("[{\"$match\": {\"$expr\": {\"$eq\": [\"$status\", \"$status\"]}}}]",
                interpolate("[{\"$match\": {\"$expr\": {\"$eq\": [\"$status\", {\"$var\": \"s\"}]}}}]", "{\"s\": \"$status\"}", false));
    }
}
