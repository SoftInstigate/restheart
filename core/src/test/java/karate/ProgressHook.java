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
package karate;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicInteger;

import com.intuit.karate.RuntimeHook;
import com.intuit.karate.Suite;
import com.intuit.karate.core.FeatureRuntime;
import com.intuit.karate.core.ScenarioRuntime;

/**
 * Replaces Karate's normal firehose of per-request debug logging with a single progress
 * bar line, updated in place. The request/response detail is not lost — Karate always
 * captures it per step (that's what feeds the HTML report) — this hook only decides
 * whether to also dump it to stdout, which it does exclusively for failed scenarios,
 * printed right after the bar so a failure is never silently scrolled past.
 *
 * <p>The bar overwrites its line via {@code \r} rather than gating on
 * {@code System.console()}: this class runs inside the JVM forked by Maven
 * Surefire/Failsafe, whose stdout is a pipe back to the Maven process — so
 * {@code System.console()} is null there even when the user is at a real terminal
 * driving {@code mvn}, which would silently disable the bar in exactly the case that
 * matters. Under CI (detected via the {@code CI} env var that GitHub Actions and
 * virtually every other CI provider sets, and which survives the Maven fork same as any
 * other env var) it prints a plain line instead: a raw/downloaded CI log has no terminal
 * to interpret {@code \r} as an overwrite, so that mode would otherwise leave literal
 * control characters glued together as one unreadable blob.
 */
public class ProgressHook implements RuntimeHook {

    private static final int BAR_WIDTH = 20;
    private static final boolean CI = System.getenv("CI") != null;
    private static final PrintStream NULL_OUT = new PrintStream(OutputStream.nullOutputStream());

    private final AtomicInteger featuresDone = new AtomicInteger();
    private final AtomicInteger scenariosDone = new AtomicInteger();
    private final AtomicInteger scenariosFailed = new AtomicInteger();
    private volatile int featuresTotal = 1;
    private volatile PrintStream realOut;
    private volatile String currentFeature = "";

    @Override
    public void beforeSuite(Suite suite) {
        featuresTotal = Math.max(suite.featuresFound, 1);
    }

    @Override
    public boolean beforeFeature(FeatureRuntime fr) {
        // Undoes the afterFeature() suppression below, right before this (real, selected)
        // feature's own scenarios would need to print anything.
        if (fr.caller.isNone()) {
            currentFeature = shortName(fr.featureCall.feature.toString());
            if (realOut != null) {
                System.setOut(realOut);
                realOut = null;
            }
            // Refresh immediately: the previous feature's bar is still on screen
            // (afterFeature() below deliberately left it there instead of clearing it),
            // so this overwrites straight to the new state in one step — no blank frame
            // in between, which is what caused the flicker at every feature boundary.
            printProgress();
        }
        return true;
    }

    @Override
    public void afterScenario(ScenarioRuntime sr) {
        // sr.caller is set whenever this scenario belongs to a feature invoked via
        // karate.call()/call read(...) (e.g. helpers/put-order.feature) rather than one
        // of the top-level features the Runner selected — those aren't part of
        // featuresTotal, so counting them here would double-count against it.
        if (!sr.caller.isNone()) {
            return;
        }
        scenariosDone.incrementAndGet();
        if (sr.result.isFailed()) {
            scenariosFailed.incrementAndGet();
            if (!CI) {
                System.out.print("\r\033[K");
            }
            System.out.println("FAILED: " + sr.scenario.getDebugInfo());
            for (var stepResult : sr.result.getStepResults()) {
                var log = stepResult.getStepLog();
                if (log != null && !log.isBlank()) {
                    System.out.print(log);
                }
            }
        }
        printProgress();
    }

    @Override
    public void afterFeature(FeatureRuntime fr) {
        // Same reasoning as afterScenario: a feature called via karate.call()/call
        // read(...) from within a scenario also triggers this hook (see the stack trace
        // through ScenarioEngine.callFeature() that motivated this guard), and isn't one
        // of the top-level features featuresTotal was computed from.
        if (!fr.caller.isNone()) {
            return;
        }
        featuresDone.incrementAndGet();
        if (CI) {
            // Under CI there's no bar to protect (printProgress() is a no-op there — see
            // below), so Karate's own per-feature summary is left alone: with nothing
            // being overwritten via \r, it can't glue onto anything, and it's exactly the
            // per-feature detail CI logs should show.
            return;
        }
        // Deliberately not clearing or updating the bar here: whatever the last
        // afterScenario() printed stays on screen, untouched, for the whole suppression
        // window below. beforeFeature() overwrites it in one shot once the next feature's
        // real numbers are ready — clearing it here first would leave a blank line visible
        // for that whole window instead (the flicker this used to cause).
        //
        // Karate has no config flag to suppress its own per-feature summary: FeatureResult
        // .printStats() is an unconditional raw System.out.println() called from
        // Suite.onFeatureDone(), synchronously right after this hook returns
        // (single-threaded — .parallel(1) — so nothing else can print in between).
        // Swapping System.out for this narrow window discards it; beforeFeature() above
        // restores the real stream before the next feature's own scenarios run, and
        // afterSuite() below is a safety net for the last feature, which has no "next" to
        // trigger that restore.
        realOut = System.out;
        System.setOut(NULL_OUT);
    }

    @Override
    public void afterSuite(Suite suite) {
        if (realOut != null) {
            System.setOut(realOut);
            realOut = null;
        }
        // afterFeature() only fires for features with at least one tag-selected scenario
        // (see FeatureRuntime.afterFeature(), guarded by !result.isEmpty()), so
        // featuresDone never reaches featuresTotal on its own when features are entirely
        // tag-skipped. Snap it here so the bar reads 100% once the suite is actually done.
        if (CI) {
            // Nothing was ever suppressed or printed by this hook under CI — Karate's own
            // per-feature and final summaries already covered it.
            return;
        }
        featuresDone.set(featuresTotal);
        printProgress();
        System.out.println();
    }

    // Just the filename, truncated, instead of the full "classpath:karate/stripe/..." path:
    // Java has no portable way to read the terminal's current column width, so the line is
    // kept short enough by construction to stay on one row on any reasonably-sized terminal
    // instead - \r\033[K only erases the current physical row, so if the line wrapped onto
    // a second one (long feature path, or a narrow/just-resized terminal) that row is left
    // behind and the bar appears to break. A terminal narrower than ~100 columns can still
    // wrap a long filename; there's no fully portable fix for that short of a terminal lib.
    private static final int MAX_FEATURE_NAME = 30;

    private static String shortName(String featurePath) {
        var slash = featurePath.lastIndexOf('/');
        var name = slash < 0 ? featurePath : featurePath.substring(slash + 1);
        return name.length() <= MAX_FEATURE_NAME ? name : "…" + name.substring(name.length() - MAX_FEATURE_NAME + 1);
    }

    private void printProgress() {
        if (CI) {
            // Bar disabled under CI: only Karate's own per-feature/final summaries show,
            // per request — a live-updating bar has no value in a static log anyway.
            return;
        }
        var done = featuresDone.get();
        var filled = Math.clamp((long) ((done / (double) featuresTotal) * BAR_WIDTH), 0, BAR_WIDTH);
        var bar = "#".repeat(filled) + "-".repeat(BAR_WIDTH - filled);
        var line = String.format("[%s] %d/%d features | %d scenarios (%d failed) | %s",
                bar, done, featuresTotal, scenariosDone.get(), scenariosFailed.get(), currentFeature);
        System.out.print("\r\033[K" + line);
        System.out.flush();
    }
}
