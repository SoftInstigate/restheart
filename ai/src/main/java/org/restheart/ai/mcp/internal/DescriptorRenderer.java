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
package org.restheart.ai.mcp.internal;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.restheart.ai.mcp.api.McpResource;
import org.restheart.ai.mcp.api.McpResource.Transport;
import org.restheart.utils.BsonUtils;

/**
 * Assembles the transport-specific request descriptor {@code how_to_call} returns —
 * <em>composes</em> the request, never executes it (see restheart#615 design principles).
 * Path-template placeholders are substituted from {@code args}; whatever is left over
 * becomes a query string (HTTP) except the conventional {@code body} key, which becomes
 * the descriptor's {@code body} field.
 */
public final class DescriptorRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)\\}");
    private static final String TOKEN_PLACEHOLDER = "<token>";

    private DescriptorRenderer() {
    }

    /**
     * @param resource   the resource being invoked
     * @param actionName an action known to exist in {@code resource.actions()}
     * @param args       action arguments — values for declared params, plus an optional {@code body} entry
     * @param transportPreference optional; must be one of {@code resource.transportsFor(actionName)} to take effect
     * @param token      optional bearer token; a placeholder is embedded when omitted
     */
    public static Map<String, Object> render(McpResource resource, String actionName, Map<String, Object> args,
            String transportPreference, String token) {
        var action = resource.actions().get(actionName);
        if (action == null) {
            throw new IllegalArgumentException("unknown action '" + actionName + "' for resource " + resource.uri());
        }

        var transport = pickTransport(resource.transportsFor(actionName), transportPreference);
        var effectiveArgs = args == null ? Map.<String, Object>of() : args;

        var consumed = new HashSet<String>();
        var path = substitutePathTemplate(action.pathTemplate(), effectiveArgs, consumed);
        var baseUrl = transport == Transport.WEBSOCKET ? toWebSocketUrl(resource.uri()) : resource.uri();

        var body = effectiveArgs.get("body");
        consumed.add("body");

        var queryString = transport == Transport.HTTP ? buildQueryString(effectiveArgs, consumed) : "";
        var url = baseUrl + path + queryString;

        return switch (transport) {
            case HTTP -> renderHttp(action, url, body, token);
            case WEBSOCKET, SSE -> renderStreaming(transport, action, url, token);
        };
    }

    private static Map<String, Object> renderHttp(McpResource.Action action, String url, Object body, String token) {
        var descriptor = new LinkedHashMap<String, Object>();
        descriptor.put("transport", Transport.HTTP.wireName());
        if (action.method() != null) {
            descriptor.put("method", action.method());
        }
        descriptor.put("url", url);

        var headers = new LinkedHashMap<String, Object>();
        headers.put("Authorization", "Bearer " + (token != null ? token : TOKEN_PLACEHOLDER));
        if (body != null) {
            headers.put("Content-Type", "application/json");
        }
        descriptor.put("headers", headers);

        if (body != null) {
            descriptor.put("body", body);
        }

        return descriptor;
    }

    private static Map<String, Object> renderStreaming(Transport transport, McpResource.Action action, String url, String token) {
        var descriptor = new LinkedHashMap<String, Object>();
        descriptor.put("transport", transport.wireName());
        if (transport == Transport.SSE) {
            descriptor.put("method", "GET");
        }
        descriptor.put("url", url);

        var headers = new LinkedHashMap<String, Object>();
        headers.put("Authorization", "Bearer " + (token != null ? token : TOKEN_PLACEHOLDER));
        if (transport == Transport.SSE) {
            headers.put("Accept", transport.mediaType());
        }
        descriptor.put("headers", headers);

        if (action.description() != null) {
            descriptor.put("message_format", Map.of("description", action.description()));
        }

        return descriptor;
    }

    private static Transport pickTransport(List<Transport> candidates, String preference) {
        if (candidates.isEmpty()) {
            return Transport.HTTP;
        }
        if (preference != null) {
            for (var candidate : candidates) {
                if (candidate.wireName().equalsIgnoreCase(preference)) {
                    return candidate;
                }
            }
        }
        return candidates.get(0);
    }

    private static String substitutePathTemplate(String pathTemplate, Map<String, Object> args, Set<String> consumed) {
        if (pathTemplate == null) {
            return "";
        }
        var matcher = PLACEHOLDER.matcher(pathTemplate);
        var result = new StringBuilder();
        while (matcher.find()) {
            var name = matcher.group(1);
            consumed.add(name);
            var value = args.get(name);
            matcher.appendReplacement(result, Matcher.quoteReplacement(urlEncode(String.valueOf(value))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String buildQueryString(Map<String, Object> args, Set<String> consumed) {
        var pairs = new ArrayList<String>();
        args.forEach((key, value) -> {
            if (consumed.contains(key) || value == null) {
                return;
            }
            pairs.add(urlEncode(key) + "=" + urlEncode(queryStringValue(value)));
        });
        return pairs.isEmpty() ? "" : "?" + String.join("&", pairs);
    }

    /** Compound values (objects/arrays) are JSON-encoded; scalars are used as-is. */
    private static String queryStringValue(Object value) {
        if (value instanceof Map || value instanceof List) {
            var wrapped = BsonUtils.toBsonDocument(Map.of("v", value));
            return BsonUtils.toJson(wrapped.get("v"));
        }
        return String.valueOf(value);
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String toWebSocketUrl(String httpUrl) {
        if (httpUrl.startsWith("https://")) {
            return "wss://" + httpUrl.substring("https://".length());
        }
        if (httpUrl.startsWith("http://")) {
            return "ws://" + httpUrl.substring("http://".length());
        }
        return httpUrl;
    }
}
