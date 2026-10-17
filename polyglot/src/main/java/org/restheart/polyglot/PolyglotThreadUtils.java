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

/**
 * Runs a Truffle operation, Engine and Context creation, enter, eval, leave and close, on the
 * calling thread with the {@code PluginsClassloader} as its context classloader.
 *
 * <p>Truffle resolves a language's fast-thread-local slot by looking its class up through the
 * thread's context classloader, and returns {@code -1} when it is not found
 * ({@code PolyglotFastThreadLocals.RESERVED_NULL}). {@code js-language} lives in
 * {@code plugins/lib}, which only the {@code PluginsClassloader} sees, so a thread whose context
 * classloader is the system one resolves JavaScript to {@code -1} and the next lookup fails with
 * {@code ArrayIndexOutOfBoundsException: Index -1} in {@code DefaultContextThreadLocal.fastGet}.
 * That is the failure #663 met, and it has nothing to do with the kind of thread: Truffle
 * supports virtual threads on HotSpot since 24.1 (GR-40931), and the
 * {@code DefaultContextThreadLocal} is a plain {@code ThreadLocal}.</p>
 *
 * <p>So the operation runs where it is called, on the request's virtual thread like every other
 * plugin, and only the classloader is set around it. One JS plugin blocking on I/O stalls no
 * other. A pooled Context is entered by a different virtual thread on every request, which is
 * fine: Truffle sweeps the threads that have terminated from a context's thread table on the
 * next enter.</p>
 *
 * <p>The system property {@code restheart.polyglot.force-platform-threads=true} is the escape
 * hatch: every operation is then serialized on one dedicated platform thread, {@code RH JS PLT},
 * as 9.7.2 to 9.8.x did. It exists to compare behaviour, and goes away once the change has been
 * exercised in production.</p>
 *
 * @see <a href="https://github.com/SoftInstigate/restheart/issues/665">#665</a>
 */
public final class PolyglotThreadUtils {
    /**
     * System property that serializes every Truffle operation on one dedicated platform thread.
     * Defaults to {@code false}.
     */
    private static final String FORCE_PLATFORM_PROP = "restheart.polyglot.force-platform-threads";

    private static final boolean FORCE_PLATFORM = Boolean.parseBoolean(System.getProperty(FORCE_PLATFORM_PROP, "false"));

    private static final String PLATFORM_THREAD_NAME = "RH JS PLT";

    // The single platform thread of the escape hatch, created on first use.
    private static volatile ExecutorService platformExecutor;

    private static ExecutorService getPlatformExecutor() {
        if (platformExecutor == null) {
            synchronized (PolyglotThreadUtils.class) {
                if (platformExecutor == null) {
                    platformExecutor = Executors.newSingleThreadExecutor(runnable -> Thread.ofPlatform().name(PLATFORM_THREAD_NAME, 0).unstarted(() -> {
                        var pluginsCl = PolyglotClassloaderHelper.getPluginsClassloader();
                        if (pluginsCl != null) {
                            Thread.currentThread().setContextClassLoader(pluginsCl);
                        }
                        runnable.run();
                    }));
                }
            }
        }
        return platformExecutor;
    }

    private PolyglotThreadUtils() {
    }

    /**
     * Runs a Truffle operation with the {@code PluginsClassloader} as the context classloader of
     * the calling thread, and restores the previous one afterwards.
     *
     * <p>With the escape hatch on, the task is dispatched to the dedicated platform thread and
     * the caller waits for it, unless the caller already is that thread.</p>
     *
     * @param task the operation
     * @return what the operation returns
     * @throws Exception whatever the operation throws
     */
    public static <T> T run(Callable<T> task) throws Exception {
        if (!FORCE_PLATFORM || isAlreadyOnPlatformThread()) {
            return withPluginsClassloader(task);
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

    private static <T> T withPluginsClassloader(Callable<T> task) throws Exception {
        var pluginsCl = PolyglotClassloaderHelper.getPluginsClassloader();
        if (pluginsCl == null) {
            return task.call();
        }

        var thread = Thread.currentThread();
        var oldCl = thread.getContextClassLoader();
        thread.setContextClassLoader(pluginsCl);
        try {
            return task.call();
        } finally {
            thread.setContextClassLoader(oldCl);
        }
    }

    /**
     * {@link #run(Callable)} for callers that declare {@code IOException}: any other checked
     * exception is wrapped in one.
     */
    public static <T> T runIO(Callable<T> task) throws IOException, InterruptedException {
        try {
            return run(task);
        } catch (IOException | InterruptedException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    /**
     * Whether the escape hatch is on. For diagnostics and tests.
     */
    public static boolean isForcePlatform() {
        return FORCE_PLATFORM;
    }

    /**
     * Whether the calling thread is the dedicated platform thread of the escape hatch.
     */
    public static boolean isAlreadyOnPlatformThread() {
        return FORCE_PLATFORM
                && platformExecutor != null
                && Thread.currentThread().getName().startsWith(PLATFORM_THREAD_NAME);
    }
}
