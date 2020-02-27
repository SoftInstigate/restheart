/*
 * RESTHeart Security
 * 
 * Copyright (C) SoftInstigate Srl
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.restheart.security.plugins;

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
