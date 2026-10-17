/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
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
package org.restheart.mongodb.interceptors;

import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.mcp.McpCatalogInvalidator;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drops the MCP catalogue when the metadata it was built from is written.
 *
 * <p>What an agent is told about a collection — that it exists, what it is for, which aggregations
 * and change streams it offers — is read from that collection's metadata. Change the metadata and
 * the catalogue is stale until it expires on its own, which means a client is told the catalogue
 * changed at some arbitrary later moment rather than at this one, and anything showing "what an
 * agent sees" shows the answer from before.
 *
 * <p>Only metadata writes, and only successful ones. A document write is far more frequent and
 * changes nothing the catalogue says; a refused write changed nothing at all.
 *
 * <p>Does nothing when no MCP server is registered on this instance: there is no catalogue to drop,
 * and the invalidator is simply absent.
 */
@RegisterPlugin(
        name = "mcpCatalogInvalidatorOnMetadataWrite",
        description = "Drops the MCP catalogue of a scope when collection or database metadata is written",
        interceptPoint = InterceptPoint.RESPONSE)
public class McpCatalogInvalidatorOnMetadataWrite implements MongoInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpCatalogInvalidatorOnMetadataWrite.class);

    @Inject("registry")
    private PluginsRegistry registry;

    private McpCatalogInvalidator invalidator;

    @OnInit
    public void init() {
        // By type, as mcpService finds its scope provider: nothing has to agree on a name, and an
        // instance without MCP simply has none.
        this.invalidator = registry.getProviders().stream()
                .filter(p -> McpCatalogInvalidator.class.isAssignableFrom(p.getInstance().rawType()))
                .findFirst()
                .map(p -> (McpCatalogInvalidator) p.getInstance().get(null))
                .orElse(null);

        if (invalidator != null) {
            LOGGER.debug("MCP catalogues will be dropped when the metadata they describe is written");
        }
    }

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        invalidator.invalidate(request);
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return invalidator != null
                && request.isHandledBy("mongo")
                && (request.isPut() || request.isPatch() || request.isDelete())
                && (request.isCollection() || request.isCollectionMeta() || request.isDb() || request.isDbMeta())
                && response.getStatusCode() > 0
                && response.getStatusCode() < HttpStatus.SC_BAD_REQUEST;
    }
}
