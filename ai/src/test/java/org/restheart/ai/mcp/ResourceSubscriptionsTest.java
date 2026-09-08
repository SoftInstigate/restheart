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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * A collection taking a thousand writes a second must produce one notification per interval, not a
 * thousand — {@code notifications/resources/updated} carries no payload, so a client already told
 * to re-read learns nothing from being told again.
 */
public class ResourceSubscriptionsTest {

    private static final Duration INTERVAL = Duration.ofMillis(200);

    private final List<String> sent = new CopyOnWriteArrayList<>();
    private final ResourceSubscriptions subs = new ResourceSubscriptions(INTERVAL, sent::add);

    private void settle() throws Exception {
        TimeUnit.MILLISECONDS.sleep(INTERVAL.toMillis() * 3);
    }

    @Test
    public void aChangeInAQuietPeriodIsSentImmediately() {
        subs.changed("uri");

        // a subscriber must not wait out an interval for something that just happened
        assertEquals(List.of("uri"), sent);
    }

    @Test
    public void aBurstCollapsesToTwo_theLeadingOneAndOneTrailing() throws Exception {
        for (var i = 0; i < 1_000; i++) {
            subs.changed("uri");
        }
        settle();

        assertEquals(2, sent.size(), "expected the immediate notification and one for the rest, got " + sent);
    }

    @Test
    public void nothingTrailsWhenTheBurstWasASingleChange() throws Exception {
        subs.changed("uri");
        settle();

        assertEquals(1, sent.size(), sent.toString());
    }

    @Test
    public void afterTheIntervalANewChangeIsImmediateAgain() throws Exception {
        subs.changed("uri");
        settle();
        sent.clear();

        subs.changed("uri");

        assertEquals(List.of("uri"), sent);
    }

    @Test
    public void urisAreRateLimitedIndependently() {
        subs.changed("a");
        subs.changed("b");

        assertTrue(sent.containsAll(List.of("a", "b")), sent.toString());
    }

    @Test
    public void forgettingAUriDropsTheNotificationItStillOwed() throws Exception {
        subs.changed("uri");
        subs.changed("uri");
        subs.forget("uri");
        settle();

        // the immediate one was already out; the trailing one belongs to a subscription that is gone
        assertEquals(1, sent.size(), sent.toString());
        assertEquals(0, subs.trackedUris());
    }

    @Test
    public void aFailingNotifierDoesNotBreakTheNextChange() throws Exception {
        var attempts = new CopyOnWriteArrayList<String>();
        var failing = new ResourceSubscriptions(INTERVAL, uri -> {
            attempts.add(uri);
            throw new IllegalStateException("transport down");
        });

        failing.changed("uri");
        settle();
        failing.changed("uri");

        assertEquals(2, attempts.size(), "a failed send must not stop later ones");
    }
}
