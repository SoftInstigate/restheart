/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2024 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.plugins.security;

import io.undertow.security.idm.Account;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

/**
 * Interface for token managers
 *
 * See https://restheart.org/docs/plugins/security-plugins/#token-managers
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface TokenManager extends Authenticator {
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
    public PasswordCredential get(final Account account);

    /**
     * invalidates the token bound to the account
     *
     * @param account
     */
    public void invalidate(final Account account);

    /**
     * updates the account bound to a token
     *
     * @param account
     */
    public void update(final Account account);

    /**
     * injects the token headers in the response
     *
     * @param exchange
     * @param token
     */
    public void injectTokenHeaders(final HttpServerExchange exchange, final PasswordCredential token);
}
