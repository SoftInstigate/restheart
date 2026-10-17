/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
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
package org.restheart.mongodb.mcp;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.BiFunction;

import org.bson.BsonDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoCollection;

/**
 * One change stream per watched collection, however many MCP resources depend on it (#617).
 *
 * <p>A collection backs several resources — its documents, one of its documents, its {@code _size}
 * — and several clients may subscribe to each. All of that is one question to MongoDB: did this
 * collection change. So the stream is keyed by collection and the listeners hang off it; the last
 * listener to leave closes it.
 *
 * <p>Deliberately not built on {@code ChangeStreamWorker}. That serves a different consumer: it
 * carries interpolated pipeline stages, a resume token, a notify-when evaluator and WebSocket/SSE
 * sinks, because a change-stream client wants the events. An MCP subscriber wants none of it —
 * {@code notifications/resources/updated} has no payload, it only says "re-read". Threading a
 * third sink type through that class would complicate a working component to share a stream that,
 * in practice, would never be shared: a change-stream client almost always has its own stages, so
 * a different key.
 */
public final class CollectionWatchers {

    private static final Logger LOGGER = LoggerFactory.getLogger(CollectionWatchers.class);

    private record Key(String db, String collection) {
    }

    private static final class Watch {
        private final Set<Runnable> listeners = new CopyOnWriteArraySet<>();
        private volatile Thread thread;
        private volatile boolean stopped;
    }

    private final Map<Key, Watch> watches = new ConcurrentHashMap<>();

    /** Supplies the collection to watch — kept as a function so this class holds no MongoClient of its own. */
    private final BiFunction<String, String, MongoCollection<BsonDocument>> collections;

    public CollectionWatchers(BiFunction<String, String, MongoCollection<BsonDocument>> collections) {
        this.collections = collections;
    }

    /**
     * Registers {@code onChange} against a collection, opening the stream if this is the first
     * listener on it.
     *
     * @return a handle that removes this listener, and closes the stream when it was the last one
     */
    public synchronized AutoCloseable watch(String db, String collection, Runnable onChange) {
        var key = new Key(db, collection);

        // Same monitor as release(), and for one reason: release() marks a Watch stopped and then
        // removes it from the map, and between those two steps the entry is still there. Registering
        // outside the lock could therefore attach this listener to a Watch about to be torn down —
        // start() would then decline to open a cursor, because stopped is already true, and the
        // subscriber would wait forever for notifications that nothing is producing. Nothing would
        // look wrong: silence is what a quiet collection looks like too.
        //
        // A stopped Watch is never reused for the same reason: its thread is on its way out.
        var watch = watches.compute(key, (k, existing) -> existing != null && !existing.stopped ? existing : new Watch());

        watch.listeners.add(onChange);

        start(key, watch);

        return () -> release(key, onChange);
    }

    private synchronized void start(Key key, Watch watch) {
        if (watch.thread != null || watch.stopped) {
            return;
        }

        // a virtual thread: this one blocks on the cursor for its whole life, which is exactly
        // what virtual threads are for and what the rest of RESTHeart's change streams already do
        watch.thread = Thread.ofVirtual()
                .name("mcp-watch-" + key.db() + "." + key.collection())
                .start(() -> run(key, watch));
    }

    /** First retry delay; doubles up to {@link #MAX_BACKOFF} while the stream cannot be opened. */
    private static final Duration INITIAL_BACKOFF = Duration.ofSeconds(1);

    private static final Duration MAX_BACKOFF = Duration.ofSeconds(30);

    /**
     * Reads the stream, reopening it for as long as somebody is subscribed.
     *
     * <p>A change stream ends for reasons that have nothing to do with the subscriber: the
     * collection is dropped and recreated, a primary steps down, a connection drops. Letting the
     * watch die then would leave every subscriber silently unnotified for the rest of the
     * server's life — the failure would be invisible, because nothing is supposed to arrive when
     * nothing changes. So the watch outlives its stream, and only the last subscriber leaving
     * ends it.
     */
    private void run(Key key, Watch watch) {
        var backoff = INITIAL_BACKOFF;

        try {
            while (!watch.stopped) {
                try (var cursor = collections.apply(key.db(), key.collection()).watch().cursor()) {
                    backoff = INITIAL_BACKOFF;

                    while (!watch.stopped && cursor.hasNext()) {
                        cursor.next();
                        notifyListeners(watch);
                    }
                } catch (Exception e) {
                    if (watch.stopped || Thread.currentThread().isInterrupted()) {
                        return;
                    }

                    LOGGER.debug("MCP change stream on {}.{} failed, retrying in {}",
                            key.db(), key.collection(), backoff, e);

                    Thread.sleep(backoff);
                    backoff = backoff.multipliedBy(2).compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff.multipliedBy(2);
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } finally {
            watches.remove(key, watch);
        }
    }

    /** A listener that throws must not take the stream down with it, nor stop the others from hearing. */
    private static void notifyListeners(Watch watch) {
        for (var listener : watch.listeners) {
            try {
                listener.run();
            } catch (Exception e) {
                LOGGER.warn("an MCP resource-change listener failed", e);
            }
        }
    }

    private synchronized void release(Key key, Runnable onChange) {
        var watch = watches.get(key);
        if (watch == null) {
            return;
        }

        watch.listeners.remove(onChange);

        if (watch.listeners.isEmpty()) {
            watch.stopped = true;
            watches.remove(key, watch);

            var thread = watch.thread;
            if (thread != null) {
                // the cursor blocks in hasNext(); interrupting is what ends it
                thread.interrupt();
            }
        }
    }

    /** How many streams are open — for tests and for anyone wondering what a deployment is paying. */
    public int openWatches() {
        return watches.size();
    }
}
