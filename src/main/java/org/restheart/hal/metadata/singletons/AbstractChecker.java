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
package org.restheart.hal.metadata.singletons;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import io.undertow.server.HttpServerExchange;
import static org.restheart.hal.metadata.singletons.Checker.FAIL_IF_NOT_SUPPORTED_PROPERTY;
import org.restheart.handlers.RequestContext;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public abstract class AbstractChecker implements Checker {
    @Override
    public abstract boolean check(HttpServerExchange exchange, RequestContext context, BasicDBObject contentToCheck, DBObject args);

    @Override
    public abstract PHASE getPhase(RequestContext context);

    @Override
    public abstract boolean doesSupportRequests(RequestContext context);
    
    @Override
    public boolean shouldCheckFailIfNotSupported(DBObject args) {
        if (args == null) {
            return true;
        }
        
        Object _failIfNotSupported = args.get(FAIL_IF_NOT_SUPPORTED_PROPERTY);

        if (_failIfNotSupported == null) {
            return true;
        } else if (_failIfNotSupported instanceof Boolean) {
            return (Boolean) _failIfNotSupported;
        } else {
            throw new IllegalArgumentException("property " + FAIL_IF_NOT_SUPPORTED_PROPERTY + " in metadata 'args' must be boolean");
        }
    }
}
