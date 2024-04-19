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
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.WildcardInterceptor;
import org.restheart.security.FileRealmAccount;
import org.restheart.security.JwtAccount;
import org.restheart.security.MongoRealmAccount;

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
        this.name = argOrDefault(config, "name", "rh_auth");
        this.secure = argOrDefault(config, "secure", true);
        this.domain = argOrDefault(config, "domain", "localhost");
        this.path = argOrDefault(config, "path", "/");
        this.httpOnly = argOrDefault(config, "http-only", true);
        this.sameSite = argOrDefault(config, "same-site", true);
        this.sameSiteMode = argOrDefault(config, "same-site-mode", "strict");
        this.secondsUntilExpiration = argOrDefault(config, "exprires-ttl", 24*60*60); // default 1 day
    }

    @Override
    public void handle(ServiceRequest<?> req, ServiceResponse<?> res) throws Exception {
        var authTokenHeader = res.getHeader("Auth-Token");

        if (authTokenHeader == null) {
            return;
        }

        var authToken = switch(req.getAuthenticatedAccount()) {
            case JwtAccount jwt -> "Bearer " + authTokenHeader;
            case FileRealmAccount fra -> "Basic " + Base64.getEncoder().encodeToString((fra.getPrincipal().getName() + ":" + authTokenHeader).getBytes());
            case MongoRealmAccount mra -> "Basic " + Base64.getEncoder().encodeToString((mra.getPrincipal().getName() + ":" + authTokenHeader).getBytes());
            default -> authTokenHeader;
        };

        if (authToken != null) {
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
    }

    @Override
    public boolean resolve(ServiceRequest<?> req, ServiceResponse<?> res) {
        return !req.isOptions() && req.isAuthenticated() && req.getQueryParameters().containsKey("set-auth-cookie");
    }
}
