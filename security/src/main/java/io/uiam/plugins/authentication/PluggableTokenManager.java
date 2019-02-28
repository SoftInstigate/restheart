/*
 * uIAM - the IAM for microservices
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
package io.uiam.plugins.authentication;

import io.undertow.security.idm.Account;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

/**
 * Interface for token managers
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public interface PluggableTokenManager extends PluggableIdentityManager {
    static final HttpString AUTH_TOKEN_HEADER = HttpString.tryFromString("Auth-Token");
    static final HttpString AUTH_TOKEN_VALID_HEADER = HttpString.tryFromString("Auth-Token-Valid-Until");
    static final HttpString AUTH_TOKEN_LOCATION_HEADER = HttpString.tryFromString("Auth-Token-Location");

    /**
     * retrieves of generate a token valid for the account
     * @param account
     * @return the token for the account
     */
    public PasswordCredential get(Account account);

    /**
     * invalidates a token
     * @param account
     * @param token 
     */
    public void invalidate(Account account, PasswordCredential token);

    /**
     * injects the token headers in the response
     * 
     * @param exchange
     * @param token 
     */
    public void injectTokenHeaders(HttpServerExchange exchange, PasswordCredential token);
}