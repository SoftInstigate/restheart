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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * How many files are chunked at once: at most {@code limit} of one key, a database, and at most
 * the node-wide cap across all keys (#758). Tika reads a file whole and holds a carrier thread
 * while it extracts, and the embedding calls that follow hold the uploader's provider quota: ten
 * uploads at once on a node shared by many tenants must not mean ten of both.
 *
 * <p>A job takes its key's slot first and a node slot after, so a job waiting for its own tenant
 * never holds a node slot another tenant could use. A lock and conditions, not {@code synchronized},
 * so a waiting virtual thread does not pin its carrier.
 */
public final class ChunkingGate {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    private final Map<String, Integer> runningByKey = new HashMap<>();
    private volatile int nodeCap;
    private int running = 0;

    /** @param nodeCap how many files the node chunks at once, across all keys; at least 1 */
    public ChunkingGate(int nodeCap) {
        this.nodeCap = Math.max(1, nodeCap);
    }

    /** Waits until {@code key} has fewer than {@code limit} jobs running and the node has a free slot, then takes both. */
    public void acquire(String key, int limit) throws InterruptedException {
        var keyLimit = Math.max(1, limit);
        lock.lock();
        try {
            while (runningByKey.getOrDefault(key, 0) >= keyLimit) {
                changed.await();
            }
            runningByKey.merge(key, 1, Integer::sum);
            try {
                while (running >= nodeCap) {
                    changed.await();
                }
            } catch (InterruptedException e) {
                // gave up waiting for a node slot: the key's slot goes back
                releaseKey(key);
                changed.signalAll();
                throw e;
            }
            running++;
        } finally {
            lock.unlock();
        }
    }

    /** Gives back the slots {@link #acquire} took for {@code key}. */
    public void release(String key) {
        lock.lock();
        try {
            running = Math.max(0, running - 1);
            releaseKey(key);
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /** Jobs running now, across all keys. */
    public int running() {
        lock.lock();
        try {
            return running;
        } finally {
            lock.unlock();
        }
    }

    /** Jobs of {@code key} holding a key slot now, running or waiting for a node slot. */
    public int running(String key) {
        lock.lock();
        try {
            return runningByKey.getOrDefault(key, 0);
        } finally {
            lock.unlock();
        }
    }

    private void releaseKey(String key) {
        runningByKey.computeIfPresent(key, (k, n) -> n > 1 ? n - 1 : null);
    }
}
