/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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
package org.restheart.test.integration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.bson.BsonDocument;

/**
 * Minimal JSON-RPC client for RESTHeart's {@code /mcp} endpoint (Streamable HTTP transport, MCP
 * spec 2025-03-26), used by the MCP integration tests ({@code McpMongo*IT}, {@code McpGraphqlAppIT}).
 *
 * <p>Protocol shape confirmed against a live RESTHeart instance rather than assumed from the
 * spec alone:
 * <ul>
 *   <li>{@code initialize} responds with plain {@code application/json} (not SSE) and carries
 *       the session id in the {@code Mcp-Session-Id} response header.</li>
 *   <li>{@code tools/call} responds as a single {@code text/event-stream} frame
 *       ({@code event: message\ndata: <json-rpc envelope>}), not plain JSON, then the connection
 *       closes — so a single blocking string read is enough, no persistent-stream handling
 *       needed (contrast with {@link ChangeStreamSseIT}'s genuinely long-lived SSE streams).</li>
 *   <li>The actual tool result is double-encoded: {@code result.content[0].text} is itself a
 *       JSON string that must be parsed again to get the real object
 *       ({@code list_apis}'s catalog, {@code how_to_call}'s descriptor, ...).</li>
 * </ul>
 */
final class McpTestClient {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String mcpUrl;
    private final String basicAuth;
    private final AtomicInteger nextId = new AtomicInteger(1);
    private String sessionId;

    McpTestClient(String baseUrl, String basicAuth) {
        this.mcpUrl = baseUrl + "/mcp";
        this.basicAuth = basicAuth;
    }

    /** Performs the {@code initialize}/{@code notifications/initialized} handshake; must be called before {@link #callTool}. */
    void initialize() throws Exception {
        var initResponse = send("""
                {"jsonrpc":"2.0","id":%d,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"it-test","version":"1.0"}}}
                """.formatted(nextId.getAndIncrement()), null);

        sessionId = initResponse.headers().firstValue("Mcp-Session-Id")
                .orElseThrow(() -> new IllegalStateException("initialize response carried no Mcp-Session-Id header: " + initResponse.body()));

        send("""
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                """, sessionId);
    }

    /**
     * @param toolName     {@code "list_apis"} or {@code "how_to_call"}
     * @param argumentsJson a JSON object literal, e.g. {@code "{\"resource\": \"...\"}"}
     * @return the tool's result, already unwrapped and parsed (i.e. {@code list_apis}'s catalog
     *         document, or {@code how_to_call}'s descriptor document)
     * @throws AssertionError if the tool call itself reports {@code isError: true}
     */
    BsonDocument callTool(String toolName, String argumentsJson) throws Exception {
        if (sessionId == null) {
            throw new IllegalStateException("call initialize() first");
        }

        var body = """
                {"jsonrpc":"2.0","id":%d,"method":"tools/call","params":{"name":"%s","arguments":%s}}
                """.formatted(nextId.getAndIncrement(), toolName, argumentsJson);
        var response = send(body, sessionId);

        var envelope = extractJsonRpcEnvelope(response.body());
        if (envelope.containsKey("error")) {
            throw new AssertionError("tools/call transport-level error: " + envelope.get("error"));
        }

        var result = envelope.getDocument("result");
        var text = result.getArray("content").get(0).asDocument().getString("text").getValue();
        if (result.containsKey("isError") && result.getBoolean("isError").getValue()) {
            throw new AssertionError(toolName + " reported isError=true: " + text);
        }

        return BsonDocument.parse(text);
    }

    private HttpResponse<String> send(String jsonRpcBody, String sessionIdHeader) throws Exception {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create(mcpUrl))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("Authorization", basicAuth)
                .POST(HttpRequest.BodyPublishers.ofString(jsonRpcBody));
        if (sessionIdHeader != null) {
            builder.header("Mcp-Session-Id", sessionIdHeader).header("Mcp-Protocol-Version", "2025-03-26");
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Response body is either plain JSON ({@code initialize}) or one SSE frame ({@code tools/call}). */
    private static BsonDocument extractJsonRpcEnvelope(String body) {
        var trimmed = body.strip();
        if (trimmed.startsWith("{")) {
            return BsonDocument.parse(trimmed);
        }
        var dataLine = trimmed.lines().filter(l -> l.startsWith("data:")).findFirst()
                .orElseThrow(() -> new IllegalStateException("no data: line in SSE response: " + body));
        return BsonDocument.parse(dataLine.substring("data:".length()).strip());
    }
}
