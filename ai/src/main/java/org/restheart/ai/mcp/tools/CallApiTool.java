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

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import org.restheart.ai.mcp.RegisteredMcpAware;
import org.restheart.ai.mcp.transport.DescriptorRenderer;
import org.restheart.plugins.mcp.McpContext;
import org.restheart.plugins.mcp.McpResource;
import org.restheart.plugins.mcp.McpResult;
import org.restheart.security.BaseAccount;
import org.restheart.utils.InProcessDispatcher;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

/**
 * The {@code call_api} tool: executes one action of one resource, server-side, with the identity
 * of the MCP session, and returns the outcome in HTTP terms.
 *
 * <p>Resolution and validation are {@link HowToCallTool#resolve}'s, so a call is the same thing
 * whether it is described or executed. Execution is the owning plugin's
 * {@link org.restheart.plugins.mcp.McpAware#execute} if it claims the action, else the default
 * that {@code execute}'s javadoc describes: the request {@link DescriptorRenderer} would have
 * handed the agent is composed here and run through RESTHeart's whole handler chain by
 * {@link InProcessDispatcher}, as the session's account. Authentication, authorizers,
 * interceptors and the service see an ordinary request.
 *
 * <p>Only HTTP actions are executable: a stream (SSE, WebSocket) has no single response to
 * return, and {@code resources/subscribe} already covers change streams inside MCP.
 */
public final class CallApiTool {
    /** the tool refuses bodies larger than this inside the MCP response; the rest is truncated and flagged */
    public static final int MAX_BODY_BYTES = 1024 * 1024;

    private final CachedResourceLookup lookup;
    private final HowToCallTool resolver;
    private final InProcessDispatcher dispatcher;
    private final McpJsonMapper jsonMapper;

    public CallApiTool(CachedResourceLookup lookup, HowToCallTool resolver, InProcessDispatcher dispatcher, McpJsonMapper jsonMapper) {
        this.lookup = lookup;
        this.resolver = resolver;
        this.dispatcher = dispatcher;
        this.jsonMapper = jsonMapper;
    }

    /**
     * @throws UnknownResourceException  if {@code resourceUri} matches no known (visible) resource
     * @throws UnknownActionException    if {@code actionName} is not declared by the resource
     * @throws ValidationFailedException if {@code args} fails validation, or the action is not executable
     * @throws IOException               if the in-process dispatch fails
     * @throws TimeoutException          if the in-process dispatch does not complete in time
     */
    public McpResult call(BaseAccount principal, String baseUrl, String scope, String resourceUri, String actionName,
                          Map<String, Object> args, Predicate<McpResource> visible) throws IOException, TimeoutException {
        var resolved = resolver.resolve(principal, baseUrl, scope, resourceUri, actionName, args, visible);

        var owner = lookup.findOwner(principal, baseUrl, scope, resourceUri)
                .orElseThrow(() -> new UnknownResourceException(resourceUri));

        // the plugin's own implementation first, for the rare plugin that is the whole semantics
        // of the operation; empty means "use the default", which is nearly always the answer
        var own = owner.instance().execute(context(principal, baseUrl, scope, owner), resourceUri, actionName, args);

        if (own.isPresent()) {
            return own.get();
        }

        var descriptor = DescriptorRenderer.render(resolved.resource(), actionName, args, null);
        var request = InProcessRequest.of(descriptor, jsonMapper);

        var response = dispatcher.dispatchAs(principal, request.method(), request.target(), request.headers(), request.body());

        return new McpResult(response.status(), headersOf(response.headers()), response.body());
    }

    private static McpContext context(BaseAccount principal, String baseUrl, String scope, RegisteredMcpAware owner) {
        return new McpContext(principal, baseUrl, scope, owner.pluginName(), owner.pluginUri(), owner.pluginConfiguration());
    }

    private static Map<String, List<String>> headersOf(HeaderMap headers) {
        var result = new LinkedHashMap<String, List<String>>();

        for (var values : headers) {
            var list = new ArrayList<String>();
            values.forEach(list::add);
            result.put(values.getHeaderName().toString(), list);
        }

        return result;
    }

    /**
     * The in-process request a {@link DescriptorRenderer} HTTP descriptor stands for: method,
     * request target (path and query, as on the request line), headers with the {@code Host} the
     * descriptor's URL names, and the body serialized as JSON.
     */
    record InProcessRequest(HttpString method, String target, HeaderMap headers, byte[] body) {
        static InProcessRequest of(Map<String, Object> descriptor, McpJsonMapper jsonMapper) throws IOException {
            var transport = String.valueOf(descriptor.get("transport"));

            if (!"http".equalsIgnoreCase(transport)) {
                throw new ValidationFailedException(List.of(
                        "action is a " + transport + " stream, which call_api cannot carry: subscribe to the resource with "
                                + "resources/subscribe, or ask how_to_call for a descriptor to use from your own code"));
            }

            var method = descriptor.get("method") == null ? "GET" : String.valueOf(descriptor.get("method"));
            var url = URI.create(String.valueOf(descriptor.get("url")));

            var target = url.getRawPath() == null || url.getRawPath().isEmpty() ? "/" : url.getRawPath();
            if (url.getRawQuery() != null) {
                target += "?" + url.getRawQuery();
            }

            var headers = new HeaderMap();
            // the URL's authority, port included: on a multi-tenant node it is what selects the tenant
            headers.put(Headers.HOST, url.getRawAuthority());
            headers.put(Headers.ACCEPT, "application/json");

            byte[] body = null;

            if (descriptor.get("body") != null) {
                body = jsonMapper.writeValueAsString(descriptor.get("body")).getBytes(StandardCharsets.UTF_8);
                headers.put(Headers.CONTENT_TYPE, "application/json");
            }

            return new InProcessRequest(HttpString.tryFromString(method.toUpperCase()), target, headers, body);
        }
    }

    /** @return the response body if it is JSON, parsed, or the text as is; {@link Optional#empty()} for an empty body */
    public Optional<Object> bodyOf(McpResult result) {
        if (result.body().length == 0) {
            return Optional.empty();
        }

        var text = result.bodyAsString();

        if (result.isJson()) {
            try {
                return Optional.ofNullable(jsonMapper.readValue(text, Object.class));
            } catch (Exception e) {
                // declared JSON but not parseable: hand it over as text rather than lose it
            }
        }

        return Optional.of(text);
    }
}
