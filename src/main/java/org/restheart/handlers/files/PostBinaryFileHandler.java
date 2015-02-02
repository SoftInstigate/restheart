/*
 * RESTHeart - the data REST API server
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.restheart.handlers.files;

import io.undertow.server.HttpServerExchange;
import org.restheart.db.Database;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Maurizio Turatti <maurizio@softinstigate.com>
 */
public class PostBinaryFileHandler extends PipedHttpHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostBinaryFileHandler.class);

    public PostBinaryFileHandler() {
        super();
    }

    public PostBinaryFileHandler(PipedHttpHandler next, Database dbsDAO) {
        super(next, dbsDAO);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        final String errMsg = "Operation 'POST' not implemented yet for binary files";
        LOGGER.error(errMsg);
        ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_IMPLEMENTED, errMsg);
    }

}
