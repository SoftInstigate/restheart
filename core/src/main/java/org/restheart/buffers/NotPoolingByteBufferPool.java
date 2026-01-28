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
 * A byte buffer pool implementation that does not actually pool resources.
 * <p>
 * This is intended for use with Virtual Threads, where resource pooling
 * is unnecessary and may even be disadvantageous due to potential overhead.
 * </p>
 *
 * @author Andrea Di Cesare
 */
public class NotPoolingByteBufferPool implements ByteBufferPool {
    private final boolean direct;
    private final int bufferSize;

    /**
     * @param direct               If this implementation should use direct buffers
     * @param bufferSize           The buffer size to use
     */
    public NotPoolingByteBufferPool(boolean direct, int bufferSize) {
        this.direct = direct;
        this.bufferSize = bufferSize;
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
    public PooledByteBuffer allocate() {
        return new SlimPooledBuffer(direct, bufferSize);
    }

    @Override
    public NotPoolingByteBufferPool getArrayBackedPool() {
        return this;
    }

    @Override
    public void close() {
        // nothing to do
    }
}
