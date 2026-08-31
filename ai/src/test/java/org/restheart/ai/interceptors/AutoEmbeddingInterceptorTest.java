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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;

public class AutoEmbeddingInterceptorTest {

    // -- findVectorSearchConfig --------------------------------------------

    @Test
    public void nullCollectionProps_returnsNull() {
        assertNull(AutoEmbeddingInterceptor.findVectorSearchConfig(null));
    }

    @Test
    public void noVectorSearchBlock_returnsNull() {
        var collProps = new BsonDocument("someOtherField", new BsonString("x"));
        assertNull(AutoEmbeddingInterceptor.findVectorSearchConfig(collProps));
    }

    @Test
    public void vectorSearchMissingEmbeddingField_returnsNull() {
        var vs = new BsonDocument("textField", new BsonString("description"));
        var collProps = new BsonDocument("vectorSearch", vs);
        assertNull(AutoEmbeddingInterceptor.findVectorSearchConfig(collProps));
    }

    @Test
    public void validVectorSearchBlock_isReturned() {
        var vs = new BsonDocument("textField", new BsonString("description"))
            .append("embeddingField", new BsonString("embedding"));
        var collProps = new BsonDocument("vectorSearch", vs);

        var found = AutoEmbeddingInterceptor.findVectorSearchConfig(collProps);

        assertEquals("description", found.getString("textField").getValue());
        assertEquals("embedding", found.getString("embeddingField").getValue());
    }

    // -- asDocumentList ------------------------------------------------------

    @Test
    public void nullContent_returnsEmptyList() {
        assertTrue(AutoEmbeddingInterceptor.asDocumentList(null).isEmpty());
    }

    @Test
    public void singleDocument_becomesOneElementList() {
        var doc = new BsonDocument("a", new BsonInt32(1));
        var docs = AutoEmbeddingInterceptor.asDocumentList(doc);
        assertEquals(List.of(doc), docs);
    }

    @Test
    public void arrayContent_filtersToDocumentsOnly() {
        var doc1 = new BsonDocument("a", new BsonInt32(1));
        var doc2 = new BsonDocument("b", new BsonInt32(2));
        var arr = new BsonArray(List.of(doc1, new BsonString("not a document"), doc2));

        var docs = AutoEmbeddingInterceptor.asDocumentList(arr);

        assertEquals(List.of(doc1, doc2), docs);
    }

    // -- collectEmbeddableTexts -----------------------------------------------

    @Test
    public void collectEmbeddableTexts_skipsDocumentsWithoutStringTextField() {
        var withText = new BsonDocument("description", new BsonString("hello world"));
        var withoutText = new BsonDocument("other", new BsonInt32(1));
        var withNonStringText = new BsonDocument("description", new BsonInt32(42));

        var targets = new ArrayList<BsonDocument>();
        var texts = new ArrayList<String>();
        AutoEmbeddingInterceptor.collectEmbeddableTexts(
            List.of(withText, withoutText, withNonStringText), "description", targets, texts);

        assertEquals(List.of(withText), targets);
        assertEquals(List.of("hello world"), texts);
    }

    // -- applyEmbeddings -------------------------------------------------------

    @Test
    public void applyEmbeddings_appendsVectorAsBsonDoubleArray() {
        var doc = new BsonDocument("description", new BsonString("hello"));
        AutoEmbeddingInterceptor.applyEmbeddings(List.of(doc), List.of(new float[] {0.1f, 0.2f}), "embedding");

        var arr = doc.getArray("embedding");
        assertEquals(2, arr.size());
        assertEquals(0.1, arr.get(0).asDouble().getValue(), 1e-6);
        assertEquals(0.2, arr.get(1).asDouble().getValue(), 1e-6);
    }

    @Test
    public void applyEmbeddings_skipsTargetWithNullVector() {
        var doc1 = new BsonDocument("description", new BsonString("hello"));
        var doc2 = new BsonDocument("description", new BsonString("world"));
        var vectors = new ArrayList<float[]>();
        vectors.add(null);
        vectors.add(new float[] {1.0f});

        AutoEmbeddingInterceptor.applyEmbeddings(List.of(doc1, doc2), vectors, "embedding");

        assertTrue(!doc1.containsKey("embedding"));
        assertArrayEquals(new double[] {1.0}, new double[] {doc2.getArray("embedding").get(0).asDouble().getValue()});
    }

    @Test
    public void applyEmbeddings_fewerVectorsThanTargets_leavesExtraTargetsUntouched() {
        var doc1 = new BsonDocument("description", new BsonString("hello"));
        var doc2 = new BsonDocument("description", new BsonString("world"));

        AutoEmbeddingInterceptor.applyEmbeddings(
            List.of(doc1, doc2), List.of(new float[] {1.0f}), "embedding");

        assertTrue(doc1.containsKey("embedding"));
        assertTrue(!doc2.containsKey("embedding"));
    }
}
