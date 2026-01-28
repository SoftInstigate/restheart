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

package org.restheart.mongodb.handlers.aggregation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.restheart.utils.BsonUtils.document;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;
import org.restheart.exchange.QueryVariableNotBoundException;
import org.restheart.utils.BsonUtils;
import org.restheart.mongodb.utils.StagesInterpolator;
import org.restheart.mongodb.utils.StagesInterpolator.STAGE_OPERATOR;
import static org.restheart.mongodb.utils.VarsInterpolator.VAR_OPERATOR;

public class AggregationPipelineTest {
    @Test
    public void testSimpleAggregation() throws Exception {
        var aggr = """
                {
                     "type": "pipeline",
                     "uri": "test_ap",
                     "allowDiskUse": false,
                     "stages": [
                         { "_$match": { "name": { "_$exists": true } } }
                     ]
                 }
                 """;

        var apDef = BsonUtils.parse(aggr).asDocument();
        var ap = new AggregationPipeline(apDef);
        assertEquals(1, StagesInterpolator.interpolate(VAR_OPERATOR.$var, STAGE_OPERATOR.$ifvar, ap.getStages(), new BsonDocument()).size());
    }

    @Test
    public void testUnboundAVar() throws Exception {
        var aggr = """
                {
                     "type": "pipeline",
                     "uri": "test_ap",
                     "allowDiskUse": false,
                     "stages": [
                         { "_$match": { "name": { "_$var": "name" } } }
                     ]
                 }
                 """;

        var apDef = BsonUtils.parse(aggr).asDocument();

        var ap = new AggregationPipeline(apDef);
        assertThrows(QueryVariableNotBoundException.class, () -> StagesInterpolator.interpolate(VAR_OPERATOR.$var, STAGE_OPERATOR.$ifvar, ap.getStages(),document().get()));
    }

    @Test
    public void testBoundAVar() throws Exception {
        var aggr = """
                {
                     "type": "pipeline",
                     "uri": "test_ap",
                     "allowDiskUse": false,
                     "stages": [
                         { "_$match": { "name": { "_$var": "name" } } }
                     ]
                 }
                 """;

        var apDef = BsonUtils.parse(aggr).asDocument();

        var ap = new AggregationPipeline(apDef);
        var resolvedStages = StagesInterpolator.interpolate(VAR_OPERATOR.$var, STAGE_OPERATOR.$ifvar, ap.getStages(), document().put("name", "foo").get());

        assertEquals(1, resolvedStages.size());
        assertEquals(new BsonString("foo"), resolvedStages.get(0).getDocument("$match").getString("name"));
    }

    @Test
    public void testOptionalParameter() throws Exception {
        var aggr = """
                {
                    "uri": "by-name",
                    "type": "pipeline",
                    "stages": [
                        { "$sort": { "$var": [ "s", { "surname": 1 } ] } }
                    ]
                }
                """;

        var apDef = BsonUtils.parse(aggr).asDocument();

        var ap = new AggregationPipeline(apDef);
        var resolvedStagesShouldUseAvar = StagesInterpolator.interpolate(VAR_OPERATOR.$var, STAGE_OPERATOR.$ifvar, ap.getStages(),document().put("s", document().put("surname", -1)).get());

        assertEquals(1, resolvedStagesShouldUseAvar.size());
        assertEquals(document().put("surname", -1).get(),
                resolvedStagesShouldUseAvar.get(0).getDocument("$sort"));

        var resolvedStagesShouldUseDefaultValue = StagesInterpolator.interpolate(VAR_OPERATOR.$var, STAGE_OPERATOR.$ifvar, ap.getStages(),document().get());

        assertEquals(1, resolvedStagesShouldUseDefaultValue.size());
        assertEquals(document().put("surname", 1).get(),
                resolvedStagesShouldUseDefaultValue.get(0).getDocument("$sort"));
    }

