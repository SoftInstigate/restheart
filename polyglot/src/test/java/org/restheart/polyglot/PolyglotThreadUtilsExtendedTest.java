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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * {@link PolyglotThreadUtils} with the escape hatch off, its default: a Truffle operation runs
 * on the thread that calls it, and only the context classloader is set around it.
 */
class PolyglotThreadUtilsExtendedTest {

    @Test
    void theEscapeHatchIsOffByDefault() {
        assertFalse(PolyglotThreadUtils.isForcePlatform());
        assertFalse(PolyglotThreadUtils.isAlreadyOnPlatformThread());
    }

    @Test
    void runsOnTheCallingThread() throws Exception {
        var caller = Thread.currentThread();
        var seen = PolyglotThreadUtils.run(Thread::currentThread);
        assertSame(caller, seen);
    }

    @Test
    void runsOnTheCallingVirtualThread() throws Exception {
        var seen = new AtomicReference<Thread>();
        var failure = new AtomicReference<Throwable>();

        var vt = Thread.ofVirtual().unstarted(() -> {
            try {
                seen.set(PolyglotThreadUtils.run(Thread::currentThread));
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        vt.start();
        vt.join();

        assertNull(failure.get(), () -> String.valueOf(failure.get()));
        assertSame(vt, seen.get());
        assertTrue(seen.get().isVirtual());
    }

    @Test
    void restoresTheContextClassloader() throws Exception {
        var before = Thread.currentThread().getContextClassLoader();
        PolyglotThreadUtils.run(() -> null);
        assertSame(before, Thread.currentThread().getContextClassLoader());
    }

    @Test
    void runIOReturnsValue() throws Exception {
        assertEquals(42, PolyglotThreadUtils.runIO(() -> 42));
    }

    @Test
    void runIOPropagatesIOException() {
        var ex = assertThrows(IOException.class,
                () -> PolyglotThreadUtils.runIO(() -> {
                    throw new IOException("io-boom");
                }));
        assertEquals("io-boom", ex.getMessage());
    }

    @Test
    void runIOPropagatesRuntimeException() {
        var ex = assertThrows(IllegalStateException.class,
                () -> PolyglotThreadUtils.runIO(() -> {
                    throw new IllegalStateException("rt-boom");
                }));
        assertEquals("rt-boom", ex.getMessage());
    }

    @Test
    void runIOWrapsOtherCheckedExceptions() {
        var ex = assertThrows(IOException.class,
                () -> PolyglotThreadUtils.runIO(() -> {
                    throw new Exception("checked-boom");
                }));
        assertEquals("checked-boom", ex.getCause().getMessage());
    }
}
