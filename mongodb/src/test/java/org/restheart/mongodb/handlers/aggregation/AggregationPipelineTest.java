package org.restheart.mongodb.handlers.aggregation;

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.junit.Assert;
import org.junit.Test;
import org.restheart.exchange.QueryVariableNotBoundException;
import org.restheart.utils.BsonUtils;
import static org.restheart.utils.BsonUtils.document;

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
        Assert.assertEquals(1, ap.getResolvedStagesAsList(new BsonDocument()).size());
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
        Assert.assertThrows(QueryVariableNotBoundException.class, () -> ap.getResolvedStagesAsList(document().get()));
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

        Assert.assertEquals(1, resolvedStages.size());
        Assert.assertEquals(new BsonString("foo"), resolvedStages.get(0).getDocument("$match").getString("name"));
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
        var resolvedStagesShouldUseAvar = ap.getResolvedStagesAsList(document().put("s", document().put("surname", -1)).get());

        Assert.assertEquals(1, resolvedStagesShouldUseAvar.size());
        Assert.assertEquals(document().put("surname", -1).get(), resolvedStagesShouldUseAvar.get(0).getDocument("$sort"));

        var resolvedStagesShouldUseDefaultValue = ap.getResolvedStagesAsList(document().get());

        Assert.assertEquals(1, resolvedStagesShouldUseDefaultValue.size());
        Assert.assertEquals(document().put("surname", 1).get(), resolvedStagesShouldUseDefaultValue.get(0).getDocument("$sort"));
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
        var optinalStagesShouldBeIn = ap.getResolvedStagesAsList(document().put("s", document().put("surname", -1)).get());

        Assert.assertEquals(2, optinalStagesShouldBeIn.size());
        Assert.assertEquals(document().put("surname", -1).get(), optinalStagesShouldBeIn.get(1).getDocument("$sort"));

        var optinalStagesShouldBeOut = ap.getResolvedStagesAsList(document().get());

        Assert.assertEquals(1, optinalStagesShouldBeOut.size());
        Assert.assertEquals(document().put("name", "foo").get(), optinalStagesShouldBeOut.get(0).getDocument("$match"));
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

        Assert.assertEquals(2, optinalStagesShouldBeIn.size());
        Assert.assertEquals(document().put("surname", -1).get(), optinalStagesShouldBeIn.get(1).getDocument("$sort"));

        var optinalStagesShouldBeOut = ap.getResolvedStagesAsList(document().get());

        Assert.assertEquals(1, optinalStagesShouldBeOut.size());
        Assert.assertEquals(document().put("name", "foo").get(), optinalStagesShouldBeOut.get(0).getDocument("$match"));
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

        Assert.assertEquals(2, optinalStagesShouldBeUsed.size());
        Assert.assertEquals(document().put("surname", -1).get(), optinalStagesShouldBeUsed.get(1).getDocument("$sort"));

        var elseStageShouldBeUsed = ap.getResolvedStagesAsList(document().get());

        Assert.assertEquals(2, elseStageShouldBeUsed.size());
        Assert.assertEquals(document().put("else", 1).get(), elseStageShouldBeUsed.get(1).getDocument("$sort"));
    }
}
