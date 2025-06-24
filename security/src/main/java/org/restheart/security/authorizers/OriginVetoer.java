/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2024 SoftInstigate
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
    }

    @Override
    public boolean isAllowed(final Request<?> request) {
        if (ignoreLists != null && ignoreLists.match(request.getPath()) != null) {
            LOGGER.debug("originVetoer: request is accepted since path is in ignore list");
            return true;
        }

        // If neither whitelist nor patterns are defined, allow all
        if ((this.whitelist == null || this.whitelist.isEmpty()) &&
                (this.whitelistPatterns == null || this.whitelistPatterns.isEmpty())) {
            return true;
        }

        final var origin = request.getHeader("Origin");
        if (origin == null) {
            LOGGER.warn("request forbidden by originVetoer due to missing Origin header");
            return false;
        }

        final var normalizedOrigin = removeTrailingSlashes(origin.toLowerCase()).concat("/");

        // Check exact/prefix matches first
        if (this.whitelist != null && !this.whitelist.isEmpty()) {
            final boolean exactMatch = this.whitelist.stream().anyMatch(wl -> normalizedOrigin.startsWith(wl));
            if (exactMatch) {
                return true;
            }
        }

        // Check pattern matches
        if (this.whitelistPatterns != null && !this.whitelistPatterns.isEmpty()) {
            final boolean patternMatch = this.whitelistPatterns.stream()
                    .anyMatch(pattern -> matchesPattern(origin, pattern));
            if (patternMatch) {
                return true;
            }
        }

        LOGGER.warn("request forbidden by originVetoer due to Origin header {} not in whitelist or patterns", origin);
        return false;
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
