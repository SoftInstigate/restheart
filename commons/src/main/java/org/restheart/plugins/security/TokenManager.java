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

import io.undertow.security.idm.Account;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import org.restheart.plugins.ConfigurablePlugin;

/**
 * Interface for token managers
 *
 * @see https://restheart.org/docs/develop/security-plugins/#token-managers
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public interface TokenManager extends Authenticator, ConfigurablePlugin {
    public static final HttpString AUTH_TOKEN_HEADER = HttpString.tryFromString("Auth-Token");
    public static final HttpString AUTH_TOKEN_VALID_HEADER = HttpString.tryFromString("Auth-Token-Valid-Until");
    public static final HttpString AUTH_TOKEN_LOCATION_HEADER = HttpString.tryFromString("Auth-Token-Location");
    public static final HttpString ACCESS_CONTROL_EXPOSE_HEADERS = HttpString.tryFromString("Access-Control-Expose-Headers");
    /**
     * retrieves of generate a token valid for the account
     *
     * @param account
     * @return the token for the account
     */
    public PasswordCredential get(Account account);

    /**
     * invalidates the token bound to the account
     *
     * @param account
     */
    public void invalidate(Account account);

    /**
     * updates the account bound to a token
     *
     * @param account
     */
    public void update(Account account);

    /**
     * injects the token headers in the response
     *
     * @param exchange
     * @param token
     */
    public void injectTokenHeaders(HttpServerExchange exchange, PasswordCredential token);
}
