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

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.graalvm.polyglot.Engine;
import org.restheart.exchange.BadRequestException;
import org.restheart.utils.HttpStatus;

/**
 * Runs GraalVM polyglot Context creation, enter/leave and eval on a platform thread.
 *
 * <p>Truffle's DefaultContextThreadLocal does not yet support virtual threads
 * (see https://github.com/oracle/graal/issues/7520): entering a Context from a
 * virtual thread can throw {@code ArrayIndexOutOfBoundsException}. RESTHeart
 * runs on Java 25, where virtual threads are used pervasively, so all Context
 * operations must be offloaded to a platform thread.</p>
 */
public final class PolyglotThreadUtils {
    // bounded, unlike newCachedThreadPool: an unbounded pool lets concurrent load
    // spawn unlimited platform threads, which is both a resource-exhaustion risk
    // and an implicit DoS vector; requests beyond the cap are rejected immediately
    // instead of queueing or running on the caller thread (which could be virtual).
    private static final int MAX_POOL_SIZE = Math.max(64, Runtime.getRuntime().availableProcessors() * 16);

    private static final ExecutorService PLATFORM_EXECUTOR = new ThreadPoolExecutor(
            0, MAX_POOL_SIZE,
            60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            Thread.ofPlatform().name("RH JS PLT-", 0).factory());

    private PolyglotThreadUtils() {}

    /**
     * Creates a polyglot Engine with the PluginsClassloader as context classloader.
     *
     * <p>Declared here (not as a lambda body inside JSPlugin's static initializer)
     * so that invoking it from a platform thread never needs to wait on JSPlugin's
     * own class-initialization monitor, which would deadlock since JSPlugin's
     * &lt;clinit&gt; is the one submitting this task and blocking on its result.</p>
     *
     * @return a newly created Engine
     */
    public static Engine createEngine() throws IOException {
        return PolyglotClassloaderHelper.withPluginsClassloaderResult(Engine::create);
    }

    /**
     * Runs the given task on a platform thread and waits for its result.
     *
     * @param task the task to run
     * @return the result of the task
     * @throws Exception if the task throws an exception, or a {@link BadRequestException}
     *                    with status 429 if the platform thread pool is saturated
     */
    public static <T> T onPlatformThread(Callable<T> task) throws Exception {
        try {
            return PLATFORM_EXECUTOR.submit(task).get();
        } catch (RejectedExecutionException ree) {
            throw new BadRequestException("Too many concurrent polyglot operations, please retry later",
                    HttpStatus.SC_TOO_MANY_REQUESTS);
        } catch (ExecutionException e) {
            var cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            } else if (cause instanceof Error err) {
                throw err;
            } else {
                throw e;
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw ie;
        }
    }
}
