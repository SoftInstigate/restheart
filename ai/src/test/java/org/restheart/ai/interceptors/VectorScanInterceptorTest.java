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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;
import org.restheart.ai.vectorscan.VectorSimilarity;
import org.restheart.exchange.MongoRequest;

public class VectorScanInterceptorTest {

    private static BsonDocument stage(String key, BsonDocument value) {
        return new BsonDocument(key, value);
    }

    private static BsonArray vector(double... values) {
        var arr = new BsonArray();
        for (var v : values) {
            arr.add(new BsonDouble(v));
        }
        return arr;
    }

    @Test
    public void toFloatArray_convertsInOrder() {
        var result = VectorScanInterceptor.toFloatArray(vector(1.0, 2.5, -3.0));
        assertArrayEquals(new float[] {1.0f, 2.5f, -3.0f}, result);
    }

    @Test
    public void indexOfVectorScanStage_bsonArray_findsSoleKeyMatch() {
        var stages = new BsonArray(List.of(
            stage("$match", new BsonDocument("status", new BsonString("published"))),
            stage("$vectorScan", new BsonDocument("path", new BsonString("embedding")))));

        assertEquals(1, VectorScanInterceptor.indexOfVectorScanStage(stages));
        assertTrue(VectorScanInterceptor.containsVectorScanStage(stages));
    }

    @Test
    public void indexOfVectorScanStage_bsonArray_absentReturnsNegativeOne() {
        var stages = new BsonArray(List.of(
            stage("$match", new BsonDocument("status", new BsonString("published")))));

        assertEquals(-1, VectorScanInterceptor.indexOfVectorScanStage(stages));
        assertFalse(VectorScanInterceptor.containsVectorScanStage(stages));
    }

    @Test
    public void indexOfVectorScanStage_ignoresMultiKeyStages() {
        // a stage with $vectorScan as one of several keys must not match --
        // real $vectorScan usage is always a single-key stage object
        var multiKey = new BsonDocument("$vectorScan", new BsonDocument())
            .append("somethingElse", new BsonString("x"));
        var stages = new BsonArray(List.of(multiKey));

        assertEquals(-1, VectorScanInterceptor.indexOfVectorScanStage(stages));
    }

    @Test
    public void indexOfVectorScanStage_list_findsSoleKeyMatch() {
        List<BsonDocument> stages = List.of(
            stage("$match", new BsonDocument()),
            stage("$sort", new BsonDocument()),
            stage("$vectorScan", new BsonDocument()));

        assertEquals(2, VectorScanInterceptor.indexOfVectorScanStage(stages));
    }

    @Test
    public void findStagesArray_returnsStagesForMatchingUri() {
        var request = mock(MongoRequest.class);
        var stagesArray = new BsonArray(List.of(stage("$vectorScan", new BsonDocument())));
        var aggrs = new BsonArray(List.of(
            new BsonDocument("uri", new BsonString("other")).append("stages", new BsonArray()),
            new BsonDocument("uri", new BsonString("semantic-search")).append("stages", stagesArray)));
        var collProps = new BsonDocument("aggrs", aggrs);

        when(request.getCollectionProps()).thenReturn(collProps);
        when(request.getAggregationOperation()).thenReturn("semantic-search");

        assertEquals(stagesArray, VectorScanInterceptor.findStagesArray(request));
    }

    @Test
    public void findStagesArray_unescapesUnderscoreDollarKeys() {
        // request.getCollectionProps() returns the raw stored form: MongoDB disallows
        // storing keys starting with $, so RESTHeart escapes them as _$xxx on write and
        // only unescapes on the way out (StagesInterpolator.interpolate(), later, in
        // handle()). This lookup runs before that -- and before resolve() -- so it must
        // unescape itself, or it will never recognize a real "$vectorScan" stage.
        var request = mock(MongoRequest.class);
        var escapedStages = new BsonArray(List.of(stage("_$match", new BsonDocument()), stage("_$vectorScan", new BsonDocument())));
        var aggrs = new BsonArray(List.of(
            new BsonDocument("uri", new BsonString("semantic-search")).append("stages", escapedStages)));
        var collProps = new BsonDocument("aggrs", aggrs);

        when(request.getCollectionProps()).thenReturn(collProps);
        when(request.getAggregationOperation()).thenReturn("semantic-search");

        var found = VectorScanInterceptor.findStagesArray(request);
        assertEquals(1, VectorScanInterceptor.indexOfVectorScanStage(found));
        assertTrue(VectorScanInterceptor.containsVectorScanStage(found));
    }