    @Test
    public void testOptionalStageWithOneVar() throws Exception {
        var aggr = """
                {
                    "uri": "by-name",
                    "type": "pipeline",
                    "stages": [
                        { "_$match": { "name": "foo" } },
                        { "_$ifvar": [ "s", { "_$sort": { "_$var": "s" } } ] }
                    ]
                }
                """;

        var apDef = BsonUtils.parse(aggr).asDocument();

        var ap = new AggregationPipeline(apDef);
        var optinalStagesShouldBeIn = StagesInterpolator.interpolate(VAR_OPERATOR.$var, STAGE_OPERATOR.$ifvar, ap.getStages(),document().put("s", document().put("surname", -1)).get());

        assertEquals(2, optinalStagesShouldBeIn.size());
        assertEquals(document().put("surname", -1).get(), optinalStagesShouldBeIn.get(1).getDocument("$sort"));

        var optinalStagesShouldBeOut = StagesInterpolator.interpolate(VAR_OPERATOR.$var, STAGE_OPERATOR.$ifvar, ap.getStages(),document().get());

        assertEquals(1, optinalStagesShouldBeOut.size());
        assertEquals(document().put("name", "foo").get(), optinalStagesShouldBeOut.get(0).getDocument("$match"));
    }

    @Test
    public void testOptionalStageWithTwoVars() throws Exception {
        var aggr = """
                {
                    "uri": "by-name",
                    "type": "pipeline",
                    "stages": [
                        { "_$match": { "name": "foo" } },
                        { "_$ifvar": [ ["s", "n" ] , { "_$sort": { "_$var": "s" } } ] }
                    ]
                }
                """;

        var apDef = BsonUtils.parse(aggr).asDocument();

        var ap = new AggregationPipeline(apDef);
        var optinalStagesShouldBeIn = StagesInterpolator.interpolate(VAR_OPERATOR.$var, STAGE_OPERATOR.$ifvar, ap.getStages(),document()
                .put("s", document().put("surname", -1))
                .put("n", 1).get());

        assertEquals(2, optinalStagesShouldBeIn.size());
        assertEquals(document().put("surname", -1).get(), optinalStagesShouldBeIn.get(1).getDocument("$sort"));

        var optinalStagesShouldBeOut = StagesInterpolator.interpolate(VAR_OPERATOR.$var, STAGE_OPERATOR.$ifvar, ap.getStages(),document().get());

        assertEquals(1, optinalStagesShouldBeOut.size());
        assertEquals(document().put("name", "foo").get(), optinalStagesShouldBeOut.get(0).getDocument("$match"));
    }

    @Test
    public void testOptionalStageWithElse() throws Exception {
        var aggr = """
                {
                    "uri": "by-name",
                    "type": "pipeline",
                    "stages": [
                        { "_$match": { "name": "foo" } },
                        { "_$ifvar": [ "s" , { "_$sort": { "_$var": "s" } }, { "_$sort": { "else": 1 } } ] }
                    ]
                }
                """;

        var apDef = BsonUtils.parse(aggr).asDocument();

        var ap = new AggregationPipeline(apDef);
        var optinalStagesShouldBeUsed = StagesInterpolator.interpolate(VAR_OPERATOR.$var, STAGE_OPERATOR.$ifvar, ap.getStages(),document()
                .put("s", document().put("surname", -1)).get());

        assertEquals(2, optinalStagesShouldBeUsed.size());
        assertEquals(document().put("surname", -1).get(), optinalStagesShouldBeUsed.get(1).getDocument("$sort"));

        var elseStageShouldBeUsed = StagesInterpolator.interpolate(VAR_OPERATOR.$var, STAGE_OPERATOR.$ifvar, ap.getStages(),document().get());

        assertEquals(2, elseStageShouldBeUsed.size());
        assertEquals(document().put("else", 1).get(), elseStageShouldBeUsed.get(1).getDocument("$sort"));
    }
}
