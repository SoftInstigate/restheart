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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * What {@link McpAware#execute(McpContext, String, String, Map)} returns: the outcome of one
 * action of one resource, in HTTP terms, because that is what an agent gets back from
 * {@code call_api} and what {@code resources/read} maps onto its own result.
 *
 * <p>A {@code 4xx}/{@code 5xx} is a result, not a failure to produce one: the agent must see the
 * status and whatever body came with it, exactly as a REST client would.
 *
 * @param status  the HTTP status code
 * @param headers the response headers, one entry per header name, values in order
 * @param body    the response body, possibly empty, never {@code null}
 */
public record McpResult(int status, Map<String, List<String>> headers, byte[] body) {
    public McpResult {
        if (headers == null) {
            headers = Map.of();
        }
        if (body == null) {
            body = new byte[0];
        }
    }

    public String bodyAsString() {
        return new String(body, StandardCharsets.UTF_8);
    }

    /** @return the first value of the header, or {@code null}; the name is matched ignoring case */
    public String header(String name) {
        for (var e : headers.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name) && !e.getValue().isEmpty()) {
                return e.getValue().get(0);
            }
        }
        return null;
    }

    /** @return whether the body is JSON, going by {@code Content-Type} */
    public boolean isJson() {
        var ct = header("Content-Type");
        return ct != null && (ct.startsWith("application/json") || ct.contains("+json"));
    }
}
