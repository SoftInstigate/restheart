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

import org.restheart.emails.EmailSender;
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
 * <p>With {@code halt-after-seconds} set, a process still not serving that long after the probe
 * went out terminates itself, with exit status {@value #HALT_STATUS}, for its orchestrator to
 * replace: a node whose carriers are stuck does not recover on its own, and on ECS a task that
 * stops is started again. Off by default, and never before the dump has been written. It halts
 * rather than exits: an exit runs the shutdown hooks, and RESTHeart's waits for the requests in
 * flight — which will never complete — while some of its work runs on virtual threads, which have
 * no carrier left to run on. The process would hang in the act of stopping.
 *
 * <p>With {@code notify-email} set and the {@code emails} provider configured, the same report is
 * also mailed to that address, once per episode — a log nobody reads until the next morning is
 * not an alarm. The mail goes out on a platform thread of its own, never through
 * {@code sendEmailAsync}, which would queue it on the very executor that is stuck; and a halt waits
 * a little for it, so the process does not die with the alarm still unsent.
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

    /** Not 1: an orchestrator's log of stopped tasks should say this was the watchdog. */
    static final int HALT_STATUS = 70;

    /**
     * How long a halt waits, at most, for the alarm mail to go out, on top of the time it has
     * already had: it leaves with the dump, not with the halt. The SMTP client gives up on its own
     * after 10s connecting and 60s on the socket, so a mail that has not gone by then will not.
     */
    static final Duration MAIL_BEFORE_HALT = Duration.ofSeconds(60);

    @Inject("config")
    private Map<String, Object> config;

    @Inject(value = "emails", required = false)
    private EmailSender emailSender;

    /** The thread of the last alarm mail, for a halt to wait on. */
    private volatile Thread mailing;

    @Override
    public void init() {
        int interval = argOrDefault(config, "interval-seconds", DEFAULT_INTERVAL_SECONDS);
        int threshold = argOrDefault(config, "threshold-seconds", DEFAULT_THRESHOLD_SECONDS);
        int haltAfter = argOrDefault(config, "halt-after-seconds", 0);

        // never before the dump: the dump is what says why
        var halt = haltAfter <= 0 ? null : Duration.ofSeconds(Math.max(haltAfter, threshold));

        String notifyEmail = argOrDefault(config, "notify-email", null);
        var mail = notifyEmail != null && !notifyEmail.isBlank() && emailSender != null && emailSender.isEnabled();

        if (notifyEmail != null && !notifyEmail.isBlank() && !mail) {
            LOGGER.warn("Workers watchdog: notify-email is set but the emails provider is not configured; the dump goes to the log only");
        }

        Consumer<String> report = mail
                ? message -> {
            LOGGER.error(message);
            mail(notifyEmail.strip(), message, halt);
        }
                : LOGGER::error;

        var watch = new Watch(ThreadsUtils.virtualThreadsExecutor(), System::nanoTime, Duration.ofSeconds(threshold),
                WorkersWatchdog::threadDump, report, LOGGER::warn, halt, this::halt);

        var watcher = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("RH workers watchdog").factory());
        watcher.scheduleWithFixedDelay(watch::check, interval, interval, TimeUnit.SECONDS);

        if (halt == null) {
            LOGGER.info("Workers watchdog on: a thread dump is logged if a blocking request cannot start within {}s", threshold);
        } else {
            LOGGER.info("Workers watchdog on: a thread dump is logged if a blocking request cannot start within {}s, "
                    + "and the process halts with status {} if it still cannot after {}s", threshold, HALT_STATUS, halt.toSeconds());
        }
    }

    private void halt() {
        LOGGER.error("Halting with status {}: blocking requests are still not being served, and this process will "
                + "not recover by itself. Its orchestrator is expected to replace it.", HALT_STATUS);

        var alarm = this.mailing;

        if (alarm != null && alarm.isAlive()) {
            try {
                alarm.join(MAIL_BEFORE_HALT);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }

        Runtime.getRuntime().halt(HALT_STATUS);
    }

    /** Mails the report on a platform thread of its own: see the class comment for why. */
    private void mail(String to, String report, Duration halt) {
        var host = hostName();
        var subject = "RESTHeart on " + host + " is not serving requests";
        var then = halt == null
                ? "The process keeps running; it will not recover by itself."
                : "Unless it recovers, the process halts " + halt.toSeconds()
                + "s after the stall began, with status " + HALT_STATUS + ", for its orchestrator to replace it.";
        var body = "<p>" + escape(subject) + ". " + escape(then) + "</p>"
                + "<p>The virtual threads named <code>RH VRT WRK</code> that are RUNNABLE with a stack are the ones "
                + "holding the carriers.</p><pre style=\"font-size:11px\">" + escape(report) + "</pre>";

        var thread = Thread.ofPlatform().daemon().name("RH workers watchdog mail").unstarted(() -> {
            try {
                emailSender.sendEmail(to, to, subject, body);
                LOGGER.info("Workers watchdog: alarm mailed to {}", to);
            } catch (Throwable t) {
                LOGGER.error("Workers watchdog: could not mail the alarm to {}", to, t);
            }
        });

        this.mailing = thread;
        thread.start();
    }

    private static String hostName() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown host";
        }
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
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
        /** How long a probe may stay out before {@link #halter} runs; {@code -1} never. */
        private final long haltNanos;
        private final Runnable halter;

        /** When the outstanding probe was handed over; negative when none is out. */
        private long outSince = -1;
        private boolean reported = false;

        Watch(Executor probed, LongSupplier nanos, Duration threshold, Supplier<String> dump,
              Consumer<String> report, Consumer<String> recovered, Duration halt, Runnable halter) {
            this.probed = probed;
            this.nanos = nanos;
            this.thresholdNanos = threshold.toNanos();
            this.dump = dump;
            this.report = report;
            this.recovered = recovered;
            this.haltNanos = halt == null ? -1 : halt.toNanos();
            this.halter = halter;
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

            if (reported && haltNanos >= 0 && waited >= haltNanos) {
                halter.run();
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
