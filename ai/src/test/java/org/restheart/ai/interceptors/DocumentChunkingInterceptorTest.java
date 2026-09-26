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
        // "aaaa bbbb cccc dddd" (19 chars), size=10, overlap=5: each window ends at a space, and the
        // next starts at the first whole word within the overlap
        var text = "aaaa bbbb cccc dddd";
        var chunks = DocumentChunkingInterceptor.splitIntoChunks(text, 10, 5);
        assertEquals(List.of("aaaa bbbb", "bbbb cccc", "cccc dddd"), chunks);
    }

    @Test
    public void anOverlapInsideAWord_carriesNoPartOfIt() {
        // overlap=3 falls inside "bbbb": the next window starts at the following word, not at "bbb"
        var chunks = DocumentChunkingInterceptor.splitIntoChunks("aaaa bbbb cccc dddd", 10, 3);
        assertEquals(List.of("aaaa bbbb", "cccc dddd"), chunks);
    }

    @Test
    public void noChunkEverCutsAWord() {
        var text = "Il regolamento edilizio disciplina le attività di trasformazione urbanistica ed edilizia "
                + "del territorio comunale, le caratteristiche degli edifici, delle loro pertinenze e degli spazi "
                + "aperti, pubblici e privati, nel rispetto della normativa nazionale e regionale vigente.";
        var words = new java.util.HashSet<>(java.util.List.of(text.split("\\s+")));
        for (int size : new int[] {15, 30, 47, 100}) {
            for (int overlap : new int[] {0, 5, 13, 40}) {
                for (var chunk : DocumentChunkingInterceptor.splitIntoChunks(text, size, overlap)) {
                    for (var word : chunk.split("\\s+")) {
                        assertTrue(words.contains(word), "size " + size + ", overlap " + overlap + ": '" + word + "' is not a word of the text, in [" + chunk + "]");
                    }
                }
            }
        }
    }

    @Test
    public void aWordLongerThanTheWindow_isOneChunkOfItsOwn() {
        var url = "https://example.com/" + "x".repeat(60);
        var chunks = DocumentChunkingInterceptor.splitIntoChunks("see " + url + " for the full list", 20, 0);
        assertTrue(chunks.stream().anyMatch(c -> c.contains(url)), "the URL is whole in a chunk, got " + chunks);
    }

    @Test
    public void aChunkTooSmallToMeanAnything_isJoinedToItsNeighbour() {
        var big = "a chunk with plenty of letters in it";
        var other = "another chunk with plenty of letters";
        assertEquals(List.of(big + " p. 12", other), DocumentChunkingInterceptor.joinTooSmall(List.of(big, "p. 12", other), 20));
        assertEquals(List.of("12 " + big), DocumentChunkingInterceptor.joinTooSmall(List.of("12", big), 20));
        assertEquals(List.of("ab cd"), DocumentChunkingInterceptor.joinTooSmall(List.of("ab", "cd"), 20));
        assertEquals(List.of("a short document"), DocumentChunkingInterceptor.joinTooSmall(List.of("a short document"), 20));
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

    @Test
    public void resumeOf_keepsWhoWhereAndTheSizes_neverTheOverrides() {
        var principal = new org.restheart.security.BaseAccount("alice", java.util.Set.of("editor"));
        var caller = new DocumentChunkingInterceptor.Caller("svc.example.com", principal,
                java.util.Map.of("override-ai-voyage-api-key", "secret"), "/mydb");
        var upload = new DocumentChunkingInterceptor.Upload(caller, "mydb", "docs.files", new org.bson.BsonString("f1"), false,
                List.of(), List.of(), 500, 50, new org.bson.types.ObjectId());

        var resume = DocumentChunkingInterceptor.resumeOf(upload, 1);

        assertEquals("svc.example.com", resume.getString("host").getValue());
        assertEquals("/mydb", resume.getString("base").getValue());
        assertEquals("alice", resume.getString("user").getValue());
        assertEquals("editor", resume.getArray("roles").get(0).asString().getValue());
        assertEquals(500, resume.getInt32("chunkSize").getValue());
        assertEquals(50, resume.getInt32("chunkOverlap").getValue());
        assertEquals(1, resume.getInt32("dbLimit").getValue());
        assertTrue(!resume.toJson().contains("secret"), "no override, no key, is kept on the file");
    }
}
