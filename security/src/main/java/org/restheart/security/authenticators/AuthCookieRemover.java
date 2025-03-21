/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2025 SoftInstigate
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

package org.restheart.security.authenticators;

import java.util.Map;

import org.restheart.configuration.Configuration;
import org.restheart.exchange.BsonRequest;
import org.restheart.exchange.BsonResponse;
import org.restheart.plugins.BsonService;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;

import io.undertow.server.handlers.CookieImpl;

/**
 * unsets the rh_auth_token cookie
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RegisterPlugin(name = "authCookieRemover",
    description = "unsets the auth cookie",
    secure = false,
    defaultURI = "/logout")
public class AuthCookieRemover implements BsonService {
    @Inject("rh-config")
    private Configuration rhConfig;

    private String name;
    private String domain;
    private String path;
    private boolean secure;
    private boolean httpOnly;
    private boolean sameSite;
    private String sameSiteMode;

    @OnInit
    @SuppressWarnings("unchecked")
    public void init() {
        if (rhConfig.toMap().containsKey("authCookieSetter")) {
            var authCookieSetterConf = (Map<String, Object>) rhConfig.toMap().get("authCookieSetter");

            this.name = argOrDefault(authCookieSetterConf, "name", "rh_auth");
            this.secure = argOrDefault(authCookieSetterConf, "secure", true);
            this.domain = argOrDefault(authCookieSetterConf, "domain", "localhost");
            this.path = argOrDefault(authCookieSetterConf, "path", "/");
            this.httpOnly = argOrDefault(authCookieSetterConf, "http-only", true);
            this.sameSite = argOrDefault(authCookieSetterConf, "same-site", true);
            this.sameSiteMode = argOrDefault(authCookieSetterConf, "same-site-mode", "strict");
        } else {
            this.name = "rh_auth";
            this.domain ="localhost";
            this.path = "/";
            this.httpOnly = true;
            this.sameSite = true;
            this.sameSiteMode = "strict";
        }
    }

    @Override
    public void handle(BsonRequest req, BsonResponse res) throws Exception {
        switch(req.getMethod()) {
            case POST -> unsetAuthTokenCookie(res);
            case OPTIONS -> handleOptions(req);
            default -> res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
        }
    }

    private void unsetAuthTokenCookie(BsonResponse res) {
        res.getExchange().setResponseCookie(new CookieImpl(this.name, null)
            .setSecure(this.secure)
            .setHttpOnly(this.httpOnly)
            .setDomain(this.domain)
            .setPath(this.path)
            .setSameSite(this.sameSite)
            .setSameSiteMode(this.sameSiteMode));
    }
}
