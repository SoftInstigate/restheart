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
package org.restheart.initializers;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.restheart.plugins.Initializer;
import org.restheart.plugins.Inject;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.ThreadsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.management.HotSpotDiagnosticMXBean;

/**
 * Writes a thread dump to the log when requests stop being served.
 *
 * <p>A blocking request runs on a virtual thread of {@link ThreadsUtils#virtualThreadsExecutor()},
 * and virtual threads run on a handful of carrier threads — one or two on a container with one or
 * two vCPU. If those carriers stay occupied, no virtual thread gets to run: every blocking request
 * hangs, while a non-blocking one such as {@code /ping}, served on the I/O thread, still answers.
 * That is what a test node did once, and nobody could say why: a distroless container has no shell
 * to run {@code jcmd} from, and the moment passes before anyone could reach it anyway.
 *
 * <p>So the process watches itself. Every {@code interval-seconds} a platform thread hands the
 * request executor an empty task. If that task has not started {@code threshold-seconds} later,
 * the executor is not serving, and the whole thread dump goes to the log — virtual threads
 * included, which {@code ThreadMXBean} does not show: it is what {@code jcmd Thread.dump_to_file}
 * writes, taken through {@link HotSpotDiagnosticMXBean#dumpThreads}. Once per episode: the next
 * dump waits until the executor has served again.
 *
 * <p>The watcher itself is a platform thread, deliberately. On a virtual thread it would stop
 * with the carriers, and be silent exactly when it is needed.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(
        name = "workersWatchdog",
        description = "writes a thread dump to the log when the executor of blocking requests stops serving",
        enabledByDefault = false)
public class WorkersWatchdog implements Initializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkersWatchdog.class);

    static final int DEFAULT_INTERVAL_SECONDS = 10;
    static final int DEFAULT_THRESHOLD_SECONDS = 30;

    @Inject("config")
    private Map<String, Object> config;

    @Override
    public void init() {
        int interval = argOrDefault(config, "interval-seconds", DEFAULT_INTERVAL_SECONDS);
        int threshold = argOrDefault(config, "threshold-seconds", DEFAULT_THRESHOLD_SECONDS);

        var watch = new Watch(ThreadsUtils.virtualThreadsExecutor(), System::nanoTime, Duration.ofSeconds(threshold),
                WorkersWatchdog::threadDump, LOGGER::error, LOGGER::warn);

        var watcher = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("RH workers watchdog").factory());
        watcher.scheduleWithFixedDelay(watch::check, interval, interval, TimeUnit.SECONDS);

        LOGGER.info("Workers watchdog on: a thread dump is logged if a blocking request cannot start within {}s", threshold);
    }

    /**
     * One check at a time, driven by the watcher: hands the executor a probe when none is out,
     * and reports once when the one that is out has waited past the threshold.
     */
    static final class Watch {
        private final Executor probed;
        private final LongSupplier nanos;
        private final long thresholdNanos;
        private final Supplier<String> dump;
        private final Consumer<String> report;
        private final Consumer<String> recovered;

        /** When the outstanding probe was handed over; negative when none is out. */
        private long outSince = -1;
        private boolean reported = false;

        Watch(Executor probed, LongSupplier nanos, Duration threshold, Supplier<String> dump,
                Consumer<String> report, Consumer<String> recovered) {
            this.probed = probed;
            this.nanos = nanos;
            this.thresholdNanos = threshold.toNanos();
            this.dump = dump;
            this.report = report;
            this.recovered = recovered;
        }

        synchronized void check() {
            var now = nanos.getAsLong();

            if (outSince < 0) {
                outSince = now;
                try {
                    probed.execute(this::served);
                } catch (RejectedExecutionException shuttingDown) {
                    outSince = -1;
                }
                return;
            }

            var waited = now - outSince;

            if (!reported && waited >= thresholdNanos) {
                reported = true;
                report.accept("Blocking requests are not being served: a task handed to the request executor "
                        + TimeUnit.NANOSECONDS.toSeconds(waited) + "s ago has not started. Thread dump follows.\n"
                        + dump.get());
            }
        }

        private synchronized void served() {
            if (reported) {
                recovered.accept("Blocking requests are served again, after "
                        + TimeUnit.NANOSECONDS.toSeconds(nanos.getAsLong() - outSince) + "s");
            }

            outSince = -1;
            reported = false;
        }
    }

    /**
     * Every thread, virtual ones included, as {@code jcmd Thread.dump_to_file} writes them. Falls
     * back to the platform threads alone, with their locks, if the file cannot be written.
     */
    static String threadDump() {
        try {
            var file = Files.createTempFile("restheart-threads-", ".txt");
            Files.delete(file); // dumpThreads refuses to overwrite

            try {
                ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class)
                        .dumpThreads(file.toString(), HotSpotDiagnosticMXBean.ThreadDumpFormat.TEXT_PLAIN);
                return Files.readString(file);
            } finally {
                Files.deleteIfExists(file);
            }
        } catch (Exception e) {
            return "(virtual threads unavailable: " + e + ")\n"
                    + Arrays.stream(ManagementFactory.getThreadMXBean().dumpAllThreads(true, true))
                            .map(Object::toString)
                            .collect(Collectors.joining());
        }
    }
}
