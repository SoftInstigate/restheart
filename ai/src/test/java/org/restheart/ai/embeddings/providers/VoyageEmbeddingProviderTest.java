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
package org.restheart.ai.embeddings.providers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

public class VoyageEmbeddingProviderTest {

    @Test
    public void inputTypeOmitted_whenNotConfigured() {
        var payload = VoyageEmbeddingProvider.buildPayload("voyage-3.5", List.of("hello"), "");
        assertFalse(payload.contains("input_type"), "empty input-type must not be sent as a field");
    }

    @Test
    public void inputTypeOmitted_whenNull() {
        var payload = VoyageEmbeddingProvider.buildPayload("voyage-3.5", List.of("hello"), null);
        assertFalse(payload.contains("input_type"));
    }

    @Test
    public void inputTypeIncluded_whenConfigured() {
        var payload = VoyageEmbeddingProvider.buildPayload("voyage-3.5", List.of("hello"), "query");

        assertTrue(payload.contains("\"input_type\":\"query\""));

        // and the payload must still be valid JSON with the expected fields
        var parsed = BsonDocument.parse(payload);
        assertTrue(parsed.getString("model").getValue().equals("voyage-3.5"));
        assertTrue(parsed.getString("input_type").getValue().equals("query"));
        assertTrue(parsed.getArray("input").size() == 1);
    }

    @Test
    public void multipleTexts_allPresentInInputArray() {
        var payload = VoyageEmbeddingProvider.buildPayload("voyage-3.5", List.of("a", "b", "c"), null);
        var parsed = BsonDocument.parse(payload);
        assertTrue(parsed.getArray("input").size() == 3);
    }
}
