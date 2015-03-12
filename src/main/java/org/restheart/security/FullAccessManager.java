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
package org.restheart.security;

import com.google.common.collect.Sets;
import io.undertow.predicate.Predicate;
import io.undertow.predicate.Predicates;
import io.undertow.server.HttpServerExchange;
import java.util.HashMap;
import java.util.Set;
import org.restheart.handlers.RequestContext;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class FullAccessManager implements AccessManager {
    private final FullAccessManagerMap acl;

    /**
     * this access manager allows any operation to any user
     */
    public FullAccessManager() {
        acl = new FullAccessManagerMap();
        Set predicates = Sets.newHashSet();
        
        predicates.add(Predicates.truePredicate());
        acl.put("k", predicates);
    }
    
    @Override
    public boolean isAllowed(HttpServerExchange exchange, RequestContext context) {
        return true;
    }

    @Override
    public HashMap<String, Set<Predicate>> getAcl() {
        return acl;
    }
}

class FullAccessManagerMap extends HashMap {
    private final String KEY = "k";
    
    @Override
    public Object get(Object key) {
        return super.get(KEY); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Object put(Object key, Object value) {
        return super.put(KEY, value); //To change body of generated methods, choose Tools | Templates.
    }
}