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
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.polyglot.Engine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClient;

/**
 * Regression tests ensuring polyglot Context creation and execution work when
 * invoked from a virtual thread. Without routing through
 * {@link PolyglotThreadUtils}, this can throw an
 * {@code ArrayIndexOutOfBoundsException} on some GraalVM/Truffle versions
 * (see https://github.com/oracle/graal/issues/7520). If these tests fail,
 * the workaround in {@link ContextQueue} is broken and must be fixed before
 * removing it.
 */
class ContextQueueVirtualThreadTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContextQueueVirtualThreadTest.class);

    private Engine engine;
    private ContextQueue contextQueue;

    @BeforeEach
    void setUp() {
        engine = Engine.create();
        contextQueue = new ContextQueue(engine, "test", null, LOGGER, Optional.<MongoClient>empty(), null, Map.of());
    }

    @AfterEach
    void tearDown() {
        engine.close();
    }

    @Test
    void newContextSucceedsWhenCalledFromVirtualThread() throws Exception {
        var result = new AtomicReference<Object>();
        var failure = new AtomicReference<Throwable>();

        var vt = Thread.ofVirtual().unstarted(() -> {
            try {
                var ctx = ContextQueue.newContext(engine, "vt-test", null, LOGGER, Optional.<MongoClient>empty(), null, Map.of());
                try {
                    ctx.enter();
                    try {
                        result.set(ctx.eval("js", "1+1").asInt());
                    } finally {
                        ctx.leave();
                    }
                } finally {
                    ctx.close();
                }
            } catch (Throwable t) {
                failure.set(t);
            }
        });

        vt.start();
        vt.join();

        assertNull(failure.get(), () -> String.valueOf(failure.get()));
        assertEquals(2, result.get());
    }

    @Test
    void executeWithContextSucceedsWhenCalledFromVirtualThread() throws Exception {
        var result = new AtomicReference<Object>();
        var failure = new AtomicReference<Throwable>();

        var vt = Thread.ofVirtual().unstarted(() -> {
            try {
                ContextQueue.ContextTask<Integer> task = ctx -> ctx.eval("js", "21*2").asInt();
                result.set(contextQueue.executeWithContext(task));
            } catch (Throwable t) {
                failure.set(t);
            }
        });

        vt.start();
        vt.join();

        assertNull(failure.get(), () -> String.valueOf(failure.get()));
        assertEquals(42, result.get());
    }

    @Test
    void engineCreateSucceedsWhenOffloadedFromVirtualThread() throws Exception {
        // mirrors how JSPlugin/JSInterceptorFactory build their static Engine field
        var result = new AtomicReference<Engine>();
        var failure = new AtomicReference<Throwable>();

        var vt = Thread.ofVirtual().unstarted(() -> {
            try {
                result.set(PolyglotThreadUtils.onPlatformThread(Engine::create));
            } catch (Throwable t) {
                failure.set(t);
            }
        });

        vt.start();
        vt.join();

        assertNull(failure.get(), () -> String.valueOf(failure.get()));
        result.get().close();
    }
}
