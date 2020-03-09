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
package org.restheart.handlers.metadata;

import java.util.List;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.plugins.GlobalChecker;
import org.restheart.plugins.PluginsRegistry;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public abstract class CheckHandler extends PipelinedHandler {
    /**
     *
     * @param next
     */
    public CheckHandler(PipelinedHandler next) {
        super(next);
    }

    /**
     *
     * @deprecated use PluginsRegistry.getInstance().getGlobalCheckers() instead
     * @return the globalCheckers
     */
    @Deprecated
    public static List<GlobalChecker> getGlobalCheckers() {
        return PluginsRegistry.getInstance().getGlobalCheckers();
    }
}
