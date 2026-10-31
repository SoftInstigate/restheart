/*-
 * ========================LICENSE_START=================================
 * restheart-polyglot
 * %%
 * Copyright (C) 2020 - 2026 SoftInstigate
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
package org.restheart.polyglot.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The file system that resolves {@code restheart:sdk}: the specifier rewrite in the plugin's own
 * sources, the virtual module served from memory, everything else untouched.
 */
class SdkFileSystemTest {
    @Test
    void rewritesTheSpecifierOfStaticImports() {
        assertEquals("import { logger } from \"" + SdkFileSystem.SDK_PATH + "\";",
                SdkFileSystem.rewrite("import { logger } from \"restheart:sdk\";"));
        assertEquals("import { logger } from '" + SdkFileSystem.SDK_PATH + "';",
                SdkFileSystem.rewrite("import { logger } from 'restheart:sdk';"));
        assertEquals("import * as sdk from\"" + SdkFileSystem.SDK_PATH + "\"",
                SdkFileSystem.rewrite("import * as sdk from\"restheart:sdk\""));
    }

    @Test
    void rewritesDynamicImports() {
        assertEquals("const sdk = await import(\"" + SdkFileSystem.SDK_PATH + "\");",
                SdkFileSystem.rewrite("const sdk = await import(\"restheart:sdk\");"));
    }

    @Test
    void leavesOtherStringsAlone() {
        var source = "logger.info(\"loaded from restheart:sdk\"); const s = 'restheart:sdk';";
        assertSame(source, SdkFileSystem.rewrite(source));
    }

    @Test
    void rewritesEveryImportAndKeepsTheLineCount() {
        var source = """
                import { a } from "restheart:sdk";
                import { b } from './lib.mjs';
                import { c } from 'restheart:sdk';
                """;
        var rewritten = SdkFileSystem.rewrite(source);
        assertEquals(source.lines().count(), rewritten.lines().count(), "line numbers in stack traces must not move");
        assertTrue(!rewritten.contains("restheart:sdk"));
        assertTrue(rewritten.contains("from './lib.mjs'"));
    }

    @Test
    void servesTheSdkModuleFromMemory(@TempDir Path dir) throws IOException {
        var fs = new SdkFileSystem(dir, "export const x = 1;");
        var sdk = fs.parsePath(URI.create("restheart:sdk"));

        assertEquals(Path.of(SdkFileSystem.SDK_PATH), sdk);
        assertEquals(sdk, fs.parsePath("restheart:sdk"));
        assertEquals(sdk, fs.toAbsolutePath(sdk));
        assertEquals(sdk, fs.toRealPath(sdk));
        assertEquals(Boolean.TRUE, fs.readAttributes(sdk, "*").get("isRegularFile"));

        try (var channel = fs.newByteChannel(sdk, Set.of())) {
            var buf = ByteBuffer.allocate(64);
            channel.read(buf);
            assertEquals("export const x = 1;", new String(buf.array(), 0, buf.position(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void rewritesThePluginSourcesAndNotNodeModules(@TempDir Path dir) throws IOException {
        var plugin = dir.resolve("plugin.mjs");
        Files.writeString(plugin, "import { a } from \"restheart:sdk\";");
        var dep = dir.resolve("node_modules/dep/index.mjs");
        Files.createDirectories(dep.getParent());
        Files.writeString(dep, "import { a } from \"restheart:sdk\";");

        var fs = new SdkFileSystem(dir, "");

        assertEquals("import { a } from \"" + SdkFileSystem.SDK_PATH + "\";", read(fs, plugin));
        assertEquals("import { a } from \"restheart:sdk\";", read(fs, dep), "a package under node_modules is served as it is");
    }

    private static String read(SdkFileSystem fs, Path p) throws IOException {
        try (var channel = fs.newByteChannel(p, Set.of())) {
            var buf = ByteBuffer.allocate((int) channel.size());
            while (buf.hasRemaining() && channel.read(buf) > 0) {
                // read all
            }
            return new String(buf.array(), 0, buf.position(), StandardCharsets.UTF_8);
        }
    }
}
