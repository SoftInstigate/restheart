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
package org.restheart.handlers.injectors;

import io.undertow.server.HttpServerExchange;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.handlers.exchange.ByteArrayRequest;
import org.restheart.handlers.exchange.PipelineBranchInfo;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 *
 * It injects the PipelineBranchInfo. It allows to programmatically understand
 * which pipeline branch (service, proxy or static resource) is handling the
 * request via BsonRequest.getPipelineBranchInfo()
 */
public class PipelineBranchInfoInjector extends PipelinedHandler {
    private final PipelineBranchInfo pbi;

    /**
     * Creates a new instance of PipelineBranchInfoInjector
     *
     * @param next
     * @param pbi
     */
    public PipelineBranchInfoInjector(PipelinedHandler next, PipelineBranchInfo pbi) {
        super(next);
        this.pbi = pbi;
    }

    /**
     * Creates a new instance of PipelineBranchInfoInjector
     *
     * @param pbi
     */
    public PipelineBranchInfoInjector(PipelineBranchInfo pbi) {
        this(null, pbi);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        ByteArrayRequest.wrap(exchange).setPipelineBranchInfo(this.pbi);

        next(exchange);
    }
}
