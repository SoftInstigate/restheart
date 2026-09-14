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
package org.restheart.security.authenticators;

import java.util.List;

import org.restheart.configuration.ConfigurationException;
import org.restheart.plugins.Inject;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.PasswordPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nulabinc.zxcvbn.Zxcvbn;

/**
 * Serves the one {@link PasswordPolicy} of this deployment, taken from
 * {@code mongoRealmAuthenticator}.
 *
 * <p>From the authenticator's own instance rather than from the configuration file: those are its
 * effective values, so nobody can end up requiring one strength while it applies another. When the
 * authenticator is absent or disabled — a deployment authenticating some other way — the policy
 * falls back to RESTHeart's documented defaults, and enforcement is off, which is what a component
 * that was never configured should do.
 */
@RegisterPlugin(
    name = "passwordPolicy",
    description = "Password strength requirement and BCrypt cost, as configured on mongoRealmAuthenticator")
public class MongoRealmPasswordPolicy implements Provider<PasswordPolicy> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoRealmPasswordPolicy.class);

    private static final Zxcvbn ZXCVBN = new Zxcvbn();

    /** RESTHeart's defaults, see restheart-default-config.yml. */
    private static final int DEFAULT_MINIMUM_STRENGTH = 3;
    private static final int DEFAULT_BCRYPT_COMPLEXITY = 12;

    @Inject("registry")
    private PluginsRegistry registry;

    private volatile PasswordPolicy policy;

    // Built on first use rather than in an @OnInit, so that it cannot matter whether this provider
    // happens to be initialised before or after whoever asks it for the policy.
    @Override
    public PasswordPolicy get(PluginRecord<?> caller) {
        var p = policy;

        if (p == null) {
            synchronized (this) {
                p = policy;
                if (p == null) {
                    policy = p = build();
                }
            }
        }

        return p;
    }

    private PasswordPolicy build() {
        var mra = mongoRealmAuthenticator();

        if (mra == null) {
            LOGGER.debug("mongoRealmAuthenticator is not available: password strength is not enforced");
            return new Policy(false, DEFAULT_MINIMUM_STRENGTH, DEFAULT_BCRYPT_COMPLEXITY);
        }

        return new Policy(mra.isEnforceMinimumPasswordStrength(), mra.getMinimumPasswordStrength(), mra.getBcryptComplexity());
    }

    private MongoRealmAuthenticator mongoRealmAuthenticator() {
        try {
            var pr = registry.getAuthenticator("mongoRealmAuthenticator");
            return pr == null || !pr.isEnabled() ? null : (MongoRealmAuthenticator) pr.getInstance();
        } catch (ConfigurationException ce) {
            return null;
        }
    }

    private record Policy(boolean enforced, int minimumStrength, int bcryptComplexity) implements PasswordPolicy {
        @Override
        public Weakness weaknessOf(String password) {
            if (!enforced || password == null) {
                return null;
            }

            var measure = ZXCVBN.measure(password);

            if (measure.getScore() >= minimumStrength) {
                return null;
            }

            var feedback = measure.getFeedback();

            return new Weakness(
                    feedback == null ? null : feedback.getWarning(),
                    feedback == null || feedback.getSuggestions() == null ? List.of() : feedback.getSuggestions());
        }
    }
}
