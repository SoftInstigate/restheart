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
package org.restheart.polyglot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PolyglotThreadUtils} covering
 * {@code isAlreadyOnPlatformThread()} and {@code onPlatformThreadIO()}.
 */
class PolyglotThreadUtilsExtendedTest {

    @Test
    void isAlreadyOnPlatformThreadReturnsFalseOnMainThread() {
        assertFalse(PolyglotThreadUtils.isAlreadyOnPlatformThread(),
                "main thread is not the platform executor thread");
    }

    @Test
    void isAlreadyOnPlatformThreadReturnsTrueInsideOnPlatformThread() throws Exception {
        var result = PolyglotThreadUtils.onPlatformThread(
                () -> PolyglotThreadUtils.isAlreadyOnPlatformThread());
        assertTrue(result,
                "task running inside onPlatformThread should see itself as on the platform thread");
    }

    @Test
    void isAlreadyOnPlatformThreadReturnsFalseOnOtherPlatformThread() throws Exception {
        var result = new AtomicBoolean(true);
        var t = new Thread(() ->
                result.set(PolyglotThreadUtils.isAlreadyOnPlatformThread()));
        t.start();
        t.join();
        assertFalse(result.get(),
                "a different platform thread is not the RH JS PLT thread");
    }

    @Test
    void isAlreadyOnPlatformThreadReturnsFalseOnVirtualThread() throws Exception {
        var result = new AtomicBoolean(true);
        var failure = new AtomicReference<Throwable>();

        var vt = Thread.ofVirtual().unstarted(() -> {
            try {
                result.set(PolyglotThreadUtils.isAlreadyOnPlatformThread());
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        vt.start();
        vt.join();

        assertNull(failure.get(), () -> String.valueOf(failure.get()));
        assertFalse(result.get(),
                "a virtual thread is not the RH JS PLT thread");
    }

    @Test
    void onPlatformThreadIOReturnsValue() throws Exception {
        var result = PolyglotThreadUtils.onPlatformThreadIO(() -> 42);
        assertEquals(42, result);
    }

    @Test
    void onPlatformThreadIOPropagatesIOException() {
        var ex = assertThrows(IOException.class,
                () -> PolyglotThreadUtils.onPlatformThreadIO(() -> {
                    throw new IOException("io-boom");
                }));
        assertEquals("io-boom", ex.getMessage());
    }

    @Test
    void onPlatformThreadIOPropagatesRuntimeException() {
        var ex = assertThrows(IllegalStateException.class,
                () -> PolyglotThreadUtils.onPlatformThreadIO(() -> {
                    throw new IllegalStateException("rt-boom");
                }));
        assertEquals("rt-boom", ex.getMessage());
    }

    @Test
    void onPlatformThreadIORunsOnPlatformThread() throws Exception {
        var threadName = PolyglotThreadUtils.onPlatformThreadIO(
                () -> Thread.currentThread().getName());
        assertTrue(threadName.startsWith("RH JS PLT"),
                "onPlatformThreadIO should dispatch to the platform thread");
    }
}
