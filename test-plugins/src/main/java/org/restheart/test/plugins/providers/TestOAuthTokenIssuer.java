package org.restheart.test.plugins.providers;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.restheart.exchange.Request;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.OAuthTokenIssuer;

import io.undertow.security.idm.Account;

/**
 * Stands in for a deployment that issues its own token through the Authorization Code flow
 * (restheart#740): offers a role and a number of days, seals them in the code, and mints an
 * opaque string from them at {@code /token}.
 *
 * <p>Acts only on requests carrying {@code ?ti=…}, and stays out of every other one, so the
 * OAuth scenarios that predate it keep today's behaviour in the same suite. {@code ti=refuse}
 * refuses the account outright, which is how the page's third answer is exercised.
 *
 * <p>The token is {@code test-token:<role>:<days>}, deliberately not a credential anything on
 * the instance accepts: what is under test is that the choice sealed at {@code /authorize} is
 * what {@code /token} mints, not what the token opens.
 */
@RegisterPlugin(
        name = "testOAuthTokenIssuer",
        description = "Issues test-token:<role>:<days> through the Authorization Code flow when ?ti is present",
        enabledByDefault = false)
public class TestOAuthTokenIssuer implements Provider<OAuthTokenIssuer> {

    static final String QPARAM = "ti";
    static final List<String> ROLES = List.of("reader", "writer");
    static final int MAX_DAYS = 365;
    static final int DEFAULT_DAYS = 30;

    @Override
    public OAuthTokenIssuer get(PluginRecord<?> caller) {
        return new Issuer();
    }

    private static final class Issuer implements OAuthTokenIssuer {

        @Override
        public Optional<Offer> offer(Account account, Request<?> request) throws Refusal {
            var mode = mode(request);

            if (mode == null) {
                return Optional.empty();
            }

            refuseIfAsked(mode);

            return Optional.of(new Offer("Test issuer", "Choose the token for " + account.getPrincipal().getName(), List.of(
                    Choice.select("role", "Role", ROLES.stream().map(r -> new Option(r, r)).toList(), ROLES.get(0)),
                    Choice.number("days", "Valid for (days)", 1, MAX_DAYS, DEFAULT_DAYS))));
        }

        @Override
        public Map<String, Object> claims(Account account, Request<?> request, Map<String, String> choice) throws Refusal {
            var mode = mode(request);

            if (mode == null) {
                return Map.of();
            }

            refuseIfAsked(mode);

            var role = choice.getOrDefault("role", ROLES.get(0));

            if (!ROLES.contains(role)) {
                throw new Refusal("invalid_scope", "role '" + role + "' is not offered");
            }

            int days;

            try {
                days = Integer.parseInt(choice.getOrDefault("days", Integer.toString(DEFAULT_DAYS)));
            } catch (NumberFormatException e) {
                throw new Refusal("invalid_request", "days must be a whole number");
            }

            if (days < 1 || days > MAX_DAYS) {
                throw new Refusal("invalid_request", "days must be between 1 and " + MAX_DAYS);
            }

            return Map.of("role", role, "days", days);
        }

        @Override
        public IssuedToken issue(Map<String, Object> claims, Account account, Request<?> request) throws Refusal {
            var role = String.valueOf(claims.get("role"));
            var days = claims.get("days") instanceof Number n ? n.intValue() : DEFAULT_DAYS;

            return IssuedToken.bearer("test-token:" + role + ":" + days, days * 86_400L);
        }

        private static String mode(Request<?> request) {
            var params = request.getExchange().getQueryParameters();

            return params.containsKey(QPARAM) ? params.get(QPARAM).peekFirst() : null;
        }

        private static void refuseIfAsked(String mode) throws Refusal {
            if ("refuse".equals(mode)) {
                throw new Refusal("access_denied", "this account may not obtain a token here");
            }
        }
    }
}
