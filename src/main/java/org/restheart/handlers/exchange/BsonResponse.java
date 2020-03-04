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

import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.HttpServerExchange;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.bson.BsonNull;
import org.bson.BsonValue;
import org.bson.json.JsonParseException;
import org.restheart.db.OperationResult;
import static org.restheart.handlers.exchange.AbstractExchange.LOGGER;
import static org.restheart.handlers.exchange.AbstractExchange.MAX_BUFFERS;
import org.restheart.utils.BuffersUtils;
import org.restheart.utils.JsonUtils;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class BsonResponse extends Response<BsonValue> {
    private OperationResult dbOperationResult;
    
    protected BsonResponse(HttpServerExchange exchange) {
        super(exchange);
        LOGGER = LoggerFactory.getLogger(BsonResponse.class);
    }
    
    public static BsonResponse wrap(HttpServerExchange exchange) {
        return new BsonResponse(exchange);
    }
    
    @Override
    protected BsonValue getErrorContent(int code,
            String httpStatusText,
            String message,
            Throwable t,
            boolean includeStackTrace) throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public BsonValue readContent() throws IOException {
        if (!isContentAvailable()) {
            return null;
        }

        if (getWrapped().getAttachment(getRawContentKey()) == null) {
            return BsonNull.VALUE;
        } else {
            try {
                String rawContentAsString = BuffersUtils.toString(
                        getRawContent(),
                        StandardCharsets.UTF_8);

                return JsonUtils.parse(rawContentAsString);
            } catch (JsonParseException ex) {
                throw new IOException("Error parsing json", ex);
            }
        }
    }

    @Override
    public void writeContent(BsonValue content) throws IOException {
        if (content != null
                && !(content.isDocument()
                || content.isArray())) {
            throw new IllegalArgumentException("response content must be "
                    + "either an object or an array");
        }
        
        setContentTypeAsJson();
        if (content == null) {
            setRawContent(null);
        } else {
            PooledByteBuffer[] dest;
            if (isContentAvailable()) {
                dest = getRawContent();
            } else {
                dest = new PooledByteBuffer[MAX_BUFFERS];
                setRawContent(dest);
            }

            BuffersUtils.transfer(
                    ByteBuffer.wrap(JsonUtils.toJson(content).getBytes()),
                    dest,
                    wrapped);
        }
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
