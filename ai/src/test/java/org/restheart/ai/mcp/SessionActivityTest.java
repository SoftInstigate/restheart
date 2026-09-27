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
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class SessionActivityTest {
    private static final Duration TIMEOUT = Duration.ofMinutes(30);

    private final AtomicLong now = new AtomicLong();
    private final SessionActivity activity = new SessionActivity(now::get);

    private void advance(Duration d) {
        now.addAndGet(d.toNanos());
    }

    @Test
    void aSessionUnusedForLongerThanTheTimeoutIsIdle() {
        activity.touch("s1");
        advance(TIMEOUT.plusSeconds(1));
        assertEquals(List.of("s1"), activity.idle(TIMEOUT));
    }

    @Test
    void aRequestResetsTheIdleTime() {
        activity.touch("s1");
        advance(TIMEOUT.minusSeconds(1));
        activity.touch("s1");
        advance(Duration.ofSeconds(2));
        assertTrue(activity.idle(TIMEOUT).isEmpty());
    }

    @Test
    void aSessionWithAnOpenStreamIsNeverIdle() {
        activity.streamOpened("s1");
        advance(TIMEOUT.multipliedBy(10));
        assertTrue(activity.idle(TIMEOUT).isEmpty());
    }

    @Test
    void theIdleTimeCountsFromWhenTheLastStreamClosed() {
        activity.streamOpened("s1");
        advance(TIMEOUT.multipliedBy(10));
        activity.streamClosed("s1");
        advance(TIMEOUT.minusSeconds(1));
        assertTrue(activity.idle(TIMEOUT).isEmpty());
        advance(Duration.ofSeconds(2));
        assertEquals(List.of("s1"), activity.idle(TIMEOUT));
    }

    @Test
    void aForgottenSessionIsNotReported() {
        activity.touch("s1");
        activity.forget("s1");
        advance(TIMEOUT.plusSeconds(1));
        assertTrue(activity.idle(TIMEOUT).isEmpty());
    }
}
