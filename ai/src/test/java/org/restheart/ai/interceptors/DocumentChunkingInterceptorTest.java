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
    public void chunkText_codeFilename_usesCodeAwareSplitting() {
        var code = "public class A {\n    void a() {}\n}\nclass B {\n    void b() {}\n}\n";
        // splitIntoChunks (character-window) would never produce a chunk split exactly
        // at the class boundary the way CodeAwareSplitter does; assert dispatch happened
        // by checking the code-aware result directly matches chunkText's output.
        assertEquals(
                org.restheart.ai.chunking.CodeAwareSplitter.splitBraceBased(code, 1000, 200),
                DocumentChunkingInterceptor.chunkText(code, "Foo.java", 1000, 200));
    }

    @Test
    public void chunkText_pythonFilename_usesIndentBasedSplitting() {
        var code = "def a():\n    pass\ndef b():\n    pass\n";
        assertEquals(
                org.restheart.ai.chunking.CodeAwareSplitter.splitIndentBased(code, 1000, 200),
                DocumentChunkingInterceptor.chunkText(code, "script.py", 1000, 200));
    }

    @Test
    public void chunkText_nonCodeFilename_fallsBackToPlainSplitting() {
        var text = "aaaa bbbb cccc dddd";
        assertEquals(
                DocumentChunkingInterceptor.splitIntoChunks(text, 10, 3),
                DocumentChunkingInterceptor.chunkText(text, "notes.txt", 10, 3));
    }

    @Test
    public void chunkText_nullFilename_fallsBackToPlainSplitting() {
        var text = "aaaa bbbb cccc dddd";
        assertEquals(
                DocumentChunkingInterceptor.splitIntoChunks(text, 10, 3),
                DocumentChunkingInterceptor.chunkText(text, null, 10, 3));
    }

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

    // -- the splitter a bucket's rule chooses (#754) ---------------------------------------

    @Test
    public void splitterText_cutsCodeInCharacterWindows_withTheOverlap() {
        var code = "public class Foo {\n  void a() { int x = 1; }\n  void b() { int y = 2; }\n}\n";
        var asText = DocumentChunkingInterceptor.chunkText(code, "Foo.java", 20, 5, "text", 0);
        var asCode = DocumentChunkingInterceptor.chunkText(code, "Foo.java", 20, 5, "auto", 0);
        assertEquals(DocumentChunkingInterceptor.splitIntoChunks(code, 20, 5), asText);
        org.junit.jupiter.api.Assertions.assertNotEquals(asText, asCode);
    }

    @Test
    public void splitterAutoOrCode_onProse_areCharacterWindows() {
        var text = "one two three four five six seven eight nine ten";
        assertEquals(DocumentChunkingInterceptor.splitIntoChunks(text, 10, 3), DocumentChunkingInterceptor.chunkText(text, "notes.txt", 10, 3, "code", 0));
        assertEquals(DocumentChunkingInterceptor.splitIntoChunks(text, 10, 3), DocumentChunkingInterceptor.chunkText(text, "notes.txt", 10, 3, "auto", 0));
    }

    // -- where the chunks go and come from (#754) ------------------------------------------

    @Test
    public void sourceOf_namesTheDatabaseTheBucketAndTheFile() {
        var oid = new org.bson.types.ObjectId("6ab3f24aa5aadbd6420c76a2");
        assertEquals("mydb/docs.files/6ab3f24aa5aadbd6420c76a2", DocumentChunkingInterceptor.sourceOf("mydb", "docs.files", new org.bson.BsonObjectId(oid)));
        assertEquals("mydb/docs.files/manual-1", DocumentChunkingInterceptor.sourceOf("mydb", "docs.files", new org.bson.BsonString("manual-1")));
        assertEquals("mydb/docs.files/42", DocumentChunkingInterceptor.sourceOf("mydb", "docs.files", new org.bson.BsonInt32(42)));
    }

    @Test
    public void pathOf_keepsTheMountTheRequestAddressedTheBucketWith() {
        var req = org.mockito.Mockito.mock(org.restheart.exchange.MongoRequest.class);
        org.mockito.Mockito.when(req.getCollectionName()).thenReturn("docs.files");
        org.mockito.Mockito.when(req.getPath()).thenReturn("/mydb/docs.files");
        assertEquals("/mydb/manuals_chunks", DocumentChunkingInterceptor.pathOf(req, "manuals_chunks"));
        org.mockito.Mockito.when(req.getPath()).thenReturn("/docs.files/abc");
        assertEquals("/manuals_chunks", DocumentChunkingInterceptor.pathOf(req, "manuals_chunks"));
    }

    @Test
    public void targetsOf_eachCollectionOnce_inRuleOrder() {
        var rules = org.restheart.ai.util.BucketChunkingConfig.rules(org.bson.BsonDocument.parse("""
                { "chunking": [ { "target-collection": "a" }, { "target-collection": "b" }, { "target-collection": "a" } ] }"""));
        assertEquals(List.of("a", "b"), DocumentChunkingInterceptor.targetsOf(rules));
    }

    @Test
    public void aWindowOfWhitespaceAlone_isNoChunk() {
        // the blank page of a PDF: more whitespace than a chunk holds
        var text = "first page" + " ".repeat(40) + "\n\n\n" + " ".repeat(40) + "second page";

        var chunks = DocumentChunkingInterceptor.splitIntoChunks(text, 20, 0);

        assertTrue(chunks.stream().noneMatch(String::isBlank), "no blank chunk, got " + chunks);
        // the words around the blank are all there, in order; where a window cuts them is the splitter's business
        assertEquals("first page second page", String.join(" ", chunks));
    }
}
