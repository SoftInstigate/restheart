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
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import org.restheart.ai.mcp.McpAwareRegistry;
import org.restheart.ai.mcp.RegisteredMcpAware;
import org.restheart.plugins.mcp.McpResource;
import org.restheart.plugins.mcp.McpResourceTemplate;
import org.restheart.security.BaseAccount;
import org.restheart.utils.ThreadsUtils;

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
    private final Cache<CatalogKey, ResourceLookup.Catalog> cache;

    /**
     * What one cached catalogue belongs to.
     *
     * <p>The base URL alone was the key until scopes existed, and it is not enough: on an instance
     * reached through a single hostname every scope shares one base URL, so one scope's catalogue
     * would be served to all of them — the very leak partitioning exists to close, reappearing
     * inside the cache where no test on a per-hostname deployment would ever see it.
     */
    public record CatalogKey(String baseUrl, String scope) {
    }

    public CachedResourceLookup(McpAwareRegistry registry, Duration ttl, Consumer<CatalogKey> onExpire) {
        // Dispatched to the framework's shared virtual-threads executor (ThreadsUtils), not run
        // on the shared, JVM-wide CompletableFuture/Caffeine delay-scheduler thread (named
        // "ForkJoinPool.commonPool-delayScheduler" — confirmed by decompiling Caffeine's
        // SystemScheduler, which is exactly what schedules this cache's proactive expiry): with
        // .executor(Runnable::run) below, onExpire otherwise runs synchronously ON whichever
        // thread the removal fires on, and for a scheduled (not lazily-triggered) expiry that IS
        // that single JVM-wide thread. onExpire's own work now includes describeMcp()/
        // describeTemplates() on every McpAware plugin plus notifyClients()'s per-session
        // sendNotification().block() (real I/O), so it's no longer cheap enough to risk stalling
        // every other JVM-wide user of CompletableFuture.delayedExecutor behind it.
        this(registry, ttl, onExpire, Ticker.systemTicker(), Scheduler.systemScheduler(), ThreadsUtils.virtualThreadsExecutor());
    }

    /** Test seam: a controllable {@link Ticker} and no real {@link Scheduler}, so tests advance time deterministically without waiting. */
    CachedResourceLookup(McpAwareRegistry registry, Duration ttl, Consumer<CatalogKey> onExpire, Ticker ticker, Scheduler scheduler) {
        // Runs onExpire synchronously (not on a virtual thread) so tests can assert its effect
        // right after triggering expiry, with no race to wait out.
        this(registry, ttl, onExpire, ticker, scheduler, Runnable::run);
    }

    private CachedResourceLookup(McpAwareRegistry registry, Duration ttl, Consumer<CatalogKey> onExpire, Ticker ticker, Scheduler scheduler, Executor onExpireExecutor) {
        this.registry = registry;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .ticker(ticker)
                .scheduler(scheduler)
                // synchronous: keeps cache maintenance itself deterministic (and testable
                // without waiting on a background thread) — onExpire's own dispatch is handled
                // separately via onExpireExecutor, see the public constructor's comment
                .executor(Runnable::run)
                // The key is handed on, not dropped: it names the scope whose catalogue expired,
                // and only that scope's clients have anything to be told about.
                .removalListener((CatalogKey key, ResourceLookup.Catalog value, RemovalCause cause) -> {
                    if (cause == RemovalCause.EXPIRED) {
                        onExpireExecutor.execute(() -> onExpire.accept(key));
                    }
                })
                .build();
    }

    /** Public: also called by {@code McpService} (a different package) to sync the MCP SDK's resource registry — see #617. */
    public List<McpResource> all(BaseAccount principal, String baseUrl, String scope) {
        return catalog(principal, baseUrl, scope).resources();
    }

    /** Public: also called by {@code McpService} (a different package) for resource-template read dispatch — see #617. */
    public Optional<McpResource> find(BaseAccount principal, String baseUrl, String scope, String resourceUri) {
        return all(principal, baseUrl, scope).stream().filter(r -> r.uri().equals(resourceUri)).findFirst();
    }

    /**
     * Public: also called by {@code McpService} to resolve which plugin owns a resource, for
     * documents-mode {@code resources/read} dispatch (its {@code readResource(...)}) — see #617.
     */
    public Optional<RegisteredMcpAware> findOwner(BaseAccount principal, String baseUrl, String scope, String resourceUri) {
        return Optional.ofNullable(catalog(principal, baseUrl, scope).owners().get(resourceUri));
    }

    private ResourceLookup.Catalog catalog(BaseAccount principal, String baseUrl, String scope) {
        return cache.get(new CatalogKey(baseUrl, scope), k -> ResourceLookup.catalog(registry, principal, k.baseUrl(), k.scope()));
    }

    /**
     * Public: also called by {@code McpService} to sync the MCP SDK's resource-template registry
     * — see #617. Not cached: unlike {@link #all}, only computed when the resource registry
     * itself is (re)synced (boot, and on every catalog TTL expiry), never per MCP request.
     */
    public List<McpResourceTemplate> templates(String baseUrl, String scope) {
        return ResourceLookup.templates(registry, baseUrl, scope);
    }
}
