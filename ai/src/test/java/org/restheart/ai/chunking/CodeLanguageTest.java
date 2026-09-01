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
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class CodeLanguageTest {

    @Test
    public void nullFilename_returnsNull() {
        assertNull(CodeLanguage.fromFilename(null));
    }

    @Test
    public void noExtension_returnsNull() {
        assertNull(CodeLanguage.fromFilename("README"));
    }

    @Test
    public void trailingDotWithNoExtension_returnsNull() {
        assertNull(CodeLanguage.fromFilename("weird."));
    }

    @Test
    public void unrecognizedExtension_returnsNull() {
        assertNull(CodeLanguage.fromFilename("report.pdf"));
        assertNull(CodeLanguage.fromFilename("notes.txt"));
        assertNull(CodeLanguage.fromFilename("readme.md"));
    }

    @Test
    public void python_isIndentBased() {
        assertEquals(CodeLanguage.INDENT_BASED, CodeLanguage.fromFilename("script.py"));
    }

    @Test
    public void braceFamilyLanguages_areBraceBased() {
        for (var filename : new String[] {
                "Main.java", "App.kt", "index.js", "index.mjs", "component.jsx",
                "app.ts", "component.tsx", "main.go", "lib.rs", "App.swift",
                "Program.cs", "lib.c", "lib.h", "lib.cpp", "lib.cc", "lib.cxx", "lib.hpp"}) {
            assertEquals(CodeLanguage.BRACE_BASED, CodeLanguage.fromFilename(filename),
                "expected BRACE_BASED for " + filename);
        }
    }

    @Test
    public void extensionMatchIsCaseInsensitive() {
        assertEquals(CodeLanguage.BRACE_BASED, CodeLanguage.fromFilename("Main.JAVA"));
        assertEquals(CodeLanguage.INDENT_BASED, CodeLanguage.fromFilename("script.PY"));
    }

    @Test
    public void pathWithDirectories_usesFinalExtension() {
        assertEquals(CodeLanguage.BRACE_BASED, CodeLanguage.fromFilename("src/main/java/App.java"));
    }
}
