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
package org.restheart.ai.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the {@code tools/list} JSON-RPC response: exactly two tools, {@code list_apis}
 * and {@code how_to_call} — see restheart#615 design principles ("one tool, one model").
 * Purely static content, built once and reused.
 */
public final class McpManifestBuilder {

    private static final Map<String, Object> MANIFEST = buildManifest();

    private McpManifestBuilder() {
    }

    public static Map<String, Object> toolsList() {
        return MANIFEST;
    }

    private static Map<String, Object> buildManifest() {
        var manifest = new LinkedHashMap<String, Object>();
        manifest.put("tools", List.of(listApisTool(), howToCallTool()));
        return manifest;
    }

    private static Map<String, Object> listApisTool() {
        var tool = new LinkedHashMap<String, Object>();
        tool.put("name", "list_apis");
        tool.put("description",
                "Lists or describes MCP-enabled APIs exposed by RESTHeart. Without arguments, returns the "
                        + "catalog (URIs, kinds, short descriptions) — optionally narrowed with `query`/`kind` and "
                        + "paged with `limit`/`cursor`. With a resource URI, returns full context: kind, supported "
                        + "transports, actions with parameter types, auth requirements, examples. On a deployment "
                        + "with many resources, prefer a filtered call over an unfiltered one. Call this before "
                        + "how_to_call to learn what you can do with a resource.");

        var properties = new LinkedHashMap<String, Object>();
        properties.put("resource", schemaProp("string", "uri", "Optional. Omit for the catalog."));
        properties.put("query", schemaProp("string", null,
                "Optional. Case-insensitive substring match against each resource's uri/kind/description. Ignored if `resource` is given."));
        properties.put("kind", schemaProp("string", null,
                "Optional. Restrict the catalog to a single kind (e.g. `collection`, `service`). Ignored if `resource` is given."));
        properties.put("limit", schemaProp("integer", null,
                "Optional. Max catalog entries to return (default: framework-configured page size). Ignored if `resource` is given."));
        properties.put("cursor", schemaProp("string", null, "Optional. Continues a previous paged catalog call."));

        var inputSchema = new LinkedHashMap<String, Object>();
        inputSchema.put("type", "object");
        inputSchema.put("properties", properties);
        tool.put("inputSchema", inputSchema);

        return tool;
    }

    private static Map<String, Object> howToCallTool() {
        var tool = new LinkedHashMap<String, Object>();
        tool.put("name", "how_to_call");
        tool.put("description",
                "Returns a request descriptor (transport, URL, headers, body) for invoking a known MCP resource. "
                        + "The tool COMPOSES the request — it does NOT execute it. After receiving the response, "
                        + "choose any client appropriate to the descriptor's transport and your host environment "
                        + "(HTTP libraries, WebSocket libraries, OS shells with curl/httpie/wscat, generated code in "
                        + "any language). The MCP server does not prescribe the tool.\n\nDispatch by action — the "
                        + "set of valid actions for a given resource is declared in the resource's list_apis output. "
                        + "Validate args against the declared params and body_schema before calling.");

        var properties = new LinkedHashMap<String, Object>();
        properties.put("resource", schemaProp("string", "uri", "Resource URI."));
        properties.put("action", schemaProp("string", null, "Action name as declared in the resource's actions map."));
        properties.put("args", schemaProp("object", null, "Action arguments — values for params and body declared by the resource."));
        properties.put("transport", schemaProp("string", null,
                "Optional transport preference (e.g. websocket vs sse for streams). If omitted, the resource's default transport is used."));
        properties.put("token", schemaProp("string", null, "Optional access token. If omitted, a `<token>` placeholder is embedded."));

        var inputSchema = new LinkedHashMap<String, Object>();
        inputSchema.put("type", "object");
        inputSchema.put("properties", properties);
        inputSchema.put("required", List.of("resource", "action"));
        tool.put("inputSchema", inputSchema);

        return tool;
    }

    private static Map<String, Object> schemaProp(String type, String format, String description) {
        var prop = new LinkedHashMap<String, Object>();
        prop.put("type", type);
        if (format != null) {
            prop.put("format", format);
        }
        prop.put("description", description);
        return prop;
    }
}
