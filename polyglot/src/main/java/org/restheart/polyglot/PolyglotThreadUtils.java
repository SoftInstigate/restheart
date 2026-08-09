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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private static final ExecutorService PLATFORM_EXECUTOR = Executors
            .newCachedThreadPool(Thread.ofPlatform().name("RH JS PLT-", 0).factory());

    private PolyglotThreadUtils() {}

    /**
     * Runs the given task on a platform thread and waits for its result.
     *
     * @param task the task to run
     * @return the result of the task
     * @throws Exception if the task throws an exception
     */
    public static <T> T onPlatformThread(Callable<T> task) throws Exception {
        try {
            return PLATFORM_EXECUTOR.submit(task).get();
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
