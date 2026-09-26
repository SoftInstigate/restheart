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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

public class ChunkingGateTest {

    /** Starts a virtual thread that takes a slot of {@code key} and holds it until {@code release} counts down. */
    private static Thread holder(ChunkingGate gate, String key, int limit, CountDownLatch acquired, CountDownLatch release) {
        return Thread.ofVirtual().start(() -> {
            try {
                gate.acquire(key, limit);
                acquired.countDown();
                release.await();
                gate.release(key);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    @Test
    public void aKeyAtItsLimit_waitsForOneOfItsOwnToFinish() throws Exception {
        var gate = new ChunkingGate(10);
        var release = new CountDownLatch(1);
        var first = new CountDownLatch(1);
        holder(gate, "tenant-a", 1, first, release);
        assertTrue(first.await(2, TimeUnit.SECONDS));

        var second = new CountDownLatch(1);
        var release2 = new CountDownLatch(1);
        holder(gate, "tenant-a", 1, second, release2);
        assertFalse(second.await(200, TimeUnit.MILLISECONDS), "a second file of the same database waits");

        release.countDown();
        assertTrue(second.await(2, TimeUnit.SECONDS), "and goes once the first is done");
        release2.countDown();
    }

    @Test
    public void anotherKey_isNotHeldUpByAKeyAtItsLimit() throws Exception {
        var gate = new ChunkingGate(10);
        var release = new CountDownLatch(1);
        var a = new CountDownLatch(1);
        holder(gate, "tenant-a", 1, a, release);
        assertTrue(a.await(2, TimeUnit.SECONDS));

        var b = new CountDownLatch(1);
        holder(gate, "tenant-b", 1, b, release);
        assertTrue(b.await(2, TimeUnit.SECONDS), "another database has its own slot");
        release.countDown();
    }

    @Test
    public void theNodeCap_holdsAcrossKeys() throws Exception {
        var gate = new ChunkingGate(2);
        var release = new CountDownLatch(1);
        var two = new CountDownLatch(2);
        for (var key : new String[] {"a", "b"}) {
            holder(gate, key, 5, two, release);
        }
        assertTrue(two.await(2, TimeUnit.SECONDS));
        assertEquals(2, gate.running());

        var third = new CountDownLatch(1);
        holder(gate, "c", 5, third, new CountDownLatch(0));
        assertFalse(third.await(200, TimeUnit.MILLISECONDS), "a third file waits for a node slot");
        assertEquals(1, gate.running("c"), "holding its database's slot while it waits");

        release.countDown();
        assertTrue(third.await(2, TimeUnit.SECONDS));
    }

    @Test
    public void releasingEverything_leavesNothingRunning() throws Exception {
        var gate = new ChunkingGate(3);
        gate.acquire("a", 2);
        gate.acquire("a", 2);
        gate.acquire("b", 1);
        assertEquals(3, gate.running());
        gate.release("a");
        gate.release("a");
        gate.release("b");
        assertEquals(0, gate.running());
        assertEquals(0, gate.running("a"));
        assertEquals(0, gate.running("b"));
    }
}
