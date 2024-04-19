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

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ArrayListMultimap;

import io.undertow.connector.ByteBufferPool;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.DirectByteBufferDeallocator;

/**
 * A byte buffer pool that tracks allocation by thread
 * This can be used to pool buffers and requires resource release
 *
 * For instance, add this to WorkingThreadsPoolDispatcher.handle():

    final var ioThread = exchange.getIoThread();
    exchange.addExchangeCompleteListener((completedExchange, nextListener) -> {
        nextListener.proceed();
        if (completedExchange.getConnection().getByteBufferPool() instanceof ThreadTrackingByteBufferPool bp) {
            bp.release();
            bp.release(ioThread); // the buffer used for the reponse is not released!
        }
    });
 *
 * However this makes all complex and resulted slower than the simple FastByteBufferPool
 *
 * @author Andrea Di Cesare
 */
public class ThreadTrackingByteBufferPool implements ByteBufferPool {
    private static final Logger LOGGER = LoggerFactory.getLogger(ThreadTrackingByteBufferPool.class);

    private final boolean direct;
    private final int bufferSize;

    private final Lock lock;
    private final ArrayListMultimap<Thread, SoftReference<SlimPooledBuffer>> buffersByThread = ArrayListMultimap.create();
    private final ArrayList<SoftReference<SlimPooledBuffer>> pool = new ArrayList<>();

    /**
     * @param direct               If this implementation should use direct buffers
     * @param bufferSize           The buffer size to use
     */
    public ThreadTrackingByteBufferPool(boolean direct, int bufferSize) {
        this.direct = direct;
        this.bufferSize = bufferSize;
        this.lock = new ReentrantLock();
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
        this.lock.lock();
        SlimPooledBuffer ret = null;
        try {
            if (!this.pool.isEmpty()) {
                var sf = this.pool.removeLast();
                var bb = sf.get();
                if (bb != null) {
                    this.buffersByThread.put(Thread.currentThread(), sf);
                    ret = bb;
                    ret.open();
                    LOGGER.debug("got byte buffer from pool");
                } else {
                    LOGGER.debug("byte buffer in pool was deallocated");
                }
            }

            if (ret == null) {
                var pooledBuffer = new SlimPooledBuffer(direct, bufferSize);

                this.buffersByThread.put(Thread.currentThread(), new SoftReference(pooledBuffer));
                ret = pooledBuffer;
                ret.open();
                LOGGER.debug("byte buffer allocated");
            }
        } finally {
             this.lock.unlock();
        }

        LOGGER.debug("allocate: pool size {}, used buffers {}", this.pool.size(), this.buffersByThread.size());
        return ret;
    }

    public List<SoftReference<SlimPooledBuffer>> allocated() {
        List<SoftReference<SlimPooledBuffer>> ret;
        this.lock.lock();
        try {
            ret = this.buffersByThread.get(Thread.currentThread());
        } finally {
            this.lock.unlock();
        }

        return ret;
    }

    public void release(Thread thread, List<SlimPooledBuffer> buffers) {
        this.lock.lock();
        try {
            var current = this.buffersByThread.get(thread);

            var next = current.stream()
                .filter(c -> !buffers.contains(c.get()))
                .collect(Collectors.toList());

            this.buffersByThread.replaceValues(thread, next);

            buffers
                .stream()
                .filter(b -> b != null)
                .peek(sr -> LOGGER.debug("byte buffer released"))
                .forEach(b -> {
                    b.close();
                    b.getBuffer().clear();
                    this.pool.add(new SoftReference<>(b));
                });
        } finally {
             this.lock.unlock();
        }

        LOGGER.debug("release (list): pool size {}, used buffers {}", this.pool.size(), this.buffersByThread.size());
    }

    public void release(Thread thread, SlimPooledBuffer buffer) {
        var list = new ArrayList<SlimPooledBuffer>(1);
        list.add(buffer);
        release(thread, list);
    }

    public void release() {
       release(Thread.currentThread());
    }

    public void release(Thread thread) {
        this.lock.lock();
        try {
            var removed = this.buffersByThread.removeAll(thread);

            removed
                .stream()
                .filter(sr -> sr.get() != null)
                .forEach(sr -> {
                    sr.get().close();
                    sr.get().getBuffer().clear();
                    this.pool.add(sr);
                });

            LOGGER.debug("{} byte buffers released for {}", removed.size(), thread.getName());
        } finally {
             this.lock.unlock();
        }

        LOGGER.debug("released for {}: pool size {}, used buffers {}", thread.getName(), this.pool.size(), this.buffersByThread.size());
    }

    @Override
    public ThreadTrackingByteBufferPool getArrayBackedPool() {
        return this;
    }

    @Override
    public void close() {
        LOGGER.debug("close byte buffer pool");
        this.lock.lock();
        try {
            this.pool.stream().map(sr -> sr.get()).filter(bb -> bb != null).forEach(bb -> DirectByteBufferDeallocator.free(bb.getBuffer()));
            this.pool.clear();
            this.buffersByThread.entries().stream().map(e -> e.getValue().get()).filter(bb -> bb != null).forEach(bb -> DirectByteBufferDeallocator.free(bb.getBuffer()));
            this.buffersByThread.clear();
        } finally {
             this.lock.unlock();
        }
    }
}
