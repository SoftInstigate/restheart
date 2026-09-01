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
package org.restheart.ai.chunking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Ported from Sophia's {@code CodeAwareSplitterTest}, adapted to this module's
 * plain-{@code List<String>} splitter API (no LangChain4j Document/TextSegment).
 */
public class CodeAwareSplitterTest {

    private static List<String> split(String code, int maxChunkSize) {
        return CodeAwareSplitter.splitBraceBased(code, maxChunkSize, 0);
    }

    private static List<String> splitPython(String code, int maxChunkSize) {
        return CodeAwareSplitter.splitIndentBased(code, maxChunkSize, 0);
    }

    // -------------------------------------------------------------------------
    // Java / brace-based
    // -------------------------------------------------------------------------

    @Test
    public void smallClass_fitsInOneChunk() {
        var code = """
                public class Foo {
                    public void hello() {
                        System.out.println("hi");
                    }
                }
                """;
        assertEquals(1, split(code, 2000).size());
    }

    @Test
    public void javaClass_splitsAtMethodBoundaries() {
        // Two methods, each ~300 chars; class header included in each chunk.
        // maxChunkSize=400 forces a split between the two methods.
        var method1 = """
                    /** Method one javadoc. */
                    public void methodOne() {
                        String a = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
                        String b = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
                        System.out.println(a + b);
                    }
                """;
        var method2 = """
                    /** Method two javadoc. */
                    public void methodTwo() {
                        String c = "cccccccccccccccccccccccccccccccccccccccccccccc";
                        String d = "dddddddddddddddddddddddddddddddddddddddddddddd";
                        System.out.println(c + d);
                    }
                """;
        var code = "public class Bar {\n" + method1 + method2 + "}\n";

        var chunks = split(code, 400);
        assertTrue(chunks.size() >= 2, "Expected at least 2 chunks, got " + chunks.size());

        // Every chunk must contain the class header for context
        for (var chunk : chunks) {
            assertTrue(chunk.contains("public class Bar {"), "Chunk missing class header:\n" + chunk);
        }
    }

    @Test
    public void javadocStaysWithItsMethod() {
        var code = """
                public class Baz {
                    /** First method docs aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa. */
                    public void first() {
                        String x = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
                        System.out.println(x);
                    }
                    /** Second method docs bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb. */
                    public void second() {
                        String y = "yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy";
                        System.out.println(y);
                    }
                }
                """;

        var chunks = split(code, 350);
        assertTrue(chunks.size() >= 2);

        var chunkWithFirst  = chunks.stream().filter(c -> c.contains("void first()")).findFirst();
        var chunkWithSecond = chunks.stream().filter(c -> c.contains("void second()")).findFirst();

        assertTrue(chunkWithFirst.isPresent());
        assertTrue(chunkWithSecond.isPresent());

        assertTrue(chunkWithFirst.get().contains("First method docs"), "Javadoc for first() not in same chunk");
        assertTrue(chunkWithSecond.get().contains("Second method docs"), "Javadoc for second() not in same chunk");
    }

    @Test
    public void braceInsideStringLiteralNotCounted() {
        var code = """
                public class Strings {
                    public String template() {
                        return "Hello { world }";
                    }
                }
                """;
        var chunks = split(code, 2000);
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).contains("Hello { world }"));
    }

    @Test
    public void braceInsideLineCommentNotCounted() {
        var code = """
                public class Comments {
                    public void foo() {
                        // This is a comment with { braces }
                        int x = 1;
                    }
                }
                """;
        assertEquals(1, split(code, 2000).size());
    }

    @Test
    public void braceInsideBlockCommentNotCounted() {
        var code = """
                public class BlockComments {
                    /* { open without close */
                    public void bar() {
                        int y = 2;
                    }
                }
                """;
        assertEquals(1, split(code, 2000).size());
    }

    @Test
    public void emptyClass_returnsSingleChunk() {
        var chunks = split("public class Empty {}\n", 2000);
        assertEquals(1, chunks.size());
    }

    @Test
    public void topLevelFunction_notWrappedInClass() {
        // C-style: two top-level functions, each should be its own chunk
        var code = """
                void funcA() {
                    int a = 1;
                    int b = 2;
                }
                void funcB() {
                    int c = 3;
                    int d = 4;
                }
                """;
        var chunks = split(code, 2000);
        assertEquals(2, chunks.size());
        assertTrue(chunks.get(0).contains("funcA"));
        assertTrue(chunks.get(1).contains("funcB"));
    }

    @Test
    public void oversizedMethod_allPiecesHaveClassHeader() {
        // A class with one method whose body alone exceeds maxChunkSize=200
        var body = "x".repeat(250);
        var code = """
                public class Big {
                    public void huge() {
                        String s = "%s";
                        System.out.println(s);
                    }
                }
                """.formatted(body);

        var chunks = split(code, 200);
        assertTrue(chunks.size() > 1, "Expected the oversized method to be split");
        for (var chunk : chunks) {
            assertTrue(chunk.contains("public class Big {"), "Chunk is missing the class header");
        }
    }

    @Test
    public void zeroOverlap_lineFallbackChunksDoNotRepeatContent() {
        // No outer brace at all -> falls straight to splitByLines. Lines are distinct
        // (not a repeating pattern) so any accidental duplication is detectable --
        // a repeating pattern could make two consecutive chunks coincide by chance.
        var source = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            source.append("line ").append(i).append('\n');
        }

        var chunks = CodeAwareSplitter.splitBraceBased(source.toString(), 100, 0);
        assertTrue(chunks.size() > 1);

        var seenLines = new ArrayList<String>();
        for (var chunk : chunks) {
            for (var line : chunk.split("\n")) {
                if (!line.isBlank()) seenLines.add(line);
            }
        }

        var expectedLines = new ArrayList<String>();
        for (int i = 0; i < 40; i++) expectedLines.add("line " + i);

        // with overlap=0 every source line must appear exactly once, in order --
        // no line duplicated across chunk boundaries, none dropped
        assertEquals(expectedLines, seenLines);
    }

    // -------------------------------------------------------------------------
    // Python / indent-based
    // -------------------------------------------------------------------------

    @Test
    public void python_splitsAtTopLevelFunctions() {
        var code = """
                def func_a():
                    x = 1
                    return x

                def func_b():
                    y = 2
                    return y
                """;
        var chunks = splitPython(code, 2000);
        assertEquals(2, chunks.size());
        assertTrue(chunks.get(0).contains("func_a"));
        assertTrue(chunks.get(1).contains("func_b"));
    }

    @Test
    public void python_splitsAtTopLevelClasses() {
        var code = """
                class Foo:
                    def method(self):
                        pass

                class Bar:
                    def other(self):
                        pass
                """;
        var chunks = splitPython(code, 2000);
        assertEquals(2, chunks.size());
        assertTrue(chunks.get(0).contains("class Foo"));
        assertTrue(chunks.get(1).contains("class Bar"));
    }
}
