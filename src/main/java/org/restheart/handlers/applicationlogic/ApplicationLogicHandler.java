/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
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
package org.restheart.handlers.applicationlogic;

import org.restheart.handlers.PipedHttpHandler;
import java.util.Map;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public abstract class ApplicationLogicHandler extends PipedHttpHandler {

    /**
     * The configuration properties passed to this handler.
     */
    protected final Map<String, Object> args;

    /**
     * Creates a new instance of the ApplicationLogicHandler
     *
     * @param next
     * @param args
     */
    public ApplicationLogicHandler(PipedHttpHandler next, Map<String, Object> args) {
        super(next);
        this.args = args;
    }
}
