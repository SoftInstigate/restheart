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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import org.graalvm.polyglot.Engine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClient;

/**
 * Integration tests for the full polyglot Context lifecycle on the dedicated
 * platform thread.  These tests verify the fix for oracle/graal#7520:
 *
 * <ul>
 *   <li>Contexts created on the platform thread can be entered/evaluated
 *       without {@code ArrayIndexOutOfBoundsException} from
 *       {@code DefaultContextThreadLocal.fastGet()}.</li>
 *   <li>Bindings ({@code LOGGER}, {@code mclient}, {@code pluginArgs}) are
 *       accessible inside entered contexts.</li>
 *   <li>Contexts pooled by {@link ContextQueue} work correctly when
 *       {@code executeWithContext()} is called from virtual threads.</li>
 *   <li>Multiple concurrent virtual threads can use the same
 *       {@link ContextQueue} without corruption.</li>
 * </ul>
 */
class ContextLifecycleTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContextLifecycleTest.class);

    private static Engine engine;

    @BeforeAll
    static void createEngine() {
        engine = Engine.create();
    }

    @AfterAll
    static void closeEngine() {
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    void newContextReturnsBareContext() {
        var ctx = ContextQueue.newContext(engine, "test", null, LOGGER,
                Optional.<MongoClient>empty(), null, Map.of());
        assertNotNull(ctx);
        ctx.enter();
        try {
            var result = ctx.eval("js", "1+1").asInt();
            assertEquals(2, result);
        } finally {
            ctx.leave();
            ctx.close();
        }
    }

    @Test
    void addBindingsMakesValuesAccessibleFromJS() {
        var ctx = ContextQueue.newContext(engine, "test", null, LOGGER,
                Optional.<MongoClient>empty(), null, Map.of());
        ctx.enter();
        try {
            ContextQueue.addBindings(ctx, "test", null, LOGGER,
                    Optional.<MongoClient>empty());

            var hasLogger = ctx.eval("js", "typeof LOGGER !== 'undefined'").asBoolean();
            assertTrue(hasLogger, "LOGGER binding should be accessible");

            var hasArgs = ctx.eval("js", "typeof pluginArgs !== 'undefined'").asBoolean();
            assertTrue(hasArgs, "pluginArgs binding should be accessible");
        } finally {
            ctx.leave();
            ctx.close();
        }
    }

    @Test
    void pooledContextWorksFromVirtualThread() throws Exception {
        var cq = new ContextQueue(engine, "test-pool", null, LOGGER,
                Optional.<MongoClient>empty(), null, Map.of());

        var result = new AtomicReference<Object>();
        var failure = new AtomicReference<Throwable>();

        var vt = Thread.ofVirtual().unstarted(() -> {
            try {
                result.set(cq.executeWithContext((ContextQueue.ContextTask<Integer>) ctx ->
                        ctx.eval("js", "21*2").asInt()));
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        vt.start();
        vt.join();

        assertNull(failure.get(), () -> "virtual thread failed: " + failure.get());
        assertEquals(42, result.get());
    }

    @Test
    void concurrentVirtualThreadsUseSameContextQueue() throws Exception {
        var cq = new ContextQueue(engine, "test-concurrent", null, LOGGER,
                Optional.<MongoClient>empty(), null, Map.of());

        var failures = new AtomicReference<String>();

        var threads = IntStream.range(0, 20)
                .mapToObj(i -> Thread.ofVirtual().unstarted(() -> {
                    try {
                        var expected = i * i;
                        var actual = cq.executeWithContext((ContextQueue.ContextTask<Integer>) ctx ->
                                ctx.eval("js", i + "*" + i).asInt());
                        if (actual != expected) {
                            failures.compareAndSet(null,
                                    "expected " + expected + " but got " + actual);
                        }
                    } catch (Throwable t) {
                        failures.compareAndSet(null,
                                "thread " + i + " failed: " + t.getMessage());
                    }
                }))
                .toList();

        for (var t : threads) t.start();
        for (var t : threads) t.join();

        assertNull(failures.get(), failures::get);
    }

    @Test
    void engineCreatedByOnPlatformThreadWorksForContextOps() throws Exception {
        var pltEngine = PolyglotThreadUtils.onPlatformThread(Engine::create);
        assertNotNull(pltEngine);

        try {
            var ctx = ContextQueue.newContext(pltEngine, "test", null, LOGGER,
                    Optional.<MongoClient>empty(), null, Map.of());
            ctx.enter();
            try {
                var result = ctx.eval("js", "'hello' + ' world'").asString();
                assertEquals("hello world", result);
            } finally {
                ctx.leave();
                ctx.close();
            }
        } finally {
            pltEngine.close();
        }
    }
}
