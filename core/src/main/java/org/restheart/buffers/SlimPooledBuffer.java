/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2024 SoftInstigate
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

import io.undertow.connector.PooledByteBuffer;

/**
 *
 * @author Andrea Di Cesare
 */
public class SlimPooledBuffer implements PooledByteBuffer {
    private boolean open;
    private final ByteBuffer buffer;

    public SlimPooledBuffer(boolean direct, int bufferSize) {
        this.buffer = direct ? ByteBuffer.allocateDirect(bufferSize) : ByteBuffer.allocate(bufferSize);
        this.open = false;
    }

    @Override
    public ByteBuffer getBuffer() {
        return this.buffer;
    }

    @Override
    public void close() {
        this.open = false;
    }

    public void open() {
        this.open = true;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public String toString() {
        return "SlimPooledBuffer{" +
                "buffer=" + buffer +
                ", open=" + open +
                '}';
    }
}