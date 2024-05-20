package org.restheart.security.authenticators;

import java.util.Map;

import org.restheart.configuration.Configuration;
import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.WildcardInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.server.handlers.Cookie;

/**
 * sets the Authorization headers from the auth cookie
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RegisterPlugin(name = "authCookieHandler",
                description = "sets the Authorization header from the auth cookie",
                interceptPoint = InterceptPoint.REQUEST_BEFORE_AUTH)
public class AuthCookieHandler implements WildcardInterceptor {
    static final Logger LOGGER = LoggerFactory.getLogger(AuthCookieHandler.class);

    @Inject("rh-config")
    private Configuration rhConfig;

    @Inject("registry")
    PluginsRegistry pluginsRegistry;

    private String authCookieName;

    private boolean enabled = true;

    @OnInit
    @SuppressWarnings("unchecked")
    public void init() {
        this.enabled = enabled(pluginsRegistry, false);

        if (rhConfig.toMap().containsKey("authCookieSetter")) {
            var authCookieSetterConf = (Map<String, Object>) rhConfig.toMap().get("authCookieSetter");

            this.authCookieName = argOrDefault(authCookieSetterConf, "name", "rh_auth");
        } else {
            this.authCookieName = "rh_auth";
        }
    }

    static boolean enabled(PluginsRegistry pluginsRegistry, boolean silent) {
        var tokenManager = pluginsRegistry.getTokenManager();

        var rndTokenManagerEnabled = tokenManager != null && "rndTokenManager".equals(tokenManager.getName());
        var jwtTokenManagerEnabled = tokenManager != null && "jwtTokenManager".equals(tokenManager.getName());

        if (!rndTokenManagerEnabled && !jwtTokenManagerEnabled) {
            if (!silent) LOGGER.warn("Cookie Authentication is disabled because it requires either rndTokenManager or jwtTokenManager. Please enable one of these token managers to use Cookie Authentication.");
            return false;
        }

        var tokenBasicAuthMechanismEnabled = pluginsRegistry.getAuthMechanisms().stream().map(pr -> pr.getName()).anyMatch(n -> "tokenBasicAuthMechanism".equals(n));
        var jwtAuthenticationMechanismEnabled = pluginsRegistry.getAuthMechanisms().stream().map(pr -> pr.getName()).anyMatch(n -> "jwtAuthenticationMechanism".equals(n));

        if (rndTokenManagerEnabled && !tokenBasicAuthMechanismEnabled) {
            if (!silent) LOGGER.warn("Cookie Authentication is disabled because the rndTokenManager requires tokenBasicAuthMechanism. Please enable tokenBasicAuthMechanism to use Cookie Authentication with rndTokenManager.");
            return false;
        } else if (jwtTokenManagerEnabled && !tokenBasicAuthMechanismEnabled && !jwtAuthenticationMechanismEnabled) {
            if (!silent) LOGGER.warn("Cookie Authentication is disabled because the jwtTokenManager requires either tokenBasicAuthMechanism or jwtAuthenticationMechanism. Please enable one of these authentication mechanisms to use Cookie Authentication.");
            return false;
        }

        return true;
    }

    @Override
    public void handle(ServiceRequest<?> req, ServiceResponse<?> res) throws Exception {
        final Cookie accessToken;
        try {
            accessToken = req.getCookie(this.authCookieName);
        } catch(Throwable t) {
            LOGGER.error("wrong cookie", t);
            return;
        }

        if (accessToken == null) {
            LOGGER.debug("no {} cookie", this.authCookieName);
        } else {
            var authorizationHeader = accessToken.getValue();
            req.setHeader("Authorization", authorizationHeader);
            LOGGER.debug("set header Authorization: {}", authorizationHeader);
        }
    }

    @Override
    public boolean resolve(ServiceRequest<?> req, ServiceResponse<?> res) {
        return this.enabled && req.getHeader("Authorization") == null;
    }
}
