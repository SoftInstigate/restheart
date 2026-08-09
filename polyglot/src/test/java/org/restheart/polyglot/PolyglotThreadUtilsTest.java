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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class PolyglotThreadUtilsTest {

    @Test
    void runsTaskOnAPlatformThread() throws Exception {
        var isVirtual = PolyglotThreadUtils.onPlatformThread(() -> Thread.currentThread().isVirtual());
        assertFalse(isVirtual, "task must run on a platform thread");
    }

    @Test
    void runsTaskOnAPlatformThreadEvenWhenCalledFromAVirtualThread() throws Exception {
        var isVirtual = new AtomicBoolean(true);
        var failure = new AtomicReference<Throwable>();

        var vt = Thread.ofVirtual().unstarted(() -> {
            try {
                isVirtual.set(PolyglotThreadUtils.onPlatformThread(() -> Thread.currentThread().isVirtual()));
            } catch (Throwable t) {
                failure.set(t);
            }
        });

        vt.start();
        vt.join();

        assertNull(failure.get(), () -> String.valueOf(failure.get()));
        assertFalse(isVirtual.get(), "task must run on a platform thread even when the caller is a virtual thread");
    }

    @Test
    void propagatesCheckedExceptionFromTask() {
        var ex = assertThrows(IOException.class, () -> PolyglotThreadUtils.onPlatformThread(() -> {
            throw new IOException("boom");
        }));
        assertEquals("boom", ex.getMessage());
    }

    @Test
    void propagatesRuntimeExceptionFromTask() {
        var ex = assertThrows(IllegalStateException.class, () -> PolyglotThreadUtils.onPlatformThread(() -> {
            throw new IllegalStateException("boom");
        }));
        assertEquals("boom", ex.getMessage());
    }
}
