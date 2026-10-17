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
package org.restheart.ai.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

public class BucketChunkingConfigTest {

    private static BsonDocument props(String chunking) {
        return BsonDocument.parse("{ \"chunking\": " + chunking + " }");
    }

    private static String reason(String chunking) {
        return BucketChunkingConfig.invalidReason(BsonDocument.parse("{ \"v\": " + chunking + " }").get("v"));
    }

    private static final String TWO_RULES = """
            [ { "name": "manuals",
                "filter": { "contentType": ["application/pdf", "application/vnd.openxmlformats-officedocument.*"] },
                "target-collection": "manuals_chunks", "chunk-size": 1000, "chunk-overlap": 200 },
              { "name": "code", "filter": { "extension": [".java", "ts", ".PY"] },
                "target-collection": "code_chunks", "splitter": "text" } ]""";

    // -- declares and rules ---------------------------------------------------------------

    @Test
    public void declares_whenTheKeyIsThere_evenEmpty() {
        assertTrue(BucketChunkingConfig.declares(props("[]")));
        assertTrue(BucketChunkingConfig.declares(props(TWO_RULES)));
        assertFalse(BucketChunkingConfig.declares(new BsonDocument()));
        assertFalse(BucketChunkingConfig.declares(null));
        assertFalse(BucketChunkingConfig.declares(props("null")));
    }

    @Test
    public void rules_readInOrder_withTheirValues() {
        var rules = BucketChunkingConfig.rules(props(TWO_RULES));
        assertEquals(2, rules.size());
        var manuals = rules.get(0);
        assertEquals("manuals", manuals.name());
        assertEquals("manuals_chunks", manuals.targetCollection());
        assertEquals(1000, manuals.chunkSize());
        assertEquals(200, manuals.chunkOverlap());
        assertEquals("auto", manuals.splitter());
        var code = rules.get(1);
        assertEquals("text", code.splitter());
        assertNull(code.chunkSize());
        assertNull(code.metadata());
    }

    @Test
    public void rules_aSingleObjectIsAListOfOne_andARuleWithoutTargetIsSkipped() {
        assertEquals(1, BucketChunkingConfig.rules(props("{ \"target-collection\": \"c\" }")).size());
        assertEquals(1, BucketChunkingConfig.rules(props("[ { \"name\": \"x\" }, { \"target-collection\": \"c\" } ]")).size());
    }

    // -- matching -------------------------------------------------------------------------

