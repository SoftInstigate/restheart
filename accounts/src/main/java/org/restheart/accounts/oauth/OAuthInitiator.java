package org.restheart.accounts.oauth;

import io.undertow.util.Headers;
import org.restheart.exchange.StringRequest;
import org.restheart.exchange.StringResponse;
import org.restheart.exchange.ExchangeKeys.METHOD;
import org.restheart.exchange.Request;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.StringService;
import org.restheart.security.ACLRegistry;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Predicate;

import static java.util.function.Predicate.not;

/**
 * Initiates the Google OAuth 2.0 authorization flow.
 *
 * <pre>
 *   GET /auth/oauth/authorize/google
 *       → 302 to Google's consent screen
 * </pre>
 *
 * The endpoint is public (no session required).
 */
@RegisterPlugin(
        name             = "oauthInitiator",
        description      = "GET /auth/oauth/authorize/{provider} — starts the OAuth flow",
        defaultURI       = "/auth/oauth/authorize",
        enabledByDefault = true)
public class OAuthInitiator implements StringService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OAuthInitiator.class);

    @Inject("oauthService")
    private OAuthService oauthService;

    @Inject("oauthConfig")
    private OAuthConfig oauthConfig;

    @Inject("acl-registry")
    private ACLRegistry aclRegistry;

    @OnInit
    public void onInit() {
        Predicate<Request<?>> isOAuthInitiate = r ->
                r.getMethod() == METHOD.GET &&
                r.getPath().matches("/auth/oauth/authorize/[^/]+");

        aclRegistry.registerAuthenticationRequirement(not(isOAuthInitiate));
        aclRegistry.registerAllow(isOAuthInitiate);

        LOGGER.info("OAuthInitiator initialized at /auth/oauth/authorize/{{provider}}");
    }

    @Override
    public void handle(StringRequest req, StringResponse res) throws Exception {
        if (req.isOptions()) { handleOptions(req); return; }

        if (!req.isGet()) {
            res.setInError(HttpStatus.SC_METHOD_NOT_ALLOWED, "Use GET");
            return;
        }

        // Extract provider from path: /auth/oauth/authorize/{provider}
        var parts = req.getPath().split("/");
        if (parts.length < 5) {
            res.setInError(HttpStatus.SC_BAD_REQUEST, "Missing provider in path");
            return;
        }
        var provider = parts[4].toLowerCase();

        if (!oauthConfig.isProviderEnabled(provider)) {
            res.setInError(HttpStatus.SC_BAD_REQUEST, "Provider '" + provider + "' is not enabled");
            return;
        }

        try {
            var result = oauthService.getAuthorizationUrl(provider, req);
            LOGGER.info("OAuth authorize → redirecting to {} consent screen", provider);
            res.setStatusCode(HttpStatus.SC_TEMPORARY_REDIRECT);
            res.getHeaders().put(Headers.LOCATION, result.url());
        } catch (OAuthService.OAuthException e) {
            LOGGER.warn("OAuth authorize error for {}: {}", provider, e.getMessage());
            var errorUrl = oauthConfig.frontendErrorUrl() + "&reason="
                    + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
            res.setStatusCode(HttpStatus.SC_TEMPORARY_REDIRECT);
            res.getHeaders().put(Headers.LOCATION, errorUrl);
        }
    }
}
