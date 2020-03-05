/*
 * RESTHeart - the Web API for MongoDB
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
package org.restheart.handlers.exchange;

import io.undertow.server.HttpServerExchange;
import org.bson.BsonValue;
import org.restheart.db.OperationResult;
import static org.restheart.handlers.exchange.AbstractExchange.LOGGER;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class BsonResponse extends Response<BsonValue> {
    private BsonValue content;
    
    private OperationResult dbOperationResult;
    
    protected BsonResponse(HttpServerExchange exchange) {
        super(exchange);
        LOGGER = LoggerFactory.getLogger(BsonResponse.class);
    }
    
    public static BsonResponse wrap(HttpServerExchange exchange) {
        return new BsonResponse(exchange);
    }
    
    /**
     * @return the content
     */
    public BsonValue getContent() {
        return content;
    }

    /**
     * @param content the content to set
     */
    public void setContent(BsonValue content) {
        this.content = content;
    }

    /**
     * @return the dbOperationResult
     */
    public OperationResult getDbOperationResult() {
        return dbOperationResult;
    }

    /**
     * @param dbOperationResult the dbOperationResult to set
     */
    public void setDbOperationResult(OperationResult dbOperationResult) {
        this.dbOperationResult = dbOperationResult;
    }
}
