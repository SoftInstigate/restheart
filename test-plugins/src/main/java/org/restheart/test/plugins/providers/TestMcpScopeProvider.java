package org.restheart.test.plugins.providers;

import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.mcp.McpScopeProvider;

/**
 * Stands in for a deployment that partitions the MCP catalogue, so both behaviours can be
 * exercised against one running instance.
 *
 * <p>A request carrying {@code ?mcpScope=<scope>} resolves to that scope; every other request
 * resolves to {@link McpScopeProvider#UNPARTITIONED}, which is what an instance with no provider
 * registered does. That is what makes this safe to leave enabled for the whole suite: the other
 * eighty-odd MCP tests never send the parameter and see exactly today's behaviour, and the tests
 * that do send it get a partitioned instance without a second RESTHeart to run.
 *
 * <p>{@code ?mcpScope=?} resolves to {@link McpScopeProvider#UNRESOLVED}, for asserting that a
 * request whose scope cannot be determined is refused rather than served the whole catalogue.
 *
 * <p>On a real partitioned deployment the scope comes from the hostname, which is present on every
 * request whatever the credential. A query parameter is used here for the same reason the other
 * test plugins in this module use one: the suite runs a single RESTHeart on a single hostname, and
 * a host-parametric mount would change routing for every scenario in it.
 */
@RegisterPlugin(
        name = "testMcpScopeProvider",
        description = "Resolves the MCP scope from ?mcpScope=<scope>, unpartitioned otherwise",
        enabledByDefault = false)
public class TestMcpScopeProvider implements Provider<McpScopeProvider> {

    private static final String QPARAM = "mcpScope";

    @Override
    public McpScopeProvider get(PluginRecord<?> caller) {
        return request -> {
            if (request == null) {
                return McpScopeProvider.UNPARTITIONED;
            }

            var scope = request.getExchange().getQueryParameters().containsKey(QPARAM)
                    ? request.getExchange().getQueryParameters().get(QPARAM).peekFirst()
                    : null;

            return scope == null || scope.isBlank() ? McpScopeProvider.UNPARTITIONED : scope;
        };
    }
}
