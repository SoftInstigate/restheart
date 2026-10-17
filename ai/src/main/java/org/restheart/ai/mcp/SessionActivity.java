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
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * When each MCP session was last used, and whether a stream is open on it, so that a session a
 * client left behind can be told apart from one that is only quiet.
 *
 * <p>A Streamable HTTP session ends when the client sends {@code DELETE}. Most clients that go
 * away do not: a crash, a closed window or a dropped network leave the session on the server,
 * and every catalog expiry then tries to notify it again. So a session with no request and no
 * open stream for longer than the idle timeout is considered gone. A client that comes back
 * after its session ended is answered {@code 404} and re-initializes, as the Streamable HTTP
 * spec prescribes.
 *
 * <p>A session with an open stream is never idle, however long ago its last request was: a
 * client listening on {@code GET} is connected by definition.
 */
final class SessionActivity {
    private record Entry(long lastSeenNanos, AtomicInteger openStreams) {
    }

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final LongSupplier nanoClock;

    SessionActivity(LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
    }

    SessionActivity() {
        this(System::nanoTime);
    }

    /** The session was used now: created, or addressed by a request. */
    void touch(String sessionId) {
        var now = nanoClock.getAsLong();
        entries.compute(sessionId, (id, e) -> new Entry(now, e == null ? new AtomicInteger() : e.openStreams()));
    }

    /** A stream opened on the session; it stays active until {@link #streamClosed}. */
    void streamOpened(String sessionId) {
        touch(sessionId);
        entries.get(sessionId).openStreams().incrementAndGet();
    }

    /** A stream closed; the idle time counts from now. */
    void streamClosed(String sessionId) {
        var e = entries.get(sessionId);
        if (e != null) {
            e.openStreams().decrementAndGet();
            touch(sessionId);
        }
    }

    /** The session ended, by {@code DELETE} or by expiry. */
    void forget(String sessionId) {
        entries.remove(sessionId);
    }

    /** The sessions with no open stream and no use for longer than {@code timeout}. */
    List<String> idle(Duration timeout) {
        var now = nanoClock.getAsLong();
        var limit = timeout.toNanos();
        return entries.entrySet().stream()
                .filter(e -> e.getValue().openStreams().get() <= 0 && now - e.getValue().lastSeenNanos() > limit)
                .map(java.util.Map.Entry::getKey)
                .toList();
    }
}
