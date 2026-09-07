/*-
 * ========================LICENSE_START=================================
 * restheart-commons
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
package org.restheart.plugins.mcp;

import java.util.LinkedHashSet;
import java.util.Set;

import org.restheart.plugins.BsonService;
import org.restheart.plugins.ByteArrayService;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.SseService;
import org.restheart.plugins.StringService;
import org.restheart.plugins.mcp.McpResource.Transport;

/**
 * Derives the default {@code transports} a resource supports from the RESTHeart
 * {@code Service} marker interfaces its owning plugin implements — used by the
 * default, config-driven {@code describeMcp()} path. A custom {@code McpAware}
 * implementation that knows better (e.g. {@code MongoService} declaring
 * {@code [websocket, sse]} for a change-stream resource) is not required to use this.
 *
 * <p>There is no {@code WebSocketService} marker interface in RESTHeart's plugin SPI
 * as of this writing — change-stream/websocket endpoints are wired directly at the
 * transport layer rather than through the {@code Service} SPI — so this derives
 * only {@link Transport#HTTP} (from {@link JsonService}, {@link BsonService},
 * {@link ByteArrayService}, {@link StringService}) and {@link Transport#SSE}
 * (from {@link SseService}). A resource needing {@link Transport#WEBSOCKET} must
 * declare it explicitly via a custom {@code describeMcp()}.
 */
public final class TransportDeriver {

    private TransportDeriver() {
    }

    public static Set<Transport> derive(Object plugin) {
        var transports = new LinkedHashSet<Transport>();

        if (plugin instanceof JsonService
                || plugin instanceof BsonService
                || plugin instanceof ByteArrayService
                || plugin instanceof StringService) {
            transports.add(Transport.HTTP);
        }

        if (plugin instanceof SseService) {
            transports.add(Transport.SSE);
        }

        return transports;
    }
}
