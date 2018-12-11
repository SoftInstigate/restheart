/*
 * uIAM - the IAM for microservices
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

    public PasswordCredential get(Account account);

    public void invalidate(Account account, PasswordCredential token);

    public void injectTokenHeaders(HttpServerExchange exchange, PasswordCredential token);
}