    @Test
    public void contentType_exactOrTrailingWildcard_caseInsensitive_parametersIgnored() {
        var manuals = BucketChunkingConfig.rules(props(TWO_RULES)).get(0);
        assertTrue(manuals.matches("application/pdf", "a.pdf"));
        assertTrue(manuals.matches("Application/PDF; charset=x", "a.pdf"));
        assertTrue(manuals.matches("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "a.docx"));
        assertFalse(manuals.matches("image/png", "a.png"));
        assertFalse(manuals.matches(null, "a.pdf"));
    }

    @Test
    public void extension_withOrWithoutTheDot_caseInsensitive() {
        var code = BucketChunkingConfig.rules(props(TWO_RULES)).get(1);
        assertTrue(code.matches(null, "Main.java"));
        assertTrue(code.matches(null, "app.TS"));
        assertTrue(code.matches(null, "script.py"));
        assertFalse(code.matches(null, "notes.txt"));
        assertFalse(code.matches(null, null));
    }

    @Test
    public void noFilter_matchesEveryFile_andBothKeysMustMatchWhenPresent() {
        var any = BucketChunkingConfig.rules(props("{ \"target-collection\": \"c\" }")).get(0);
        assertTrue(any.matches(null, null));
        var both = BucketChunkingConfig.rules(props("{ \"target-collection\": \"c\", \"filter\": { \"contentType\": \"text/*\", \"extension\": \".md\" } }")).get(0);
        assertTrue(both.matches("text/plain", "readme.md"));
        assertFalse(both.matches("text/plain", "readme.txt"));
        assertFalse(both.matches("application/pdf", "readme.md"));
    }

    @Test
    public void metadataQuery_putsEachFieldUnderMetadata_throughAndOrNor() {
        var q = BucketChunkingConfig.metadataQuery(BsonDocument.parse("""
                { "kind": "manual", "size": { "$gt": 3 }, "$or": [ { "lang": "en" }, { "lang": { "$in": ["it"] } } ] }"""));
        assertEquals(BsonDocument.parse("""
                { "metadata.kind": "manual", "metadata.size": { "$gt": 3 },
                  "$or": [ { "metadata.lang": "en" }, { "metadata.lang": { "$in": ["it"] } } ] }"""), q);
    }

    // -- invalidReason --------------------------------------------------------------------

    @Test
    public void invalidReason_validForms_null() {
        assertNull(reason(TWO_RULES));
        assertNull(reason("{ \"target-collection\": \"c\" }"));
        assertNull(reason("[]"));
        assertNull(reason("null"));
        assertNull(reason("{ \"target-collection\": \"c\", \"filter\": { \"metadata\": { \"kind\": \"manual\" } } }"));
    }

    @Test
    public void invalidReason_namesTheRule() {
        assertEquals("chunking[1] needs a non-blank string target-collection", reason("[ { \"target-collection\": \"c\" }, { \"name\": \"x\" } ]"));
        assertTrue(reason("\"yes\"").startsWith("chunking must be an object"));
        assertEquals("chunking[0] must be an object with target-collection", reason("[ 3 ]"));
    }

    @Test
    public void invalidReason_unknownKeys_inTheRuleOrInTheFilter() {
        assertTrue(reason("{ \"target-collection\": \"c\", \"targetCollection\": \"d\" }").startsWith("chunking: unknown key 'targetCollection'"));
        assertTrue(reason("{ \"target-collection\": \"c\", \"filter\": { \"mime\": \"x\" } }").startsWith("chunking: unknown filter key 'mime'"));
    }

    @Test
    public void invalidReason_valuesOfTheWrongKind() {
        assertEquals("chunking: filter.extension must be a string or a list of strings", reason("{ \"target-collection\": \"c\", \"filter\": { \"extension\": [1] } }"));
        assertEquals("chunking: filter.metadata must be a MongoDB filter, an object", reason("{ \"target-collection\": \"c\", \"filter\": { \"metadata\": \"manual\" } }"));
        assertEquals("chunking: chunk-size must be a positive number", reason("{ \"target-collection\": \"c\", \"chunk-size\": 0 }"));
        assertEquals("chunking: chunk-overlap must be smaller than chunk-size", reason("{ \"target-collection\": \"c\", \"chunk-size\": 100, \"chunk-overlap\": 100 }"));
        assertEquals("chunking: name must be a non-blank string", reason("{ \"target-collection\": \"c\", \"name\": 3 }"));
    }

    @Test
    public void invalidReason_embeddingKeys_pointToTheTargetCollection() {
        for (var key : new String[]{"provider", "model", "dimensions", "groupBy"}) {
            var r = reason("{ \"target-collection\": \"c\", \"" + key + "\": \"x\" }");
            assertTrue(r.startsWith("chunking: '" + key + "' does not belong in a chunking rule"), r);
            assertTrue(r.contains("vectorSearch rules of the target collection"), r);
        }
    }

    @Test
    public void splitter_autoByDefault_andOnlyTheThreeNames() {
        assertEquals("auto", BucketChunkingConfig.rules(props("{ \"target-collection\": \"c\" }")).get(0).splitter());
        assertEquals("text", BucketChunkingConfig.rules(props("{ \"target-collection\": \"c\", \"splitter\": \"TEXT\" }")).get(0).splitter());
        assertNull(reason("{ \"target-collection\": \"c\", \"splitter\": \"code\" }"));
        assertEquals("chunking: splitter must be one of [auto, code, text]", reason("{ \"target-collection\": \"c\", \"splitter\": \"tika\" }"));
    }

    @Test
    public void invalidReason_codeOperatorsInTheMetadataFilter_refusedAtAnyDepth() {
        assertEquals("chunking: filter.metadata may not use $where", reason("{ \"target-collection\": \"c\", \"filter\": { \"metadata\": { \"$where\": \"1\" } } }"));
        assertEquals("chunking: filter.metadata may not use $function",
                reason("{ \"target-collection\": \"c\", \"filter\": { \"metadata\": { \"$or\": [ { \"$expr\": { \"$function\": {} } } ] } } }"));
    }
}
