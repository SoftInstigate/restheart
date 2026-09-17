/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2026 SoftInstigate
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
package org.restheart.security.services;

import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.security.OAuthTokenIssuer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds the deployment's {@link OAuthTokenIssuer}, and reads the user's choice off a request.
 *
 * <p>Shared by {@code oauthAuthorizationService} and {@code authTokenService}: both have to agree
 * on which issuer is active and on how a choice is spelled, and neither should own that for the
 * other.
 */
final class OAuthTokenIssuers {

    private static final Logger LOGGER = LoggerFactory.getLogger(OAuthTokenIssuers.class);

    /**
     * The claim of the authorization code that carries what the issuer sealed at
     * {@code /authorize}. Its presence is what tells {@code /token} that the issuer, and only the
     * issuer, answers this code.
     */
    static final String CODE_CLAIM = "ti";

    /** Prefix of the query parameters that carry a choice on {@code POST /authorize}. */
    static final String CHOICE_PARAM_PREFIX = "choice.";

    private OAuthTokenIssuers() {
    }

    /**
     * The registered issuer, found by the type it provides rather than by an agreed name, as
     * {@code mcpService} finds its scope provider; empty when none is registered.
     *
     * <p>Resolved on demand and not at {@code @OnInit}: providers may initialise after the service
     * that asks for them.
     */
    static Optional<OAuthTokenIssuer> discover(PluginsRegistry registry, Object self) {
        PluginRecord<?> selfRecord = registry.getServices().stream()
                .filter(r -> r.getInstance() == self)
                .findFirst()
                .orElse(null);

        for (var record : registry.getProviders()) {
            var instance = record.getInstance();

            if (!OAuthTokenIssuer.class.isAssignableFrom(instance.rawType())) {
                continue;
            }

            if (instance.get(selfRecord) instanceof OAuthTokenIssuer issuer) {
                return Optional.of(issuer);
            }

            LOGGER.warn("Provider '{}' declares OAuthTokenIssuer but supplied nothing — the default token is issued", record.getName());
        }

        return Optional.empty();
    }

    /**
     * The user's choice: what {@code scope} preselects, overridden by what the page sent.
     *
     * <p>{@code scope} is the one standard way a client has to say what it wants, so an entry of
     * the form {@code <name>:<value>} counts as a choice — until the page, which is where a person
     * decides, sends {@code choice.<name>} for the same name. A scope entry without a colon is not
     * a choice and is left to the issuer to read from the request, if it cares.
     */
    static Map<String, String> choiceOf(Map<String, Deque<String>> queryParameters) {
        var choice = new LinkedHashMap<String, String>();

        var scope = first(queryParameters, "scope");

        if (scope != null) {
            for (var entry : scope.trim().split("\\s+")) {
                var colon = entry.indexOf(':');

                if (colon > 0 && colon < entry.length() - 1) {
                    choice.put(entry.substring(0, colon), entry.substring(colon + 1));
                }
            }
        }

        queryParameters.forEach((name, values) -> {
            if (name.startsWith(CHOICE_PARAM_PREFIX) && name.length() > CHOICE_PARAM_PREFIX.length()
                    && values != null && values.peekFirst() != null) {
                choice.put(name.substring(CHOICE_PARAM_PREFIX.length()), values.peekFirst());
            }
        });

        return choice;
    }

    private static String first(Map<String, Deque<String>> params, String name) {
        var deque = params.get(name);
        return deque != null ? deque.peekFirst() : null;
    }
}
