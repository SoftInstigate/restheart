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

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.Request;
import org.restheart.exchange.ByteArrayResponse;
import org.restheart.plugins.ByteArrayInterceptor;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.RegisterPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

/**
 * Leaves out of {@code resources/list} and {@code resources/templates/list} what the caller could
 * not read anyway.
 *
 * <p>The MCP SDK serves both from a registry built once for the whole server, not per request, and
 * gives a server author no way to supply its own list handler
 * (<a href="https://github.com/modelcontextprotocol/java-sdk/issues/1128">java-sdk#1128</a>);
 * per-caller repositories are
 * <a href="https://github.com/modelcontextprotocol/java-sdk/issues/578">java-sdk#578</a>, targeted
 * at SDK 3.0. Until then the listing is filtered on the way out, which is possible because those
 * methods now answer through RESTHeart's own response sender rather than writing SSE into the
 * exchange — see {@code UndertowStreamableServerTransportProvider.BUFFERED_METHODS}.
 *
 * <p>The rule is not restated here: {@link CatalogVisibility} asks the framework's own
 * {@code DescriptorAuthorization}, the same one applied to a real request, so a listing cannot
 * disagree with what a read would do.
 *
 * <p><strong>This filter fails loudly, not open.</strong> It is coupled to the SDK's result shape,
 * and a security filter that silently stops matching is worse than none: if the payload is not the
 * shape it expects, it says so at WARN and leaves the response alone rather than pretending to
 * have filtered it.
 */
@RegisterPlugin(
        name = "mcpCatalogFilterInterceptor",
        description = "Removes from resources/list and resources/templates/list the resources the caller cannot read",
        interceptPoint = InterceptPoint.RESPONSE,
        requiresContent = true)
public class McpCatalogFilterInterceptor implements ByteArrayInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpCatalogFilterInterceptor.class);

    /** As registered in {@code McpService}: this interceptor concerns that service and no other. */
    private static final String MCP_SERVICE = "mcpService";

    /** The two list results, and the array each carries. */
    private static final Map<String, String> LISTED = Map.of(
            "resources", "uri",
            "resourceTemplates", "uriTemplate");

    @Override
    public boolean resolve(ByteArrayRequest request, ByteArrayResponse response) {
        if (!request.isPost() || response.isInError()
                || response.getContent() == null || response.getContent().length == 0) {
            return false;
        }

        // ByteArrayInterceptors are offered every ByteArrayService's response; only /mcp's is ours.
        var pipeline = Request.getPipelineInfo(request.getExchange());

        return pipeline != null && MCP_SERVICE.equals(pipeline.getName());
    }

    @Override
    public void handle(ByteArrayRequest request, ByteArrayResponse response) throws Exception {
        var body = JsonParser.parseString(new String(response.getContent(), StandardCharsets.UTF_8));

        if (!body.isJsonObject() || !body.getAsJsonObject().has("result")) {
            return;
        }

        var result = body.getAsJsonObject().getAsJsonObject("result");

        var listed = LISTED.entrySet().stream()
                .filter(e -> result.has(e.getKey()) && result.get(e.getKey()).isJsonArray())
                .findFirst()
                .orElse(null);

        if (listed == null) {
            return;
        }

        var visible = McpService.catalogVisibility(request.getExchange());
        var entries = result.getAsJsonArray(listed.getKey());

        var kept = new JsonArray();
        var dropped = 0;

        for (var entry : entries) {
            var uri = uriOf(entry, listed.getValue());

            if (uri == null) {
                LOGGER.warn("mcpCatalogFilterInterceptor: an entry of '{}' has no '{}' — the SDK's result"
                        + " shape has changed and the catalog is NOT being filtered", listed.getKey(), listed.getValue());
                return;
            }

            if (visible.test(stripTemplate(uri))) {
                kept.add(entry);
            } else {
                dropped++;
            }
        }

        if (dropped == 0) {
            return;
        }

        result.add(listed.getKey(), kept);
        response.setContent(body.toString());

        LOGGER.debug("removed {} of {} entries from '{}'", dropped, entries.size(), listed.getKey());
    }

    private static String uriOf(com.google.gson.JsonElement entry, String field) {
        return entry.isJsonObject() && entry.getAsJsonObject().has(field)
                ? entry.getAsJsonObject().get(field).getAsString()
                : null;
    }

    /**
     * A template URI carries the parameters it accepts ({@code /coll/_size{?filter,count}}) and a
     * document template a placeholder ({@code /coll/{id}}); both are cut back to the resource they
     * belong to, since that is what the catalog is keyed by.
     */
    private static String stripTemplate(String uri) {
        var brace = uri.indexOf('{');
        var stripped = brace < 0 ? uri : uri.substring(0, brace);
        return stripped.endsWith("/") ? stripped.substring(0, stripped.length() - 1) : stripped;
    }

}
