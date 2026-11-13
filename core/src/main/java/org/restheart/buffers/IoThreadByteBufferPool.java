/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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
package org.restheart.buffers;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import io.undertow.connector.ByteBufferPool;
import io.undertow.connector.PooledByteBuffer;

/**
 * Buffer pool for the IO threads, without locks.
 *
 * <p>Each thread keeps its free buffers in a {@link ThreadLocal}: the IO threads
 * are few and live as long as the server. A buffer released by another thread,
 * e.g. by a virtual thread that finished a request, goes to a shared lock-free
 * queue, from which any thread can take it.</p>
 *
 * <p>It replaces Undertow's {@code DefaultByteBufferPool}, whose per-thread
 * cache is a single {@code Collections.synchronizedMap(new WeakHashMap<>())}:
 * every allocation and release on every IO thread took the same lock.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 9.15.0
 */
final class IoThreadByteBufferPool implements ByteBufferPool {
    /** Free buffers kept by each thread. */
    private static final int MAX_LOCAL = 64;

    /** Free buffers kept in the shared queue; beyond it they are left to the GC. */
    private static final int MAX_SHARED = 1024;

    private final boolean direct;
    private final int bufferSize;
    private final ThreadLocal<ArrayDeque<ByteBuffer>> local = ThreadLocal.withInitial(ArrayDeque::new);
    private final ConcurrentLinkedQueue<ByteBuffer> shared = new ConcurrentLinkedQueue<>();
    private final AtomicInteger sharedSize = new AtomicInteger();
    private volatile IoThreadByteBufferPool arrayBackedPool;

    IoThreadByteBufferPool(boolean direct, int bufferSize) {
        this.direct = direct;
        this.bufferSize = bufferSize;
    }

    @Override
    public PooledByteBuffer allocate() {
        final var owner = Thread.currentThread();
        final var free = local.get();

        var buffer = free.pollFirst();

        if (buffer == null) {
            buffer = shared.poll();

            if (buffer != null) {
                sharedSize.decrementAndGet();
            } else {
                buffer = direct ? ByteBuffer.allocateDirect(bufferSize) : ByteBuffer.allocate(bufferSize);
            }
        }

        buffer.clear();

        return new Pooled(buffer, owner, free);
    }

    private void release(ByteBuffer buffer, Thread owner, ArrayDeque<ByteBuffer> ownerFree) {
        if (Thread.currentThread() == owner && ownerFree.size() < MAX_LOCAL) {
            ownerFree.offerFirst(buffer);
        } else if (sharedSize.incrementAndGet() <= MAX_SHARED) {
            shared.offer(buffer);
        } else {
            sharedSize.decrementAndGet();
        }
    }

    @Override
    public ByteBufferPool getArrayBackedPool() {
        if (!direct) {
            return this;
        }

        var ret = arrayBackedPool;

        if (ret == null) {
            synchronized (this) {
                ret = arrayBackedPool;
                if (ret == null) {
                    ret = arrayBackedPool = new IoThreadByteBufferPool(false, bufferSize);
                }
            }
        }

        return ret;
    }

    @Override
    public int getBufferSize() {
        return bufferSize;
    }

    @Override
    public boolean isDirect() {
        return direct;
    }

    @Override
    public void close() {
        // buffers are left to the GC
    }

    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<Pooled> OPEN = AtomicIntegerFieldUpdater.newUpdater(Pooled.class, "open");

    private final class Pooled implements PooledByteBuffer {
        private final ByteBuffer buffer;
        private final Thread owner;
        private final ArrayDeque<ByteBuffer> ownerFree;
        // 1 open, 0 released; released once even if close() races on two threads
        private volatile int open = 1;

        Pooled(ByteBuffer buffer, Thread owner, ArrayDeque<ByteBuffer> ownerFree) {
            this.buffer = buffer;
            this.owner = owner;
            this.ownerFree = ownerFree;
        }

        @Override
        public ByteBuffer getBuffer() {
            if (open == 0) {
                throw new IllegalStateException("buffer already released");
            }
            return buffer;
        }

        @Override
        public void close() {
            if (OPEN.compareAndSet(this, 1, 0)) {
                release(buffer, owner, ownerFree);
            }
        }

        @Override
        public boolean isOpen() {
            return open == 1;
        }

        @Override
        public String toString() {
            return "IoThreadByteBufferPool.Pooled{" + buffer + "}";
        }
    }
}
