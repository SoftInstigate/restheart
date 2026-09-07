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
package org.restheart.ai.rerank.providers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

public class CohereRerankProviderTest {

    @Test
    public void topNOmitted_whenNonPositive() {
        var payload = CohereRerankProvider.buildPayload("rerank-v3.5", "q", List.of("a", "b"), 0);
        assertFalse(payload.contains("top_n"));
    }

    @Test
    public void topNIncluded_whenPositive() {
        var payload = CohereRerankProvider.buildPayload("rerank-v3.5", "q", List.of("a", "b"), 5);
        assertTrue(payload.contains("\"top_n\":5"));

        var parsed = BsonDocument.parse(payload);
        assertTrue(parsed.getString("model").getValue().equals("rerank-v3.5"));
        assertTrue(parsed.getString("query").getValue().equals("q"));
        assertTrue(parsed.getArray("documents").size() == 2);
    }
}
