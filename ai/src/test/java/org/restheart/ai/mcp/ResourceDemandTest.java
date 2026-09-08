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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Decides when a change stream is opened and, more importantly, when it stops. Getting the "last
 * subscriber left" answer wrong in one direction leaks a stream for a client that is gone; in the
 * other it stops notifying a client that is still there.
 */
public class ResourceDemandTest {

    private final ResourceDemand demand = new ResourceDemand();

    @Test
    public void theFirstSubscriberOpensTheWatch() {
        assertTrue(demand.subscribed("uri", "s1"));
    }

    @Test
    public void aSecondSubscriberDoesNotOpenAnother() {
        demand.subscribed("uri", "s1");

        assertFalse(demand.subscribed("uri", "s2"), "one watch serves both");
    }

    @Test
    public void theWatchSurvivesUntilTheLastSubscriberLeaves() {
        demand.subscribed("uri", "s1");
        demand.subscribed("uri", "s2");

        assertFalse(demand.unsubscribed("uri", "s1"), "s2 is still listening");
        assertTrue(demand.unsubscribed("uri", "s2"));
    }

    @Test
    public void theSameSessionSubscribingTwiceStillCountsOnce() {
        demand.subscribed("uri", "s1");
        demand.subscribed("uri", "s1");

        assertTrue(demand.unsubscribed("uri", "s1"), "one session, one subscription");
    }

    @Test
    public void aSessionEndingReleasesOnlyWhatNobodyElseWants() {
        demand.subscribed("a", "s1");
        demand.subscribed("b", "s1");
        demand.subscribed("b", "s2");

        assertEquals(List.of("a"), demand.sessionEnded("s1"), "b still has s2");
    }

    @Test
    public void aSessionEndingTwiceReleasesNothingTheSecondTime() {
        demand.subscribed("a", "s1");
        demand.sessionEnded("s1");

        assertEquals(List.of(), demand.sessionEnded("s1"));
    }

    @Test
    public void unsubscribingSomethingNeverSubscribedIsNotALastSubscriber() {
        // otherwise it would close a watch that another session opened
        assertFalse(demand.unsubscribed("uri", "s1"));
    }

    @Test
    public void shutdownSeesEverythingStillHeld() {
        demand.subscribed("a", "s1");
        demand.subscribed("b", "s2");

        assertTrue(demand.all().containsAll(List.of("a", "b")));
        assertEquals(2, demand.subscribedUris());
    }
}
