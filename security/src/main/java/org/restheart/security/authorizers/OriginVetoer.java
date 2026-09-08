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
package org.restheart.security.authorizers;

import static org.restheart.utils.URLUtils.removeTrailingSlashes;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.restheart.configuration.ConfigurationException;
import org.restheart.exchange.Request;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.Authorizer;
import org.restheart.plugins.security.Authorizer.TYPE;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.util.PathTemplate;
import io.undertow.util.PathTemplateMatcher;

@RegisterPlugin(
        name = "originVetoer",
        description = "protects from CSRF attacks by forbidding requests whose Origin header is not whitelisted",
        enabledByDefault = false,
        authorizerType = TYPE.VETOER)
public class OriginVetoer implements Authorizer {
    private static final Logger LOGGER = LoggerFactory.getLogger(OriginVetoer.class);

    private List<String> whitelist = null;
    private List<String> whitelistPatterns = null;
    private boolean allowMissingOrigin = false;
    private PathTemplateMatcher<Boolean> ignoreLists = new PathTemplateMatcher<>();

    @Inject("config")
    private Map<String, Object> config;

    @OnInit
    public void init() {
        try {
            final List<String> _whitelist = arg(config, "whitelist");
            this.whitelist = _whitelist.stream()
                    .filter(item -> item != null)
                    .map(item -> item.strip())
                    .map(item -> item.toLowerCase())
                    .map(item -> removeTrailingSlashes(item))
                    .map(item -> item.concat("/"))
                    .collect(Collectors.toList());

            LOGGER.info("whitelist defined for originVetoer, requests will be accepted with Origin header in {}",
                    this.whitelist);
        } catch (final ConfigurationException ce) {
            this.whitelist = null;
            LOGGER.info("No whitelist defined for originVetoer, all Origin headers are accepted");
        }

        // New: Support for origin patterns
        try {
            final List<String> _whitelistPatterns = arg(config, "whitelist-patterns");
            this.whitelistPatterns = _whitelistPatterns.stream()
                    .filter(item -> item != null)
                    .map(item -> item.strip())
                    .map(item -> item.toLowerCase())
                    .collect(Collectors.toList());

            LOGGER.info(
                    "whitelist patterns defined for originVetoer, requests will be accepted with Origin header matching patterns {}",
                    this.whitelistPatterns);
        } catch (final ConfigurationException ce) {
            this.whitelistPatterns = null;
            LOGGER.info("No whitelist patterns defined for originVetoer");
        }

        try {
            final List<String> _ingoreList = arg(config, "ignore-paths");
            _ingoreList.stream()
                    .filter(item -> item != null)
                    .map(item -> item.strip())
                    .map(item -> item.toLowerCase())
                    .map(item -> PathTemplate.create(item))
                    .forEach(item -> this.ignoreLists.add(item, true));

            LOGGER.info(
                    "ignore list defined for originVetoer, requests will be accepted without checking the Origin header for paths in {}",
                    _ingoreList);
        } catch (final ConfigurationException ce) {
            this.ignoreLists = null;
            LOGGER.info("No ignoreLists defined for originVetoer, all paths are checked");
        }

        this.allowMissingOrigin = argOrDefault(config, "allow-missing-origin", true);
        if (this.allowMissingOrigin) {
            LOGGER.info("allow-missing-origin enabled for originVetoer, requests without Origin header are allowed");
        }
    }

