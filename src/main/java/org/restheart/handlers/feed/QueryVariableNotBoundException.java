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

import org.restheart.hal.InvalidHalException;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class QueryVariableNotBoundException extends InvalidHalException {

    /**
     *
     */
    public QueryVariableNotBoundException() {
        super();
    }

    /**
     *
     * @param message
     */
    public QueryVariableNotBoundException(String message) {
        super(message);
    }

    /**
     *
     * @param message
     * @param cause
     */
    public QueryVariableNotBoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
