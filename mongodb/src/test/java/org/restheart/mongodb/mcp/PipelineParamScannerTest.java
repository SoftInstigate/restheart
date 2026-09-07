/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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
package org.restheart.mongodb.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bson.BsonArray;
import org.junit.jupiter.api.Test;

public class PipelineParamScannerTest {

    @Test
    public void noVars_emptySet() {
        var stages = BsonArray.parse("[{\"$match\": {\"status\": \"active\"}}]");
        assertTrue(PipelineParamScanner.scan(stages).names().isEmpty());
    }

    @Test
    public void simpleVar_found() {
        var stages = BsonArray.parse("[{\"$match\": {\"location\": {\"$var\": \"location\"}}}]");
        assertEquals(List.of("location"), List.copyOf(PipelineParamScanner.scan(stages).names()));
    }

    @Test
    public void varWithDefault_nameExtracted() {
        var stages = BsonArray.parse("[{\"$match\": {\"qty\": {\"$var\": [\"minQty\", 5]}}}]");
        assertEquals(List.of("minQty"), List.copyOf(PipelineParamScanner.scan(stages).names()));
    }

    @Test
    public void multipleDistinctVars_acrossStages() {
        var stages = BsonArray.parse("""
                [
                  {"$match": {"location": {"$var": "location"}}},
                  {"$group": {"_id": {"$var": "groupBy"}}}
                ]
                """);
        assertEquals(List.of("location", "groupBy"), List.copyOf(PipelineParamScanner.scan(stages).names()));
    }

    @Test
    public void sameVarUsedTwice_countedOnce() {
        var stages = BsonArray.parse("""
                [
                  {"$match": {"a": {"$var": "x"}, "b": {"$var": "x"}}}
                ]
                """);
        assertEquals(1, PipelineParamScanner.scan(stages).names().size());
    }

    @Test
    public void varNestedInsideArrayOperator_found() {
        var stages = BsonArray.parse("""
                [
                  {"$match": {"$or": [ {"a": {"$var": "x"}}, {"b": {"$var": "y"}} ]}}
                ]
                """);
        assertEquals(List.of("x", "y"), List.copyOf(PipelineParamScanner.scan(stages).names()));
    }

    @Test
    public void docWithExtraKeyAlongside_varKey_isNotTreatedAsVarReference() {
        // {"$var": "x", "extra": 1} has 2 keys, so it does not match VarsInterpolator's
        // own "exactly one key named $var" rule -- it is just an ordinary two-field document.
        var stages = BsonArray.parse("[{\"$match\": {\"$var\": \"x\", \"extra\": 1}}]");
        assertTrue(PipelineParamScanner.scan(stages).names().isEmpty());
    }

    @Test
    public void bareVarWithNoDefault_isRequired() {
        var stages = BsonArray.parse("[{\"$match\": {\"status\": {\"$var\": \"status\"}}}]");
        assertTrue(PipelineParamScanner.scan(stages).isRequired("status"));
    }

    @Test
    public void varWithDefault_isNotRequired() {
        var stages = BsonArray.parse("[{\"$match\": {\"qty\": {\"$var\": [\"minQty\", 5]}}}]");
        assertFalse(PipelineParamScanner.scan(stages).isRequired("minQty"));
    }

    @Test
    public void ifvarConditionVariable_isNotRequired() {
        var stages = BsonArray.parse("""
                [
                  {"$ifvar": ["includeStats", {"$addFields": {"flag": true}}]}
                ]
                """);
        var result = PipelineParamScanner.scan(stages);
        assertEquals(List.of("includeStats"), List.copyOf(result.names()));
        assertFalse(result.isRequired("includeStats"));
    }

    @Test
    public void bareVarInsideIfvarThenStage_isNotRequired() {
        // the classic pattern: {"$ifvar": ["sortField", {"$sort": {"$var": "sortField"}}]} --
        // the $var reference inside the guarded stage can never throw, since the whole stage is
        // skipped when the condition variable ("sortField", same name here) is absent
        var stages = BsonArray.parse("""
                [
                  {"$ifvar": ["sortField", {"$sort": {"$var": "sortField"}}]}
                ]
                """);
        var result = PipelineParamScanner.scan(stages);
        assertEquals(List.of("sortField"), List.copyOf(result.names()));
        assertFalse(result.isRequired("sortField"));
    }

    @Test
    public void ifvarElseStage_alsoTreatedAsConditional() {
        var stages = BsonArray.parse("""
                [
                  {"$ifvar": ["flag", {"$match": {"a": {"$var": "onVar"}}}, {"$match": {"b": {"$var": "offVar"}}}]}
                ]
                """);
        var result = PipelineParamScanner.scan(stages);
        assertFalse(result.isRequired("onVar"));
        assertFalse(result.isRequired("offVar"));
    }

    @Test
    public void sameVarRequiredOutsideAndOptionalInsideIfvar_isRequiredOverall() {
        var stages = BsonArray.parse("""
                [
                  {"$match": {"status": {"$var": "status"}}},
                  {"$ifvar": ["status", {"$addFields": {"flag": true}}]}
                ]
                """);
        assertTrue(PipelineParamScanner.scan(stages).isRequired("status"));
    }
}