    @Override
    public boolean isAllowed(final Request<?> request) {
        // Allow genuine CORS preflight through — the service handles CORS headers.
        // A preflight is issued by the browser and carries no credentials, so it
        // can't be a CSRF vector; the actual request (POST, GET, etc.) is checked
        // instead. Vetoing it would be pointless anyway: browsers fail a preflight
        // whose status is not 2xx, no matter which CORS headers it carries.
        // Plain OPTIONS requests (no Origin, no Access-Control-Request-Method) are
        // not preflights and stay subject to the check.
        if (request.isOptions()
                && request.getHeader("Origin") != null
                && request.getHeader("Access-Control-Request-Method") != null) {
            LOGGER.debug("originVetoer: CORS preflight accepted without checking the Origin header");
            return true;
        }

        if (ignoreLists != null && ignoreLists.match(request.getPath()) != null) {
            LOGGER.debug("originVetoer: request is accepted since path is in ignore list");
            return true;
        }

        // A per-request override replaces the static whitelist entirely for
        // this request (e.g. multi-tenant deployments resolving a
        // per-service whitelist via an interceptor). Absent → static config,
        // as before. Present but empty → deny all (fail closed), distinct
        // from "no override" (null).
        final List<String> overrideWhitelist = request.attachedParam("override-origin-whitelist");
        final var hasOverride = overrideWhitelist != null;
        final var effectiveWhitelist = hasOverride ? normalize(overrideWhitelist) : this.whitelist;

        // If neither whitelist nor patterns are defined, allow all —
        // but only when there's no override in play; an override always
        // means the caller wants requests validated against it.
        if (!hasOverride
                && (this.whitelist == null || this.whitelist.isEmpty())
                && (this.whitelistPatterns == null || this.whitelistPatterns.isEmpty())) {
            return true;
        }

        final var origin = request.getHeader("Origin");
        if (origin == null) {
            // per-request override takes precedence over static config
            final Boolean overrideAllowMissing = request.attachedParam("override-origin-allow-missing");
            final var effectiveAllowMissing = overrideAllowMissing != null ? overrideAllowMissing : this.allowMissingOrigin;

            if (effectiveAllowMissing) {
                LOGGER.debug("originVetoer: request allowed despite missing Origin header (allow-missing-origin)");
                return true;
            }

            LOGGER.debug("originVetoer: request denied due to missing Origin header");
            request.attachParam(Authorizer.VETO_MESSAGE, "Origin header is required");
            return false;
        }

        final var normalizedOrigin = removeTrailingSlashes(origin.toLowerCase()).concat("/");

        // Check exact/prefix matches first
        if (effectiveWhitelist != null && !effectiveWhitelist.isEmpty()) {
            final boolean exactMatch = effectiveWhitelist.stream().anyMatch(wl -> normalizedOrigin.startsWith(wl));
            if (exactMatch) {
                return true;
            }
        }

        // Pattern matches are only consulted from static config — an
        // override, when present, is exact-match only (see class docs).
        if (!hasOverride && this.whitelistPatterns != null && !this.whitelistPatterns.isEmpty()) {
            final boolean patternMatch = this.whitelistPatterns.stream()
                    .anyMatch(pattern -> matchesPattern(origin, pattern));
            if (patternMatch) {
                return true;
            }
        }

        LOGGER.debug("originVetoer: request denied due to Origin header {} not in whitelist or patterns", origin);
        request.attachParam(Authorizer.VETO_MESSAGE, "Origin " + origin + " not allowed");
        return false;
    }

    /** Applies the same normalization used for the static whitelist at init time. */
    private static List<String> normalize(final List<String> origins) {
        return origins.stream()
                .filter(item -> item != null)
                .map(String::strip)
                .map(String::toLowerCase)
                .map(item -> removeTrailingSlashes(item))
                .map(item -> item.concat("/"))
                .collect(Collectors.toList());
    }

    private boolean matchesPattern(final String origin, final String pattern) {
        // Convert glob-like patterns to regex
        final String regex = pattern
                .replace(".", "\\.") // Escape dots
                .replace("*", ".*"); // Convert * to .*

        try {
            return origin.toLowerCase().matches(regex);
        } catch (final Exception e) {
            LOGGER.warn("Invalid pattern '{}': {}", pattern, e.getMessage());
            return false;
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public boolean isAuthenticationRequired(final Request request) {
        return false;
    }
}
