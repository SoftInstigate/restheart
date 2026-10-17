package org.restheart.ai.mcp;

import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.mcp.McpCatalogInvalidator;

/**
 * Hands out the way to tell the MCP server that a catalogue is out of date.
 *
 * <p>A provider rather than a direct call, so that a module which knows its own data changed —
 * {@code restheart-mongodb}, when a collection's metadata is written — can say so without
 * depending on this one. Found by type, as {@code McpScopeProvider} is.
 *
 * <p>The returned invalidator is a lambda and not the service itself: MCP may be disabled, or not
 * yet initialised, and then there is no catalogue to drop and saying so must cost nothing.
 */
@RegisterPlugin(
        name = "mcpCatalogInvalidator",
        description = "Lets a service tell the MCP server that something it describes has changed")
public class McpCatalogInvalidatorProvider implements Provider<McpCatalogInvalidator> {

    @Override
    public McpCatalogInvalidator get(PluginRecord<?> caller) {
        return request -> {
            var mcp = McpService.instance();

            if (mcp != null) {
                mcp.invalidateCatalog(request);
            }
        };
    }
}
