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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class RerankWireParsingTest {

    @Test
    public void parsesCohereShapedResponse() {
        var body = "{\"results\":["
            + "{\"index\":2,\"relevance_score\":0.9},"
            + "{\"index\":0,\"relevance_score\":0.5}"
            + "],\"id\":\"abc\"}";

        var results = RerankWireParsing.parse(body, "results");

        assertEquals(2, results.size());
        assertEquals(2, results.get(0).index());
        assertEquals(0.9, results.get(0).score(), 1e-9);
        assertEquals(0, results.get(1).index());
        assertEquals(0.5, results.get(1).score(), 1e-9);
    }

    @Test
    public void parsesVoyageShapedResponse() {
        var body = "{\"object\":\"list\",\"data\":["
            + "{\"relevance_score\":0.455,\"index\":0},"
            + "{\"relevance_score\":0.439,\"index\":1}"
            + "],\"model\":\"rerank-2.5-lite\",\"usage\":{\"total_tokens\":8}}";

        var results = RerankWireParsing.parse(body, "data");

        assertEquals(2, results.size());
        assertEquals(0, results.get(0).index());
        assertEquals(0.455, results.get(0).score(), 1e-9);
        assertEquals(1, results.get(1).index());
        assertEquals(0.439, results.get(1).score(), 1e-9);
    }

    @Test
    public void emptyArray_returnsEmptyList() {
        assertTrue(RerankWireParsing.parse("{\"results\":[]}", "results").isEmpty());
    }

    @Test
    public void escape_handlesQuotesAndBackslashes() {
        var escaped = RerankWireParsing.escape("a \"quoted\" \\value\n");
        assertEquals("a \\\"quoted\\\" \\\\value\\n", escaped);
    }
}
