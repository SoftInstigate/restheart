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

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.WildcardInterceptor;
import static org.restheart.security.authenticators.AuthCookieHandler.enabled;

import io.undertow.server.handlers.CookieImpl;


/**
 * sets the rh_auth_token cookie when the URL contains the qparam ?set-auth-cookie
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RegisterPlugin(name = "authCookieSetter",
                description = "sets the auth cookie if the URL contains the qparam ?set-auth-cookie",
                interceptPoint = InterceptPoint.RESPONSE)
public class AuthCookieSetter implements WildcardInterceptor {
    @Inject("config")
    private Map<String, Object> config;

    @Inject("registry")
    PluginsRegistry pluginsRegistry;

    private boolean enabled = true;
    private boolean jwtAuthWithJwtAuthMechanism = false;

    private String name;
    private String domain;
    private String path;
    private boolean secure;
    private boolean httpOnly;
    private boolean sameSite;
    private String sameSiteMode;
    private int secondsUntilExpiration; // the number of seconds until the cookie expires

    @OnInit
    public void init() {
        this.enabled = enabled(pluginsRegistry, true);
        // true if the token manager is jwtTokenManager and jwtAuthenticationMechanism is enabled
        this.jwtAuthWithJwtAuthMechanism = pluginsRegistry.getTokenManager() != null && "jwtTokenManager".equals(pluginsRegistry.getTokenManager().getName()) && pluginsRegistry.getAuthMechanisms().stream().map(pr -> pr.getName()).anyMatch(n -> "jwtAuthenticationMechanism".equals(n));

        this.name = argOrDefault(config, "name", "rh_auth");
        this.secure = argOrDefault(config, "secure", true);
        this.domain = argOrDefault(config, "domain", "localhost");
        this.path = argOrDefault(config, "path", "/");
        this.httpOnly = argOrDefault(config, "http-only", true);
        this.sameSite = argOrDefault(config, "same-site", true);
        this.sameSiteMode = argOrDefault(config, "same-site-mode", "strict");
        this.secondsUntilExpiration = argOrDefault(config, "expires-ttl", 24*60*60); // default 1 day
    }

    @Override
    public void handle(ServiceRequest<?> req, ServiceResponse<?> res) throws Exception {
        var authTokenHeader = res.getHeader("Auth-Token");

        // if the token is issued by jwtTokenManager and the jwtAuthenticationMechanism is enabled
        // use JWT authentication (i.e. Bearer...)
        // otherwise rely on tokenBasicAuthMechanism (i.e. Basic...)
        var authToken = jwtAuthWithJwtAuthMechanism
            ? "Bearer ".concat(authTokenHeader)
            : "Basic ".concat(Base64.getEncoder().encodeToString((req.getAuthenticatedAccount().getPrincipal().getName() + ":" + authTokenHeader).getBytes()));

        var expiry = LocalDateTime.now()
            .plusSeconds(this.secondsUntilExpiration)
            .toInstant(ZoneOffset.UTC);

        res.getExchange().setResponseCookie(new CookieImpl(this.name, authToken)
            .setSecure(this.secure)
            .setHttpOnly(this.httpOnly)
            .setDomain(this.domain)
            .setPath(this.path)
            .setSameSite(this.sameSite)
            .setSameSiteMode(this.sameSiteMode)
            .setExpires(Date.from(expiry)));
    }

    @Override
    public boolean resolve(ServiceRequest<?> req, ServiceResponse<?> res) {
        return this.enabled && !req.isOptions() && req.isAuthenticated() && req.getQueryParameters().containsKey("set-auth-cookie") && res.getHeader("Auth-Token") != null;
    }
}
