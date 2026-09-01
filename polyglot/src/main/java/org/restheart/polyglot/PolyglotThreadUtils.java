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
import java.util.concurrent.Executors;

import org.graalvm.polyglot.Engine;

/**
 * Runs GraalVM polyglot Context creation, enter/leave and eval on a dedicated
 * single platform thread.
 *
 * <p>Truffle's {@code DefaultContextThreadLocal} (see oracle/graal#7520) stores
 * per-thread state in a fixed-size slot array indexed by an internal counter.
 * When Engine and Context objects are created on one thread and then used from
 * a different thread, the slot indices can resolve to {@code -1}, causing an
 * {@code ArrayIndexOutOfBoundsException}. Using a <em>single</em> dedicated
 * thread for <strong>all</strong> polyglot operations eliminates the
 * cross-thread access entirely.</p>
 *
 * <p>When the Truffle bug is fixed, set the system property
 * {@code restheart.polyglot.force-platform-threads=false} to let polyglot
 * operations run on the caller thread (virtual or platform) directly,
 * bypassing the dedicated thread entirely.</p>
 */
public final class PolyglotThreadUtils {
    /**
     * System property to control whether polyglot operations are forced onto
     * a dedicated platform thread. Defaults to {@code true} (workaround for
     * oracle/graal#7520). Set to {@code false} once Truffle fully supports
     * virtual threads.
     */
    private static final String FORCE_PLATFORM_PROP = "restheart.polyglot.force-platform-threads";

    private static final boolean FORCE_PLATFORM;

    static {
        FORCE_PLATFORM = Boolean.parseBoolean(
                System.getProperty(FORCE_PLATFORM_PROP, "true"));
    }

    // Single dedicated platform thread for ALL Truffle operations.
    // Using one thread avoids DefaultContextThreadLocal cross-thread corruption
    // (see oracle/graal#7520).  The unbounded queue serialises polyglot work;
    // under extreme concurrency this adds latency but never crashes.
    private static volatile ExecutorService platformExecutor;

    private static ExecutorService getPlatformExecutor() {
        if (platformExecutor == null) {
            synchronized (PolyglotThreadUtils.class) {
                if (platformExecutor == null) {
                    platformExecutor = Executors.newSingleThreadExecutor(runnable -> {
                        return Thread.ofPlatform().name("RH JS PLT", 0).unstarted(() -> {
                            // Set PluginsClassloader on the platform thread so that ALL
                            // Truffle operations use the same classloader context.
                            var pluginsCl = PolyglotClassloaderHelper.getPluginsClassloader();
                            if (pluginsCl != null) {
                                Thread.currentThread().setContextClassLoader(pluginsCl);
                            }
                            runnable.run();
                        });
                    });
                }
            }
        }
        return platformExecutor;
    }

    private PolyglotThreadUtils() {
    }

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
     * Runs the given task on the dedicated platform thread and waits for its result.
     *
     * <p>When {@code restheart.polyglot.force-platform-threads} is {@code true}
     * (the default), the task is dispatched to the single dedicated platform
     * thread. When set to {@code false}, the task runs directly on the caller
     * thread.</p>
     *
     * @param task the task to run
     * @return the result of the task
     * @throws Exception if the task throws an exception
     */
    public static <T> T onPlatformThread(Callable<T> task) throws Exception {
        if (!FORCE_PLATFORM) {
            return task.call();
        }

        try {
            return getPlatformExecutor().submit(task).get();
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

    /**
     * Returns whether polyglot operations are currently forced onto platform threads.
     * Useful for diagnostics and tests.
     */
    public static boolean isForcePlatform() {
        return FORCE_PLATFORM;
    }

    /**
     * Returns {@code true} if the calling thread is the dedicated platform
     * thread used by {@link #onPlatformThread(Callable)}.  Useful to avoid
     * self-deadlock when already running inside a platform-thread lambda.
     */
    public static boolean isAlreadyOnPlatformThread() {
        return FORCE_PLATFORM
                && platformExecutor != null
                && Thread.currentThread().getName().startsWith("RH JS PLT");
    }

    /**
     * IOException-friendly variant of {@link #onPlatformThread(Callable)}.
     */
    public static <T> T onPlatformThreadIO(Callable<T> task) throws java.io.IOException, InterruptedException {
        try {
            return onPlatformThread(task);
        } catch (java.io.IOException | InterruptedException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new java.io.IOException(e);
        }
    }
}
