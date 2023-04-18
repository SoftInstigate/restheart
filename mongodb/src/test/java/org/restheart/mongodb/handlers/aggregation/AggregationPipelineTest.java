package org.restheart.mongodb.handlers.aggregation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.restheart.utils.BsonUtils.document;

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;
import org.restheart.exchange.QueryVariableNotBoundException;
import org.restheart.utils.BsonUtils;

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
        assertEquals(1, ap.getResolvedStagesAsList(new BsonDocument()).size());
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
        assertThrows(QueryVariableNotBoundException.class, () -> ap.getResolvedStagesAsList(document().get()));
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
        var resolvedStages = ap.getResolvedStagesAsList(document().put("name", "foo").get());

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
        var resolvedStagesShouldUseAvar = ap
                .getResolvedStagesAsList(document().put("s", document().put("surname", -1)).get());

        assertEquals(1, resolvedStagesShouldUseAvar.size());
        assertEquals(document().put("surname", -1).get(),
                resolvedStagesShouldUseAvar.get(0).getDocument("$sort"));

        var resolvedStagesShouldUseDefaultValue = ap.getResolvedStagesAsList(document().get());

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
        var optinalStagesShouldBeIn = ap
                .getResolvedStagesAsList(document().put("s", document().put("surname", -1)).get());

        assertEquals(2, optinalStagesShouldBeIn.size());
        assertEquals(document().put("surname", -1).get(), optinalStagesShouldBeIn.get(1).getDocument("$sort"));

        var optinalStagesShouldBeOut = ap.getResolvedStagesAsList(document().get());

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
        var optinalStagesShouldBeIn = ap.getResolvedStagesAsList(document()
                .put("s", document().put("surname", -1))
                .put("n", 1).get());

        assertEquals(2, optinalStagesShouldBeIn.size());
        assertEquals(document().put("surname", -1).get(), optinalStagesShouldBeIn.get(1).getDocument("$sort"));

        var optinalStagesShouldBeOut = ap.getResolvedStagesAsList(document().get());

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
        var optinalStagesShouldBeUsed = ap.getResolvedStagesAsList(document()
                .put("s", document().put("surname", -1)).get());

        assertEquals(2, optinalStagesShouldBeUsed.size());
        assertEquals(document().put("surname", -1).get(), optinalStagesShouldBeUsed.get(1).getDocument("$sort"));

        var elseStageShouldBeUsed = ap.getResolvedStagesAsList(document().get());

        assertEquals(2, elseStageShouldBeUsed.size());
        assertEquals(document().put("else", 1).get(), elseStageShouldBeUsed.get(1).getDocument("$sort"));
    }
}
