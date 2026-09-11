/*-
 * ========================LICENSE_START=================================
 * restheart-ai
 * %%
 * Copyright (C) 2024 - 2026 SoftInstigate
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
package org.restheart.ai.mcp;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns "this resource's data changed" into at most one {@code notifications/resources/updated}
 * per resource per interval (#617).
 *
 * <p>The rate limit is not a compromise, it is the semantics. The notification carries no payload:
 * it tells a client to re-read, and a client that has been told once has nothing more to learn
 * from being told again before it reads. A collection taking a thousand writes a second must
 * produce one notification per interval, not a thousand.
 *
 * <p>The first change in a quiet period fires immediately — a subscriber should not wait out an
 * interval for a change that just happened — and further changes within the interval are collapsed
 * into a single trailing notification, so nothing is lost either.
 */
public class ResourceSubscriptions {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceSubscriptions.class);

    private final Duration interval;
    private final Consumer<String> notify;

    /** One per watched URI; present only while that URI has a watch. */
    private final Map<String, Coalescer> coalescers = new ConcurrentHashMap<>();

    public ResourceSubscriptions(Duration interval, Consumer<String> notify) {
        this.interval = interval;
        this.notify = notify;
    }

    /** Records a change on {@code uri}, notifying now or scheduling the interval's single trailing notification. */
    public void changed(String uri) {
        coalescers.computeIfAbsent(uri, Coalescer::new).changed();
    }

    /** Drops the coalescer for a URI nobody watches any more, along with any notification it still owed. */
    public void forget(String uri) {
        var coalescer = coalescers.remove(uri);
        if (coalescer != null) {
            coalescer.cancelled.set(true);
        }
    }

    public int trackedUris() {
        return coalescers.size();
    }

    private final class Coalescer {
        private final String uri;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        /** True while an interval is running; a change arriving then is folded into its trailing notification. */
        private final AtomicBoolean windowOpen = new AtomicBoolean(false);
        private final AtomicBoolean pending = new AtomicBoolean(false);

        Coalescer(String uri) {
            this.uri = uri;
        }

        void changed() {
            if (cancelled.get()) {
                return;
            }

            if (windowOpen.compareAndSet(false, true)) {
                fire();
                scheduleTrailing();
            } else {
                pending.set(true);
            }
        }

        private void scheduleTrailing() {
            Thread.ofVirtual().name("mcp-notify-" + uri).start(() -> {
                try {
                    Thread.sleep(interval);

                    // one notification for everything that happened during the interval, sent
                    // before the window closes: closing first would let a change arriving in
                    // between open a new window and fire immediately, right next to this one
                    if (pending.compareAndSet(true, false) && !cancelled.get()) {
                        fire();
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } finally {
                    windowOpen.set(false);

                    // A change that arrived after the pending flag was read above, but before this
                    // line, set the flag with nobody left scheduled to consume it: the window was
                    // still open, so changed() took the else branch and returned. Another change
                    // would pick it up, but on a resource that then goes quiet the last one is
                    // simply never delivered — and "re-read" notifications going missing is
                    // indistinguishable from nothing having happened.
                    //
                    // The CAS is what keeps this from firing twice: if changed() has already
                    // reopened the window it has also fired and scheduled, and we do nothing.
                    if (pending.get() && !cancelled.get() && windowOpen.compareAndSet(false, true)) {
                        scheduleTrailing();
                    }
                }
            });
        }

        private void fire() {
            try {
                notify.accept(uri);
            } catch (Exception e) {
                LOGGER.warn("failed to notify subscribers that {} changed", uri, e);
            }
        }
    }
}
