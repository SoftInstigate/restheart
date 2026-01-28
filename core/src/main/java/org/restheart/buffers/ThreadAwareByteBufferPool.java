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
import io.undertow.server.DefaultByteBufferPool;

/**
 * A byte buffer pool that delegates to specific implementations based on the thread type.
 * <p>
 * For IO threads, it utilizes the {@code undertow.DefaultByteBufferPool} to pool resources efficiently.
 * For virtual worker threads, it uses the {@code NotPoolingByteBufferPool}, which doesn't pool resources, to optimize performance
 * given the unique characteristics of virtual threads.
 * </p>
 *
 * @author Andrea Di Cesare
 */

public class ThreadAwareByteBufferPool implements ByteBufferPool {
    private final DefaultByteBufferPool undertowDefaultByteBufferPool;
    private final NotPoolingByteBufferPool notPoolingByteBufferPool;

    private final boolean enablePooling;

    /**
     * @param direct               If io threads implementation should use direct buffers
     * @param bufferSize           The buffer size to use
     * @param enablePooling        true to enable pooling for platform threads
     */
    public ThreadAwareByteBufferPool(boolean direct, int bufferSize, boolean enablePooling) {
        this.undertowDefaultByteBufferPool = enablePooling ? new DefaultByteBufferPool(direct, bufferSize, -1, 4) : null;
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
            : this.undertowDefaultByteBufferPool.allocate();
    }

    @Override
    public ByteBufferPool getArrayBackedPool() {
        return !enablePooling || Thread.currentThread().isVirtual()
            ? this.notPoolingByteBufferPool.getArrayBackedPool()
            : this.undertowDefaultByteBufferPool.getArrayBackedPool();
    }

    @Override
    public void close() {
        if (enablePooling && !Thread.currentThread().isVirtual()) {
            this.undertowDefaultByteBufferPool.close();
        }
    }
}
