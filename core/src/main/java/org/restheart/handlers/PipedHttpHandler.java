/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.handlers;

/**
 * @deprecated use PipelinedHandler
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@Deprecated
public abstract class PipedHttpHandler extends PipelinedHandler {

    /**
     * Creates a default instance of PipedHttpHandler with next = null
     */
    public PipedHttpHandler() {
        super(null);
    }

    /**
     * Creates an instance of PipedHttpHandler with specified next handler
     *
     * @param next the next handler in this chain
     */
    public PipedHttpHandler(PipelinedHandler next) {
        super(next);
    }

    /**
     * set the next PipedHttpHandler
     * @param next
     */
    protected void setNext(PipedHttpHandler next) {
        super.setNext(next);
    }
}
