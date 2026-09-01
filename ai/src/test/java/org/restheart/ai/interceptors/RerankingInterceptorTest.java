/*-
 * ========================LICENSE_START=================================
 * restheart-ai
 * %%
 * Copyright (C) 2024 - 2026 SoftInstigate
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
package org.restheart.ai.interceptors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;
import org.restheart.exchange.MongoRequest;
import org.restheart.plugins.ai.RankedResult;

public class RerankingInterceptorTest {

    // -- resolveQuery: "$var" syntax, same as everywhere else in an aggregation ------

    @Test
    public void resolveQuery_literalString_returnedAsIs() {
        var request = mock(MongoRequest.class);
        var rerankConfig = new BsonDocument("query", new BsonString("electric cars"));

        assertEquals("electric cars", RerankingInterceptor.resolveQuery(rerankConfig, request));
    }

    @Test
    public void resolveQuery_varReference_resolvesFromAggregationVars() {
        var request = mock(MongoRequest.class);
        when(request.getAggregationVars()).thenReturn(new BsonDocument("q", new BsonString("electric cars")));
        var rerankConfig = new BsonDocument("query", new BsonDocument("$var", new BsonString("q")));

        assertEquals("electric cars", RerankingInterceptor.resolveQuery(rerankConfig, request));
    }

    @Test
    public void resolveQuery_varWithDefault_usesDefaultWhenUnbound() {
        var request = mock(MongoRequest.class);
        when(request.getAggregationVars()).thenReturn(new BsonDocument());
        var rerankConfig = new BsonDocument("query",
            new BsonDocument("$var", new BsonArray(List.of(new BsonString("q"), new BsonString("fallback")))));

        assertEquals("fallback", RerankingInterceptor.resolveQuery(rerankConfig, request));
    }

    @Test
    public void resolveQuery_varWithDefault_usesBoundValueWhenPresent() {
        var request = mock(MongoRequest.class);
        when(request.getAggregationVars()).thenReturn(new BsonDocument("q", new BsonString("bound value")));
        var rerankConfig = new BsonDocument("query",
            new BsonDocument("$var", new BsonArray(List.of(new BsonString("q"), new BsonString("fallback")))));

        assertEquals("bound value", RerankingInterceptor.resolveQuery(rerankConfig, request));
    }

    @Test
    public void resolveQuery_unboundVarWithNoDefault_returnsNull() {
        var request = mock(MongoRequest.class);
        when(request.getAggregationVars()).thenReturn(new BsonDocument());
        var rerankConfig = new BsonDocument("query", new BsonDocument("$var", new BsonString("q")));

        assertNull(RerankingInterceptor.resolveQuery(rerankConfig, request));
    }

    @Test
    public void resolveQuery_missingQueryField_returnsNull() {
        var request = mock(MongoRequest.class);
        assertNull(RerankingInterceptor.resolveQuery(new BsonDocument(), request));
    }

    @Test
    public void findRerankConfig_unescapesDollarKeysInsideRerankBlock() {
        // request.getCollectionProps() returns the raw stored form: MongoDB disallows
        // storing keys starting with $, so a "query": {"$var": "q"} rerank block is
        // actually stored (and returned here) as {"_$var": "q"} -- findRerankConfig
        // must unescape it, or resolveQuery() would never recognize the "$var" key.
        var request = mock(MongoRequest.class);
        var escapedRerank = new BsonDocument("model", new BsonString("rerank-2.5"))
            .append("query", new BsonDocument("_$var", new BsonString("q")))
            .append("topK", new BsonInt32(3));
        var aggrs = new BsonArray(List.of(
            new BsonDocument("uri", new BsonString("semantic-search")).append("rerank", escapedRerank)));
        var collProps = new BsonDocument("aggrs", aggrs);

        when(request.getCollectionProps()).thenReturn(collProps);
        when(request.getAggregationOperation()).thenReturn("semantic-search");
        when(request.getAggregationVars()).thenReturn(new BsonDocument("q", new BsonString("electric cars")));

        var rerankConfig = RerankingInterceptor.findRerankConfig(request);
        assertEquals("electric cars", RerankingInterceptor.resolveQuery(rerankConfig, request));
    }

    @Test
    public void resolveQuery_varResolvesToNonString_returnsNull() {
        var request = mock(MongoRequest.class);
        when(request.getAggregationVars()).thenReturn(new BsonDocument("q", new BsonInt32(42)));
        var rerankConfig = new BsonDocument("query", new BsonDocument("$var", new BsonString("q")));

        assertNull(RerankingInterceptor.resolveQuery(rerankConfig, request));
    }

    private static BsonArray sampleResults() {
        var results = new BsonArray();
        results.add(new BsonDocument("text", new BsonString("first document")));
        results.add(new BsonDocument("text", new BsonString("second document")));
        results.add(new BsonDocument("text", new BsonString("third document")));
        return results;
    }

    @Test
    public void reordersDocumentsAccordingToRankingIndex() {
        var original = sampleResults();
        // rerank API says: originally-third document is now first, originally-first is second
        var rerankResponse = "[{\"index\":2,\"score\":0.9},{\"index\":0,\"score\":0.5}]";

        var reranked = RerankingInterceptor.applyRanking(original, rerankResponse);

        assertEquals(2, reranked.size());
        assertEquals("third document", reranked.get(0).asDocument().getString("text").getValue());
        assertEquals("first document", reranked.get(1).asDocument().getString("text").getValue());
    }

    @Test
    public void appendsRerankScoreToSurvivingDocuments() {
        var original = sampleResults();
        var rerankResponse = "[{\"index\":1,\"score\":0.75}]";

        var reranked = RerankingInterceptor.applyRanking(original, rerankResponse);

        assertEquals(1, reranked.size());
        assertEquals(0.75, reranked.get(0).asDocument().getDouble("_rerankScore").getValue(), 1e-9);
    }

    @Test
    public void missingScore_defaultsToZero() {
        var original = sampleResults();
        var rerankResponse = "[{\"index\":0}]";

        var reranked = RerankingInterceptor.applyRanking(original, rerankResponse);

        assertEquals(1, reranked.size());
        assertEquals(0.0, reranked.get(0).asDocument().getDouble("_rerankScore").getValue(), 1e-9);
    }

    @Test
    public void outOfRangeIndex_isSkippedNotThrown() {
        var original = sampleResults();
        var rerankResponse = "[{\"index\":99,\"score\":0.9},{\"index\":1,\"score\":0.5}]";

        var reranked = RerankingInterceptor.applyRanking(original, rerankResponse);

        assertEquals(1, reranked.size());
        assertEquals("second document", reranked.get(0).asDocument().getString("text").getValue());
    }

    @Test
    public void negativeIndex_isSkipped() {
        var original = sampleResults();
        var rerankResponse = "[{\"index\":-1,\"score\":0.9}]";

        var reranked = RerankingInterceptor.applyRanking(original, rerankResponse);

        assertTrue(reranked.isEmpty());
    }

    @Test
    public void emptyRankingArray_producesEmptyResult() {
        var reranked = RerankingInterceptor.applyRanking(sampleResults(), "[]");
        assertTrue(reranked.isEmpty());
    }

    // -- applyRankedResults (Phase 2: Provider<RerankModel> path) --------------

    @Test
    public void applyRankedResults_reordersDocumentsAccordingToIndex() {
        var original = sampleResults();
        var ranked = List.of(new RankedResult(2, 0.9), new RankedResult(0, 0.5));

        var reranked = RerankingInterceptor.applyRankedResults(original, ranked);

        assertEquals(2, reranked.size());
        assertEquals("third document", reranked.get(0).asDocument().getString("text").getValue());
        assertEquals("first document", reranked.get(1).asDocument().getString("text").getValue());
    }

    @Test
    public void applyRankedResults_appendsRerankScore() {
        var original = sampleResults();
        var ranked = List.of(new RankedResult(1, 0.75));

        var reranked = RerankingInterceptor.applyRankedResults(original, ranked);

        assertEquals(1, reranked.size());
        assertEquals(0.75, reranked.get(0).asDocument().getDouble("_rerankScore").getValue(), 1e-9);
    }

    @Test
    public void applyRankedResults_outOfRangeIndex_isSkipped() {
        var original = sampleResults();
        var ranked = List.of(new RankedResult(99, 0.9), new RankedResult(1, 0.5));

        var reranked = RerankingInterceptor.applyRankedResults(original, ranked);

        assertEquals(1, reranked.size());
        assertEquals("second document", reranked.get(0).asDocument().getString("text").getValue());
    }

    @Test
    public void applyRankedResults_negativeIndex_isSkipped() {
        var reranked = RerankingInterceptor.applyRankedResults(sampleResults(), List.of(new RankedResult(-1, 0.9)));
        assertTrue(reranked.isEmpty());
    }

    @Test
    public void applyRankedResults_emptyList_producesEmptyResult() {
        var reranked = RerankingInterceptor.applyRankedResults(sampleResults(), List.of());
        assertTrue(reranked.isEmpty());
    }

    @Test
    public void escape_handlesQuotesNewlinesAndBackslashes() {
        var escaped = RerankingInterceptor.escape("line1\n\"quoted\"\\line2\ttabbed\rcr");
        // the escaped string must be safe to embed in a JSON string literal:
        // re-wrap it and confirm it parses back to the exact original value.
        var roundTripped = BsonDocument.parse("{\"v\":\"" + escaped + "\"}").getString("v").getValue();
        assertEquals("line1\n\"quoted\"\\line2\ttabbed\rcr", roundTripped);
    }
}
