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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

public class DocumentChunkingInterceptorTest {

    @Test
    public void nullText_returnsNoChunks() {
        assertEquals(List.of(), DocumentChunkingInterceptor.splitIntoChunks(null, 1000, 200));
    }

    @Test
    public void emptyText_returnsNoChunks() {
        assertEquals(List.of(), DocumentChunkingInterceptor.splitIntoChunks("", 1000, 200));
    }

    @Test
    public void nonPositiveSize_returnsNoChunks() {
        assertEquals(List.of(), DocumentChunkingInterceptor.splitIntoChunks("some text", 0, 0));
        assertEquals(List.of(), DocumentChunkingInterceptor.splitIntoChunks("some text", -1, 0));
    }

    @Test
    public void textShorterThanChunkSize_returnsSingleChunk() {
        var chunks = DocumentChunkingInterceptor.splitIntoChunks("a short document", 1000, 200);
        assertEquals(List.of("a short document"), chunks);
    }

    @Test
    public void textLongerThanChunkSize_splitsOnWordBoundariesWithOverlap() {
        // "aaaa bbbb cccc dddd" (19 chars), size=10, overlap=3.
        // Walked through by hand against DocumentChunkingInterceptor.splitIntoChunks:
        //  start=0  end=10 -> trimmed to word boundary at 9  -> "aaaa bbbb", step=6
        //  start=6  end=16 -> trimmed to word boundary at 14 -> "bbb cccc",  step=5
        //  start=11 end=19 (== len, no trim)                -> "ccc dddd",  step=5
        //  start=16 end=19 (== len, no trim)                -> "ddd",       step<=0 -> falls back to size
        //  start=26 >= len(19) -> loop ends
        var text = "aaaa bbbb cccc dddd";
        var chunks = DocumentChunkingInterceptor.splitIntoChunks(text, 10, 3);
        assertEquals(List.of("aaaa bbbb", "bbb cccc", "ccc dddd", "ddd"), chunks);
    }

    @Test
    public void zeroOverlap_chunksDoNotRepeatContent() {
        var text = "aaaa bbbb cccc dddd";
        var chunks = DocumentChunkingInterceptor.splitIntoChunks(text, 10, 0);
        // no chunk should be empty, and every chunk must be non-blank
        chunks.forEach(c -> assertTrue(!c.isBlank(), "chunk should not be blank: [" + c + "]"));
    }

    @Test
    public void overlapGreaterThanSize_stillTerminatesAndCoversText() {
        // overlap > size would make (end - start - overlap) negative on every
        // iteration if not guarded; splitIntoChunks falls back to step = size
        // in that case. This must not loop forever and must make forward
        // progress until the whole text has been consumed.
        var text = "aaaa bbbb cccc dddd eeee ffff gggg";
        var chunks = DocumentChunkingInterceptor.splitIntoChunks(text, 5, 50);

        assertTrue(!chunks.isEmpty());
        // every word from the source text must appear in at least one chunk
        for (var word : text.split(" ")) {
            assertTrue(chunks.stream().anyMatch(c -> c.contains(word)),
                "word '" + word + "' missing from chunks " + chunks);
        }
    }

    @Test
    public void chunks_areStrippedOfLeadingAndTrailingWhitespace() {
        var chunks = DocumentChunkingInterceptor.splitIntoChunks("  padded text  ", 1000, 200);
        assertEquals(List.of("padded text"), chunks);
    }
}
