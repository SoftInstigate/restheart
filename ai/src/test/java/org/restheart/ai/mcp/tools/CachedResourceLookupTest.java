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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.restheart.ai.mcp.McpAwareRegistry;
import org.restheart.ai.mcp.RegisteredMcpAware;
import org.restheart.plugins.mcp.McpAware;
import org.restheart.plugins.mcp.McpContext;
import org.restheart.plugins.mcp.McpResource;

import com.github.benmanes.caffeine.cache.Scheduler;
import com.github.benmanes.caffeine.cache.Ticker;

public class CachedResourceLookupTest {

    private static final class MutableTicker implements Ticker {
        private long nanos = 0;

        void advance(Duration d) {
            nanos += d.toNanos();
        }

        @Override
        public long read() {
            return nanos;
        }
    }

    private static McpAwareRegistry countingRegistry(AtomicInteger callCount, McpResource resource) {
        McpAware aware = new McpAware() {
            @Override
            public List<McpResource> describeMcp(McpContext ctx) {
                callCount.incrementAndGet();
                return List.of(resource);
            }
        };
        return McpAwareRegistry.of(List.of(new RegisteredMcpAware(aware, "p1", "/x", Map.of())));
    }

    @Test
    public void withinTtl_secondCallDoesNotRecompute() {
        var callCount = new AtomicInteger();
        var registry = countingRegistry(callCount, McpResource.builder().uri("https://host/a").build());
        var lookup = new CachedResourceLookup(registry, Duration.ofMinutes(5), () -> {}, new MutableTicker(), Scheduler.disabledScheduler());

        lookup.all(null, "https://host");
        lookup.all(null, "https://host");

        assertEquals(1, callCount.get());
    }

    @Test
    public void afterTtlExpires_recomputesAndFiresOnExpire() {
        var callCount = new AtomicInteger();
        var registry = countingRegistry(callCount, McpResource.builder().uri("https://host/a").build());
        var ticker = new MutableTicker();
        var expired = new AtomicBoolean(false);
        var lookup = new CachedResourceLookup(registry, Duration.ofMinutes(5), () -> expired.set(true), ticker, Scheduler.disabledScheduler());

        lookup.all(null, "https://host");
        ticker.advance(Duration.ofMinutes(6));
        lookup.all(null, "https://host");

        assertEquals(2, callCount.get());
        assertTrue(expired.get());
    }

    @Test
    public void beforeTtlExpires_onExpireNeverFires() {
        var callCount = new AtomicInteger();
        var registry = countingRegistry(callCount, McpResource.builder().uri("https://host/a").build());
        var ticker = new MutableTicker();
        var expired = new AtomicBoolean(false);
        var lookup = new CachedResourceLookup(registry, Duration.ofMinutes(5), () -> expired.set(true), ticker, Scheduler.disabledScheduler());

        lookup.all(null, "https://host");
        ticker.advance(Duration.ofMinutes(1));
        lookup.all(null, "https://host");

        assertEquals(1, callCount.get());
        assertTrue(!expired.get());
    }

    @Test
    public void differentBaseUrls_cachedIndependently() {
        var callCount = new AtomicInteger();
        var registry = countingRegistry(callCount, McpResource.builder().uri("https://host/a").build());
        var lookup = new CachedResourceLookup(registry, Duration.ofMinutes(5), () -> {}, new MutableTicker(), Scheduler.disabledScheduler());

        lookup.all(null, "https://host1");
        lookup.all(null, "https://host2");

        assertEquals(2, callCount.get());
    }

    @Test
    public void find_locatesResourceWithinCachedCatalog() {
        var resource = McpResource.builder().uri("https://host/a").build();
        var registry = McpAwareRegistry.of(List.of(new RegisteredMcpAware(
                new McpAware() {
                    @Override
                    public List<McpResource> describeMcp(McpContext ctx) {
                        return List.of(resource);
                    }
                }, "p1", "/x", Map.of())));
        var lookup = new CachedResourceLookup(registry, Duration.ofMinutes(5), () -> {});

        assertEquals("https://host/a", lookup.find(null, "https://host", "https://host/a").orElseThrow().uri());
        assertTrue(lookup.find(null, "https://host", "https://host/missing").isEmpty());
    }
}
