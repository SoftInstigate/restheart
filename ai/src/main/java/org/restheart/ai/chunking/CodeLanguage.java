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

/**
 * Classifies a source file, by its filename extension, as one of the structural
 * families {@link CodeAwareSplitter} knows how to split at function/class
 * boundaries rather than at arbitrary character counts.
 */
public enum CodeLanguage {
    /** C, C++, Java, Kotlin, JavaScript, TypeScript, Go, Rust, Swift, C#, ... */
    BRACE_BASED,
    /** Python. */
    INDENT_BASED;

    /**
     * Resolves the code language from a filename's extension.
     *
     * @return the matching {@link CodeLanguage}, or {@code null} if {@code filename}
     *         is missing, has no extension, or isn't a recognized source file (in
     *         which case the caller should fall back to plain character-based chunking).
     */
    public static CodeLanguage fromFilename(String filename) {
        if (filename == null) {
            return null;
        }

        var dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return null;
        }

        return switch (filename.substring(dot + 1).toLowerCase()) {
            case "py" -> INDENT_BASED;
            case "java", "kt", "js", "mjs", "jsx", "ts", "tsx",
                 "go", "rs", "swift", "cs", "c", "h", "cpp", "cc", "cxx", "hpp" -> BRACE_BASED;
            default -> null;
        };
    }
}
