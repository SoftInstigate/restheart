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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Splits source code at natural structural boundaries (function / class / method
 * level) rather than at arbitrary character counts, for two structural families:
 *
 * <ul>
 * <li>{@link #splitBraceBased brace-based} — C, C++, Java, Kotlin, JavaScript,
 * TypeScript, Go, Rust, Swift, C#, ...
 * <li>{@link #splitIndentBased indent-based} — Python
 * </ul>
 *
 * <p>Strategy:
 * <ol>
 * <li>Split the file at top-level brace blocks (or top-level def/class for Python).
 * <li>If a block exceeds {@code maxChunkSize}, re-split at the next inner depth level.
 * <li>If still too large, fall back to line-based chunking with character overlap.
 * </ol>
 *
 * <p>Brace counting is comment- and string-literal-aware (handles {@code //} line
 * comments, block comments, double- and single-quoted strings with backslash
 * escapes). Multi-line raw strings are not fully modelled but the heuristic is
 * robust enough for the RAG use-case this exists for.
 *
 * <p>Ported from Sophia's {@code com.softinstigate.sophia.splitter.CodeAwareSplitter}.
 */
public final class CodeAwareSplitter {

    private CodeAwareSplitter() {
    }

    /** Splits brace-delimited source (Java, Kotlin, JS/TS, C-family, Go, Rust, Swift, ...). */
    public static List<String> splitBraceBased(String text, int maxChunkSize, int overlap) {
        var blocks = splitAtDepth(text.split("\n", -1), 0);
        return finishSplitting(blocks, maxChunkSize, overlap);
    }

    /** Splits indentation-based source (Python). */
    public static List<String> splitIndentBased(String text, int maxChunkSize, int overlap) {
        var blocks = splitByIndent(text);
        return finishSplitting(blocks, maxChunkSize, overlap);
    }

    private static List<String> finishSplitting(List<String> blocks, int maxChunkSize, int overlap) {
        var chunks = new ArrayList<String>();
        for (var block : blocks) {
            if (block.isBlank()) {
                continue;
            }
            if (block.length() <= maxChunkSize) {
                chunks.add(block.stripTrailing());
            } else {
                for (var piece : splitLargeBlock(block, maxChunkSize, overlap)) {
                    if (!piece.isBlank()) {
                        chunks.add(piece.stripTrailing());
                    }
                }
            }
        }
        return chunks;
    }

    // =========================================================================
    // BRACE-BASED
    // =========================================================================

    /**
     * Generic depth-aware split: emits a block each time the brace depth
     * returns to {@code targetDepth} after having been deeper.
     */
    private static List<String> splitAtDepth(String[] lines, int targetDepth) {
        List<String> blocks  = new ArrayList<>();
        List<String> current = new ArrayList<>();

        int     depth          = targetDepth;
        boolean inBlockComment = false;
        boolean insideBlock    = false;

        for (var line : lines) {
            int[] result   = braceChange(line, inBlockComment);
            int   change   = result[0];
            inBlockComment = result[1] == 1;

            int prevDepth = depth;
            depth = Math.max(targetDepth, depth + change);

            current.add(line);

            if (!insideBlock && depth > targetDepth) {
                insideBlock = true;
            }

            // Block completed: returned to targetDepth after going deeper
            if (insideBlock && prevDepth > targetDepth && depth == targetDepth) {
                blocks.add(String.join("\n", current));
                current.clear();
                insideBlock = false;
            }
        }

        if (!current.isEmpty()) {
            var trailing = String.join("\n", current);
            if (!blocks.isEmpty() && !insideBlock) {
                // Merge trailing non-block lines (e.g. closing comments) into last block
                blocks.set(blocks.size() - 1,
                        blocks.get(blocks.size() - 1) + "\n" + trailing);
            } else {
                blocks.add(trailing);
            }
        }

        return blocks;
    }

    /**
     * Counts the net brace depth change for a single source line,
     * ignoring braces inside string literals and comments.
     *
     * @return int[2] { netChange, inBlockComment_after (0 or 1) }
     */
    private static int[] braceChange(String line, boolean startInBlockComment) {
        int     change         = 0;
        boolean inBlockComment = startInBlockComment;
        boolean inDoubleQuote  = false;
        boolean inSingleQuote  = false;

        for (int i = 0; i < line.length(); i++) {
            char c    = line.charAt(i);
            char next = (i + 1 < line.length()) ? line.charAt(i + 1) : 0;

            if (inBlockComment) {
                if (c == '*' && next == '/') { inBlockComment = false; i++; }
                continue;
            }

            if (inDoubleQuote) {
                if (c == '\\') { i++; continue; }   // escape sequence
                if (c == '"')  inDoubleQuote = false;
                continue;
            }

            if (inSingleQuote) {
                if (c == '\\') { i++; continue; }   // escape sequence
                if (c == '\'') inSingleQuote = false;
                continue;
            }

            // Outside any comment or string literal
            if (c == '/' && next == '/')  break;                          // line comment
            if (c == '/' && next == '*') { inBlockComment = true; i++; continue; }
            if (c == '"')                { inDoubleQuote  = true; continue; }
            if (c == '\'')               { inSingleQuote  = true; continue; }
            if (c == '{') change++;
            if (c == '}') change--;
        }

        return new int[]{ change, inBlockComment ? 1 : 0 };
    }

    // =========================================================================
    // INDENT-BASED (Python)
    // =========================================================================

    /**
     * Splits Python source at top-level {@code def}, {@code class}, and
     * {@code async def} declarations (lines starting at column 0).
     */
    private static List<String> splitByIndent(String text) {
        var          lines   = text.split("\n", -1);
        List<String> blocks  = new ArrayList<>();
        List<String> current = new ArrayList<>();

        for (var line : lines) {
            boolean isTopLevel = line.startsWith("def ")
                    || line.startsWith("class ")
                    || line.startsWith("async def ");

            if (isTopLevel && !current.isEmpty()) {
                blocks.add(String.join("\n", current));
                current.clear();
            }
            current.add(line);
        }

        if (!current.isEmpty()) blocks.add(String.join("\n", current));
        return blocks;
    }

    // =========================================================================
    // Large-block fallback
    // =========================================================================

    /**
     * Attempts to split an oversized block by extracting its inner content
     * (stripping the outer brace wrapper) and splitting at depth 0.
     * This correctly handles Java classes by splitting at method boundaries.
     *
     * <p>The class header (everything up to and including the opening '{') is
     * prepended to every produced chunk so that each chunk is self-contained.
     * When a single method body still exceeds maxChunkSize, it is split at
     * line boundaries and the header is still prepended to each piece.
     */
    private static List<String> splitLargeBlock(String block, int maxChunkSize, int overlap) {
        var lines = block.split("\n", -1);

        // Find the line where the outermost { opens (depth 0 -> 1)
        int openLine = -1;
        int depth = 0;
        boolean inBlockComment = false;
        for (int i = 0; i < lines.length; i++) {
            int[] r = braceChange(lines[i], inBlockComment);
            inBlockComment = r[1] == 1;
            int prev = depth;
            depth += r[0];
            if (prev == 0 && depth == 1) { openLine = i; break; }
        }

        if (openLine >= 0 && openLine < lines.length - 1) {
            var header     = String.join("\n", Arrays.copyOfRange(lines, 0, openLine + 1));
            var innerLines = Arrays.copyOfRange(lines, openLine + 1, lines.length - 1);
            var inner      = splitAtDepth(innerLines, 0);

            if (inner.size() > 1) {
                var result = new ArrayList<String>();
                for (var part : inner) {
                    if (part.isBlank()) continue;
                    var chunk = header + "\n" + part;
                    if (chunk.length() <= maxChunkSize) {
                        result.add(chunk);
                    } else {
                        // Method body alone exceeds maxChunkSize: split the body
                        // by lines and prepend the class header to every piece.
                        for (var piece : splitByLines(part, maxChunkSize, overlap)) {
                            if (!piece.isBlank()) result.add(header + "\n" + piece);
                        }
                    }
                }
                return result;
            }

            // inner produced only 1 block (entire class body is one un-splittable unit,
            // e.g. a single enormous method). Split inner content by lines, still
            // prepending the class header to every piece.
            var innerContent = String.join("\n", innerLines);
            var result = new ArrayList<String>();
            for (var piece : splitByLines(innerContent, maxChunkSize, overlap)) {
                if (!piece.isBlank()) result.add(header + "\n" + piece);
            }
            return result;
        }

        // No outer brace found at all (e.g. free-standing code without brace structure).
        return splitByLines(block, maxChunkSize, overlap);
    }

    /**
     * Last-resort line-by-line chunking with character-level overlap.
     */
    private static List<String> splitByLines(String block, int maxChunkSize, int overlap) {
        var          lines   = block.split("\n", -1);
        List<String> result  = new ArrayList<>();
        var          current = new StringBuilder();

        for (var line : lines) {
            int needed = (current.length() > 0 ? 1 : 0) + line.length();
            if (current.length() + needed > maxChunkSize && current.length() > 0) {
                result.add(current.toString());
                // Carry the tail of the previous chunk as overlap context
                var prev = current.toString();
                current = new StringBuilder(
                        prev.substring(Math.max(0, prev.length() - overlap)));
            }
            if (current.length() > 0) current.append('\n');
            current.append(line);
        }

        if (!current.isEmpty()) result.add(current.toString());
        return result;
    }
}
