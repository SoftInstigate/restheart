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
package org.restheart.ai.mcp.transport;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.restheart.plugins.mcp.McpResource;
import org.restheart.plugins.mcp.McpResource.Transport;
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

    /**
     * What the descriptor's {@code Authorization} header carries in place of a credential.
     *
     * <p>A descriptor is documentation for code the agent writes <em>for the user</em>: the
     * credential is the user's own (an API key, a token their app obtains), never something the
     * MCP server hands out. Executing an action from the agent itself is {@code call_api}'s job,
     * and that runs with the session's identity with no credential in sight. Public because the
     * {@code how_to_call} tool description quotes it.
     */
    public static final String CREDENTIAL_PLACEHOLDER = "<your-credential>";

    private DescriptorRenderer() {
    }

    /**
     * <p>The descriptor never carries a credential: its {@code Authorization} header always holds
     * {@link #CREDENTIAL_PLACEHOLDER}. That is what makes it stable — an agent can ask how to call a
     * resource once and reuse the answer, instead of holding something that silently expires.
     *
     * @param resource   the resource being invoked
     * @param actionName an action known to exist in {@code resource.actions()}
     * @param args       action arguments — values for declared params, plus an optional {@code body} entry
     * @param transportPreference optional; must be one of {@code resource.transportsFor(actionName)} to take effect
     */
    public static Map<String, Object> render(McpResource resource, String actionName, Map<String, Object> args,
                                             String transportPreference) {
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

        // an SSE/WebSocket subscription is still opened over a plain HTTP(S)/WS(S) URL that can
        // carry a query string exactly like any GET — a change-stream's own $var bindings
        // (avars) go through here identically to an aggregation's, so this must not be
        // HTTP-only. Confirmed live: an aggregation's `avars` worked, a change-stream's did not,
        // because this used to build the query string for HTTP only.
        var queryString = buildQueryString(effectiveArgs, consumed);
        var url = baseUrl + path + queryString;

        return switch (transport) {
            case HTTP -> renderHttp(action, url, body);
            case WEBSOCKET, SSE -> renderStreaming(transport, action, url);
        };
    }

    private static Map<String, Object> renderHttp(McpResource.Action action, String url, Object body) {
        var descriptor = new LinkedHashMap<String, Object>();
        descriptor.put("transport", Transport.HTTP.wireName());
        if (action.method() != null) {
            descriptor.put("method", action.method());
        }
        descriptor.put("url", url);

        var headers = new LinkedHashMap<String, Object>();
        headers.put("Authorization", "Bearer " + CREDENTIAL_PLACEHOLDER);
        if (body != null) {
            headers.put("Content-Type", "application/json");
        }
        descriptor.put("headers", headers);

        if (body != null) {
            descriptor.put("body", body);
        }

        // What the action says about itself travels with the request it describes. For a write to
        // a collection with constraints that is how to read a 409 — retry it, or never retry it —
        // which an agent needs at the moment it is about to send, not in the catalogue it read
        // some minutes earlier.
        if (action.description() != null) {
            descriptor.put("notes", action.description());
        }

        return descriptor;
    }

    private static Map<String, Object> renderStreaming(Transport transport, McpResource.Action action, String url) {
        var descriptor = new LinkedHashMap<String, Object>();
        descriptor.put("transport", transport.wireName());
        if (transport == Transport.SSE) {
            descriptor.put("method", "GET");
        }
        descriptor.put("url", url);

        var headers = new LinkedHashMap<String, Object>();
        headers.put("Authorization", "Bearer " + CREDENTIAL_PLACEHOLDER);
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

    /**
     * The path an invocation will address, relative to the resource: the action's template with
     * its placeholders filled in from {@code args}.
     */
    public static String pathFor(McpResource.Action action, Map<String, Object> args) {
        return substitutePathTemplate(action == null ? null : action.pathTemplate(),
                args == null ? Map.of() : args, new HashSet<>());
    }

    /**
     * The query parameters an invocation will carry: every argument except the body and the ones
     * the path template consumes, decoded as Undertow will hold them.
     *
     * <p>This exists so that a check made <em>before</em> a call can see exactly what the call
     * will send. An ACL rule may decide on a query parameter — RESTHeart's own predicate language
     * reads them with {@code %{q,name}} — and a question asked without them gets a different
     * answer from the one the request itself will get.
     */
    public static Map<String, Deque<String>> queryParametersOf(McpResource.Action action, Map<String, Object> args) {
        var effective = args == null ? Map.<String, Object>of() : args;

        var consumed = new HashSet<String>();
        substitutePathTemplate(action == null ? null : action.pathTemplate(), effective, consumed);
        consumed.add("body");

        var out = new LinkedHashMap<String, Deque<String>>();
        effective.forEach((key, value) -> {
            if (consumed.contains(key) || value == null) {
                return;
            }
            out.put(key, new ArrayDeque<>(List.of(queryStringValue(value))));
        });

        return out;
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
