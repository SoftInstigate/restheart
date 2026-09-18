/*-
 * ========================LICENSE_START=================================
 * restheart-metrics
 * %%
 * Copyright (C) 2023 - 2026 SoftInstigate
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
package org.restheart.metrics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.restheart.exchange.ByteArrayRequest;
import org.restheart.utils.InProcessDispatcher;

import io.undertow.server.HttpServerExchange;

/**
 * One operation of an agent must cost one counted request. {@code call_api} and
 * {@code resources/read} run the action through the whole chain in-process, so the node sees two
 * exchanges for it: the {@code /mcp} request and the one this server dispatched to itself. Timing
 * both would double what a deployment bills on.
 */
public class RequestsMetricsCollectorResolveTest {

    private static RequestsMetricsCollector collector() throws Exception {
        var collector = new RequestsMetricsCollector();
        var config = RequestsMetricsCollector.class.getDeclaredField("config");
        config.setAccessible(true);
        config.set(collector, Map.<String, Object>of("include", java.util.List.of("/*"), "exclude", java.util.List.of()));
        var init = RequestsMetricsCollector.class.getDeclaredMethod("onInit");
        init.setAccessible(true);
        init.invoke(collector);
        return collector;
    }

    private static ByteArrayRequest requestOn(String path, boolean inProcess) {
        var exchange = new HttpServerExchange(null);
        exchange.setRequestPath(path);
        exchange.setRelativePath(path);
        if (inProcess) {
            exchange.putAttachment(InProcessDispatcher.IN_PROCESS, Boolean.TRUE);
        }
        return ByteArrayRequest.init(exchange);
    }

    @Test
    public void aRequestFromAListener_isCounted() throws Exception {
        assertTrue(collector().resolve(requestOn("/todos", false), null),
                "an ordinary request must be timed, or the node counts nothing at all");
    }

    @Test
    public void aRequestTheServerDispatchedToItself_isNotCounted() throws Exception {
        assertFalse(collector().resolve(requestOn("/todos", true), null),
                "the /mcp request that asked for this action is already counted: timing this one doubles it");
    }
}
