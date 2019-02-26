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
package org.restheart.handlers.feed;

import io.undertow.websockets.WebSocketProtocolHandshakeHandler;
import java.util.List;
import org.bson.BsonDocument;

/**
 *
 * @author omartrasatti
 */
public class CacheableFeed {

    private WebSocketProtocolHandshakeHandler webSocketProtocolHandshakeHandler;
    private List<BsonDocument> aVars = null;

    public CacheableFeed(
            WebSocketProtocolHandshakeHandler webSocketProtocolHandshakeHandler,
            List<BsonDocument> aVars) {
        
        this.webSocketProtocolHandshakeHandler = webSocketProtocolHandshakeHandler;
        this.aVars = aVars;
        
    }
    
    public WebSocketProtocolHandshakeHandler getHandshakeHandler() {
        return this.webSocketProtocolHandshakeHandler;
    }
    
    public List<BsonDocument> getAVars() {
        return this.aVars;
    }
}
