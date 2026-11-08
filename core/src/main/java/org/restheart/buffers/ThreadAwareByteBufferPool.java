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

import io.undertow.connector.ByteBufferPool;
import io.undertow.connector.PooledByteBuffer;

/**
 * A byte buffer pool that delegates to specific implementations based on the thread type.
 * <p>
 * For IO threads, it utilizes {@link IoThreadByteBufferPool}, a lock-free pool with a free list per thread
 * (since 9.15.0; before, Undertow's {@code DefaultByteBufferPool}, whose per-thread cache takes a global lock).
 * For virtual worker threads, it uses the {@code NotPoolingByteBufferPool}, which doesn't pool resources, to optimize performance
 * given the unique characteristics of virtual threads.
 * </p>
 *
 * @author Andrea Di Cesare
 */

public class ThreadAwareByteBufferPool implements ByteBufferPool {
    private final ByteBufferPool ioThreadsPool;
    private final NotPoolingByteBufferPool notPoolingByteBufferPool;

    private final boolean enablePooling;

    /**
     * @param direct               If io threads implementation should use direct buffers
     * @param bufferSize           The buffer size to use
     * @param enablePooling        true to enable pooling for platform threads
     */
    public ThreadAwareByteBufferPool(boolean direct, int bufferSize, boolean enablePooling) {
        this.ioThreadsPool = enablePooling ? new IoThreadByteBufferPool(direct, bufferSize) : null;
        this.notPoolingByteBufferPool = new NotPoolingByteBufferPool(false, bufferSize);
        this.enablePooling = enablePooling;
    }

    @Override
    public int getBufferSize() {
        return this.notPoolingByteBufferPool.getBufferSize();
    }

    @Override
    public boolean isDirect() {
        return this.notPoolingByteBufferPool.isDirect();
    }

    @Override
    public PooledByteBuffer allocate() {
        return !enablePooling || Thread.currentThread().isVirtual()
                ? this.notPoolingByteBufferPool.allocate()
                : this.ioThreadsPool.allocate();
    }

    @Override
    public ByteBufferPool getArrayBackedPool() {
        return !enablePooling || Thread.currentThread().isVirtual()
                ? this.notPoolingByteBufferPool.getArrayBackedPool()
                : this.ioThreadsPool.getArrayBackedPool();
    }

    @Override
    public void close() {
        if (enablePooling && !Thread.currentThread().isVirtual()) {
            this.ioThreadsPool.close();
        }
    }
}
