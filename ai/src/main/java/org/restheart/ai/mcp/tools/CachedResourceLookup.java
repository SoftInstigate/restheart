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
package org.restheart.ai.mcp.tools;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.restheart.ai.mcp.McpAwareRegistry;
import org.restheart.plugins.mcp.McpResource;
import org.restheart.security.BaseAccount;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.Scheduler;
import com.github.benmanes.caffeine.cache.Ticker;

/**
 * Caches {@link ResourceLookup#all}, keyed by {@code baseUrl}, for a fixed TTL — replacing any
 * source-side invalidation (a MongoDB write watch, a change-stream) with a simpler, uniform
 * contract: catalog data is at most {@code ttl} stale, for any {@code McpAware} implementation,
 * with no implementation-specific wiring required.
 *
 * <p>On expiry, {@code onExpire} runs once per evicted entry — {@code McpService} wires this to
 * {@code UndertowStreamableServerTransportProvider.notifyClients("notifications/tools/list_changed",
 * ...)}, so already-connected agents are told to refetch. A {@link Scheduler} drives expiry
 * proactively (not just lazily on the next cache access), so the notification still fires within
 * {@code ttl} even with zero traffic in the meantime — otherwise an idle deployment would never
 * evict, and "stale for at most {@code ttl}" wouldn't actually hold.
 *
 * <p>Not keyed by principal: none of the current {@code McpAware} implementations
 * (({@code PingService}, {@code MongoMcpAwareImpl}, {@code GraphqlMcpAwareImpl})) vary their
 * output by the calling principal — see #616's amended "ACL filtering" design — so caching per
 * {@code baseUrl} alone is correct today. A future principal-dependent implementation would need
 * this revisited.
 */
public final class CachedResourceLookup {

    private final McpAwareRegistry registry;
    private final Cache<String, List<McpResource>> cache;

    public CachedResourceLookup(McpAwareRegistry registry, Duration ttl, Runnable onExpire) {
        this(registry, ttl, onExpire, Ticker.systemTicker(), Scheduler.systemScheduler());
    }

    /** Test seam: a controllable {@link Ticker} and no real {@link Scheduler}, so tests advance time deterministically without waiting. */
    CachedResourceLookup(McpAwareRegistry registry, Duration ttl, Runnable onExpire, Ticker ticker, Scheduler scheduler) {
        this.registry = registry;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .ticker(ticker)
                .scheduler(scheduler)
                // synchronous: keeps behavior deterministic (and testable without waiting on a
                // background thread) — onExpire's own work (a fire-and-forget notification) is
                // cheap enough not to need offloading
                .executor(Runnable::run)
                .removalListener((String key, List<McpResource> value, RemovalCause cause) -> {
                    if (cause == RemovalCause.EXPIRED) {
                        onExpire.run();
                    }
                })
                .build();
    }

    List<McpResource> all(BaseAccount principal, String baseUrl) {
        return cache.get(baseUrl, k -> ResourceLookup.all(registry, principal, baseUrl));
    }

    Optional<McpResource> find(BaseAccount principal, String baseUrl, String resourceUri) {
        return all(principal, baseUrl).stream().filter(r -> r.uri().equals(resourceUri)).findFirst();
    }
}
