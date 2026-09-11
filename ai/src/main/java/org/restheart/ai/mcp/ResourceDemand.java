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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is still interested in each resource, so a watch can be closed when nobody is (#617).
 *
 * <p>The SDK keeps its own subscription map and is the authority on <em>delivery</em>; this one
 * exists for a different question — whether to keep paying for a change stream. It has to be
 * separate because the SDK's map is private and it drops a session's subscriptions silently, which
 * would leave a watch running for a client that is long gone.
 *
 * <p>Everything that ends a subscription is visible to the transport: an explicit
 * {@code resources/unsubscribe}, the {@code DELETE} that closes a session, and shutdown. What is
 * not visible is a client that simply vanishes without a {@code DELETE}; its watch survives until
 * the resource leaves the catalog or the server stops.
 */
public class ResourceDemand {

    private final Map<String, Set<String>> subscribersByUri = new ConcurrentHashMap<>();

    /** @return true if this is the first subscriber to {@code uri}, i.e. the watch has to be opened */
    public boolean subscribed(String uri, String sessionId) {
        var added = new boolean[1];

        subscribersByUri.compute(uri, (u, sessions) -> {
            if (sessions == null) {
                added[0] = true;
                sessions = ConcurrentHashMap.newKeySet();
            }
            sessions.add(sessionId);
            return sessions;
        });

        return added[0];
    }

    /** @return true if that was the last subscriber to {@code uri}, i.e. the watch can be closed */
    public boolean unsubscribed(String uri, String sessionId) {
        var emptied = new boolean[1];

        subscribersByUri.computeIfPresent(uri, (u, sessions) -> {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                emptied[0] = true;
                return null;
            }
            return sessions;
        });

        return emptied[0];
    }

    /** @return the URIs left with no subscribers at all once this session is gone */
    public List<String> sessionEnded(String sessionId) {
        var orphaned = new ArrayList<String>();

        subscribersByUri.forEach((uri, sessions) -> {
            if (sessions.contains(sessionId) && unsubscribed(uri, sessionId)) {
                orphaned.add(uri);
            }
        });

        return orphaned;
    }

    /** @return every URI still subscribed to — what shutdown has to release */
    /**
     * Whether anybody is subscribed to {@code uri} right now.
     *
     * <p>Asked when a watch is about to be released: the decision to release was taken when the
     * last subscriber left, and by the time it is carried out a new one may have arrived.
     */
    public boolean hasSubscribers(String uri) {
        var sessions = subscribersByUri.get(uri);
        return sessions != null && !sessions.isEmpty();
    }

    public List<String> all() {
        return List.copyOf(subscribersByUri.keySet());
    }

    public int subscribedUris() {
        return subscribersByUri.size();
    }
}
