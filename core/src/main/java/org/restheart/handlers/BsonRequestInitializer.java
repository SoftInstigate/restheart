/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
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
package org.restheart.handlers;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.PathMatcher;
import org.restheart.handlers.exchange.BsonRequest;
import org.restheart.handlers.exchange.PipelineBranchInfo.PIPELINE_BRANCH;
import org.restheart.plugins.MongoMount;
import org.restheart.plugins.PluginsRegistryImpl;

/**
 * Initializes BsonRequest for requets handled by MongoService
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class BsonRequestInitializer extends PipelinedHandler {
    private final MongoMount DEFAULT_MONGO_MOUNT = new MongoMount("*", "/");

    /**
     * PathMatcher is used by the root PathHandler to route the call. Here we
     * use the same logic to identify the correct MongoMount in order to
     * correctly init the BsonRequest
     */
    private final PathMatcher<MongoMount> mongoMounts = new PathMatcher<>();

    /**
     * Creates a new instance of CORSHandler
     *
     */
    public BsonRequestInitializer() {
        super();
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var path = exchange.getRequestPath();

        var pi = PluginsRegistryImpl.getInstance().getPipelineInfo(path);

        if (pi != null && 
                pi.getType() == PIPELINE_BRANCH.SERVICE
                && "mongo".equals(pi.getName())) {
            var mmm = mongoMounts.match(path);

            if (mmm != null && mmm.getValue() != null) {
                var mm = mmm.getValue();

                BsonRequest.init(exchange,
                        mm.uri,
                        mm.resource);
            } else {
                BsonRequest.init(exchange,
                        DEFAULT_MONGO_MOUNT.uri,
                        DEFAULT_MONGO_MOUNT.resource);
            }
        }

        next(exchange);
    }

    /**
     * Adds a MongoMount. The BsonRequest is initialized with the matching
     * MongoMount. If none matches, DEFAULT_MONGO_MOUNT is used. This allows
     * initializing BsonRequest with the correct (uri, resource) parameters for
     * the mongo service
     *
     * @param mm
     */
    public void addMongoMount(MongoMount mm) {
        mongoMounts.addPrefixPath(mm.uri, mm);
    }
}
