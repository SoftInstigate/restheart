package org.restheart.security.authenticators;

import java.util.Map;

import org.restheart.configuration.Configuration;
import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.OnInit;
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

    private String authCookieName;

    @OnInit
    public void init() {
        if (rhConfig.toMap().containsKey("authCookieSetter")) {
            var authCookieSetterConf = (Map<String, Object>) rhConfig.toMap().get("authCookieSetter");

            this.authCookieName = argOrDefault(authCookieSetterConf, "name", "rh_auth");
        } else {
            this.authCookieName = "rh_auth";
        }
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
            LOGGER.debug("no access_token cookie");
        } else {
            var authorizationHeader = accessToken.getValue();
            req.setHeader("Authorization", authorizationHeader);
            LOGGER.debug("set header Authorization: {}", authorizationHeader);
        }
    }

    @Override
    public boolean resolve(ServiceRequest<?> req, ServiceResponse<?> res) {
        return req.getHeader("Authorization") == null;
    }
}
