/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2020 SoftInstigate
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
package org.restheart.plugins.security;

import io.undertow.server.HttpServerExchange;
import org.restheart.plugins.ConfigurablePlugin;

/**
 * @see https://restheart.org/docs/develop/security-plugins/#authorizers
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface Authorizer extends ConfigurablePlugin {

    /**
     *
     * @param exchange
     * @return true if request is allowed
     */
    boolean isAllowed(HttpServerExchange exchange);

    /**
     *
     * @param exchange
     * @return true if not authenticated user won't be allowed
     */
    boolean isAuthenticationRequired(final HttpServerExchange exchange);
}