    @Test
    public void findStagesArray_noMatchingUri_returnsNull() {
        var request = mock(MongoRequest.class);
        var aggrs = new BsonArray(List.of(
            new BsonDocument("uri", new BsonString("other")).append("stages", new BsonArray())));
        var collProps = new BsonDocument("aggrs", aggrs);

        when(request.getCollectionProps()).thenReturn(collProps);
        when(request.getAggregationOperation()).thenReturn("semantic-search");

        assertNull(VectorScanInterceptor.findStagesArray(request));
    }

    @Test
    public void findStagesArray_noAggrsMetadata_returnsNull() {
        var request = mock(MongoRequest.class);
        when(request.getCollectionProps()).thenReturn(new BsonDocument());
        when(request.getAggregationOperation()).thenReturn("semantic-search");

        assertNull(VectorScanInterceptor.findStagesArray(request));
    }

    @Test
    public void scoreAndRank_ordersByScoreDescendingAndTruncatesToLimit() {
        var near = new BsonDocument("_id", new BsonString("near")).append("embedding", vector(1.0, 0.0));
        var mid = new BsonDocument("_id", new BsonString("mid")).append("embedding", vector(0.7, 0.7));
        var far = new BsonDocument("_id", new BsonString("far")).append("embedding", vector(0.0, 1.0));

        var result = VectorScanInterceptor.scoreAndRank(
            List.of(far, near, mid), "embedding", new float[] {1.0f, 0.0f}, VectorSimilarity.COSINE, 2);

        assertEquals(2, result.size());
        assertEquals("near", result.get(0).asDocument().getString("_id").getValue());
        assertEquals("mid", result.get(1).asDocument().getString("_id").getValue());
        assertTrue(result.get(0).asDocument().containsKey("score"));
        assertTrue(result.get(0).asDocument().getDouble("score").getValue() > result.get(1).asDocument().getDouble("score").getValue());
    }

    @Test
    public void scoreAndRank_skipsDocumentsMissingTheVectorField() {
        var noVector = new BsonDocument("_id", new BsonString("no-vector"));
        var withVector = new BsonDocument("_id", new BsonString("has-vector")).append("embedding", vector(1.0, 0.0));

        var result = VectorScanInterceptor.scoreAndRank(
            List.of(noVector, withVector), "embedding", new float[] {1.0f, 0.0f}, VectorSimilarity.COSINE, 10);

        assertEquals(1, result.size());
        assertEquals("has-vector", result.get(0).asDocument().getString("_id").getValue());
    }

    @Test
    public void scoreAndRank_skipsDocumentsWithMismatchedVectorLength() {
        var wrongLength = new BsonDocument("_id", new BsonString("wrong-length")).append("embedding", vector(1.0, 0.0, 0.0));
        var correct = new BsonDocument("_id", new BsonString("correct")).append("embedding", vector(1.0, 0.0));

        var result = VectorScanInterceptor.scoreAndRank(
            List.of(wrongLength, correct), "embedding", new float[] {1.0f, 0.0f}, VectorSimilarity.COSINE, 10);

        assertEquals(1, result.size());
        assertEquals("correct", result.get(0).asDocument().getString("_id").getValue());
    }

    @Test
    public void scoreAndRank_doesNotMutateOriginalDocuments() {
        var doc = new BsonDocument("_id", new BsonString("d")).append("embedding", vector(1.0, 0.0));

        VectorScanInterceptor.scoreAndRank(List.of(doc), "embedding", new float[] {1.0f, 0.0f}, VectorSimilarity.COSINE, 10);

        assertFalse(doc.containsKey("score"), "the input document must not be mutated -- scoreAndRank must clone before appending 'score'");
    }

    @Test
    public void scoreAndRank_emptyCandidates_returnsEmptyArray() {
        var result = VectorScanInterceptor.scoreAndRank(List.of(), "embedding", new float[] {1.0f}, VectorSimilarity.COSINE, 10);
        assertEquals(0, result.size());
    }
}
