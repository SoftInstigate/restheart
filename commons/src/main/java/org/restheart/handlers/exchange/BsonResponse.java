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
import io.undertow.util.AttachmentKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bson.BsonValue;
import static org.restheart.handlers.exchange.AbstractExchange.LOGGER;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class BsonResponse extends Response<BsonValue> {
    private static final AttachmentKey<BsonResponse> BSON_RESPONSE_ATTACHMENT_KEY
            = AttachmentKey.create(BsonResponse.class);
    
    private BsonValue content;
    
    private OperationResult dbOperationResult;
    
    private final List<String> warnings = new ArrayList<>();
    
    protected BsonResponse(HttpServerExchange exchange) {
        super(exchange);
        LOGGER = LoggerFactory.getLogger(BsonResponse.class);
    }
    
    public static BsonResponse wrap(HttpServerExchange exchange) {
        var cached = exchange.getAttachment(BSON_RESPONSE_ATTACHMENT_KEY);
        
        if (cached == null) {
            var response = new BsonResponse(exchange);
            exchange.putAttachment(BSON_RESPONSE_ATTACHMENT_KEY,
                    response);
            return response;
        } else {
            return cached;
        }
    }
    
    public static boolean isInitialized(HttpServerExchange exchange) {
        return (exchange.getAttachment(BSON_RESPONSE_ATTACHMENT_KEY) != null);
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
    
    /**
     * @return the warnings
     */
    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    /**
     * @param warning
     */
    public void addWarning(String warning) {
        warnings.add(warning);
    }
}
