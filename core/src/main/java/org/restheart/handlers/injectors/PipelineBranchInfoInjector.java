/*
 * RESTHeart Security
 * 
 * Copyright (C) SoftInstigate Srl
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
