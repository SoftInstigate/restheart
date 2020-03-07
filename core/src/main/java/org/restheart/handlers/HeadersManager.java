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
package org.restheart.handlers;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import io.undertow.util.HttpString;

/**
 * @author msuchecki
 */
public class HeadersManager {

    private final HttpServerExchange exchange;

    /**
     *
     * @param exchange
     */
    public HeadersManager(HttpServerExchange exchange) {
        this.exchange = exchange;
    }

    /**
     *
     * @param headerName
     * @return
     */
    public boolean isRequestHeaderSet(HttpString headerName) {
        return isSet(getRequestHeader(headerName));
    }

    private boolean isSet(HeaderValues vals) {
        return vals != null && !vals.isEmpty();
    }

    /**
     *
     * @param headerName
     * @return
     */
    public HeaderValues getRequestHeader(HttpString headerName) {
        return exchange.getRequestHeaders().get(headerName);
    }

    /**
     *
     * @param headerName
     * @param value
     */
    public void addResponseHeader(HttpString headerName, String value) {
        exchange.getResponseHeaders().put(headerName, value);
    }

    /**
     *
     * @param headerName
     * @param value
     */
    public void addResponseHeader(HttpString headerName, Boolean value) {
        exchange.getResponseHeaders().put(headerName, value.toString());
    }
}
