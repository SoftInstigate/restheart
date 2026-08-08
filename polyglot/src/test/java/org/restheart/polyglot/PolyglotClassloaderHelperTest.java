/*-
 * ========================LICENSE_START=================================
 * restheart-polyglot
 * %%
 * Copyright (C) 2018 - 2026 SoftInstigate
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
package org.restheart.polyglot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class PolyglotClassloaderHelperTest {

    @Test
    void withPluginsClassloaderResultReturnsValue() throws IOException {
        var result = PolyglotClassloaderHelper.withPluginsClassloaderResult(() -> "hello");
        assertEquals("hello", result);
    }

    @Test
    void withPluginsClassloaderResultPropagatesException() {
        assertThrows(IOException.class, () ->
            PolyglotClassloaderHelper.withPluginsClassloaderResult(() -> {
                throw new IOException("test");
            }));
    }

    @Test
    void withPluginsClassloaderRestoresContextClassLoader() throws IOException {
        ClassLoader original = Thread.currentThread().getContextClassLoader();

        PolyglotClassloaderHelper.withPluginsClassloaderResult(() -> "done");

        assertSame(original, Thread.currentThread().getContextClassLoader(),
            "context classloader must be restored after call");
    }

    @Test
    void withPluginsClassloaderRestoresOnException() {
        ClassLoader original = Thread.currentThread().getContextClassLoader();

        assertThrows(IOException.class, () ->
            PolyglotClassloaderHelper.withPluginsClassloaderResult(() -> {
                throw new IOException("fail");
            }));

        assertSame(original, Thread.currentThread().getContextClassLoader(),
            "context classloader must be restored even after exception");
    }

    @Test
    void withPluginsClassloaderVoidReturnsValue() throws IOException {
        final boolean[] ran = {false};
        PolyglotClassloaderHelper.withPluginsClassloader(() -> ran[0] = true);
        assertEquals(true, ran[0]);
    }

    @Test
    void withPluginsClassloaderVoidPropagatesException() {
        assertThrows(IOException.class, () ->
            PolyglotClassloaderHelper.withPluginsClassloader(() -> {
                throw new IOException("test");
            }));
    }

    @Test
    void withPluginsClassloaderVoidRestoresOnException() {
        ClassLoader original = Thread.currentThread().getContextClassLoader();

        assertThrows(IOException.class, () ->
            PolyglotClassloaderHelper.withPluginsClassloader(() -> {
                throw new IOException("fail");
            }));

        assertSame(original, Thread.currentThread().getContextClassLoader(),
            "context classloader must be restored even after exception");
    }

    @Test
    void withPluginsClassloaderHandlesNullClassLoader() throws IOException {
        // PluginsClassloader is not initialized in unit tests,
        // so the helper falls back to running the action directly.
        // This verifies the fallback path works.
        var result = PolyglotClassloaderHelper.withPluginsClassloaderResult(() -> "fallback");
        assertEquals("fallback", result);
    }
}
