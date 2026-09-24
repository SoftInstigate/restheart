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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;

public class WorkersWatchdogTest {

    /** An executor that holds its tasks until told to run them: a stuck one, then a recovered one. */
    static final class HeldExecutor implements Executor {
        final List<Runnable> held = new ArrayList<>();

        @Override
        public void execute(Runnable task) {
            held.add(task);
        }

        void release() {
            var tasks = List.copyOf(held);
            held.clear();
            tasks.forEach(Runnable::run);
        }
    }

    private long now = 0;
    private final List<String> reports = new ArrayList<>();
    private final List<String> recoveries = new ArrayList<>();
    private int halts = 0;

    private WorkersWatchdog.Watch watch(Executor executor) {
        return watch(executor, null);
    }

    private WorkersWatchdog.Watch watch(Executor executor, Duration halt) {
        return new WorkersWatchdog.Watch(executor, () -> now, Duration.ofSeconds(30), () -> "THE DUMP",
                reports::add, recoveries::add, halt, () -> halts++);
    }

    private void advance(int seconds) {
        now += Duration.ofSeconds(seconds).toNanos();
    }

    @Test
    public void anExecutorThatServesIsNeverReported() {
        var watch = watch(Runnable::run);

        for (int i = 0;i < 10;i++) {
            watch.check();
            advance(10);
        }

        assertEquals(List.of(), reports);
    }

    @Test
    public void aProbeThatDoesNotStartIsReportedOnceWithTheDump() {
        var executor = new HeldExecutor();
        var watch = watch(executor);

        watch.check();          // probe handed over
        advance(20);
        watch.check();          // waited 20s: under the threshold
        assertEquals(List.of(), reports);

        advance(10);
        watch.check();          // 30s: reported
        assertEquals(1, reports.size());
        assertTrue(reports.get(0).contains("THE DUMP"), reports.get(0));

        advance(60);
        watch.check();          // still stuck: not reported again
        assertEquals(1, reports.size(), "once per episode");
        assertEquals(1, executor.held.size(), "no second probe while the first is out");
    }

    @Test
    public void recoveringClosesTheEpisodeAndTheNextOneIsReportedAgain() {
        var executor = new HeldExecutor();
        var watch = watch(executor);

        watch.check();
        advance(40);
        watch.check();
        assertEquals(1, reports.size());

        executor.release();     // the executor serves again
        assertEquals(1, recoveries.size());
        assertTrue(recoveries.get(0).contains("40s"), recoveries.get(0));

        watch.check();          // a new probe, stuck again
        advance(30);
        watch.check();
        assertEquals(2, reports.size(), "a new episode is reported");
    }

    @Test
    public void withoutHaltItNeverHalts() {
        var watch = watch(new HeldExecutor());

        watch.check();
        advance(3600);
        watch.check();

        assertEquals(1, reports.size());
        assertEquals(0, halts);
    }

    @Test
    public void aProcessStillStuckAfterTheHaltDelayHaltsAfterTheDump() {
        var watch = watch(new HeldExecutor(), Duration.ofSeconds(120));

        watch.check();
        advance(30);
        watch.check();          // the dump first
        assertEquals(1, reports.size());
        assertEquals(0, halts, "not before the halt delay");

        advance(60);
        watch.check();
        assertEquals(0, halts);

        advance(30);
        watch.check();          // 120s out: halts
        assertEquals(1, halts);
    }

    @Test
    public void recoveringBeforeTheHaltDelayDoesNotHalt() {
        var executor = new HeldExecutor();
        var watch = watch(executor, Duration.ofSeconds(120));

        watch.check();
        advance(60);
        watch.check();
        executor.release();

        advance(120);
        watch.check();          // a fresh probe, served at once
        executor.release();
        watch.check();

        assertEquals(0, halts);
    }

    @Test
    public void theDumpIncludesTheCurrentThread() {
        // the real dump: whatever the JVM, it names the thread asking for it
        assertTrue(WorkersWatchdog.threadDump().contains(Thread.currentThread().getName()));
    }
}
