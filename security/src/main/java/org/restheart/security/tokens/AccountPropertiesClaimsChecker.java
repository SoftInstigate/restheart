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
package org.restheart.security.tokens;

import java.util.ArrayList;
import java.util.List;

import org.restheart.configuration.ConfigurationException;
import org.restheart.plugins.InitPoint;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.Inject;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.AclVarsInterpolator;

/**
 * Checks, once every plugin has registered its ACL variables, that each {@code @} entry of
 * {@code jwtConfigProvider/account-properties-claims} and
 * {@code required-account-properties-claims} names one of them, and is the bare variable.
 *
 * <p>An entry can only be checked here: a {@link org.restheart.security.VarResolver} is
 * registered by the plugin that owns it, {@code "@team"} by restheart-accounts in an
 * {@code AFTER_STARTUP} initializer, long after {@link JwtConfigProvider} has read the list. So
 * this runs last among those initializers, and a bad entry is reported in the startup log, with
 * the configuration in hand, instead of as a claim that silently never appears in any token.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 9.12.0
 */
@RegisterPlugin(
        name = "accountPropertiesClaimsChecker",
        description = "checks that the @ entries of jwtConfigProvider/account-properties-claims name registered ACL variables",
        initPoint = InitPoint.AFTER_STARTUP,
        // after every initializer that registers a VarResolver, whatever its own priority
        priority = Integer.MAX_VALUE)
public class AccountPropertiesClaimsChecker implements Initializer {
    @Inject("jwtConfigProvider")
    private JwtConfigProvider.JwtConfig jwtConfig;

    @Override
    public void init() {
        var problems = new ArrayList<String>();

        check("account-properties-claims", jwtConfig.accountPropertiesClaims(), problems);
        check("required-account-properties-claims", jwtConfig.requiredAccountPropertiesClaims(), problems);

        if (!problems.isEmpty()) {
            throw new ConfigurationException(String.join("; ", problems));
        }
    }

    private static void check(String key, List<String> entries, List<String> problems) {
        if (entries == null) {
            return;
        }

        for (var entry : entries) {
            if (!DefaultJwtIssuer.isVariableClaim(entry)) {
                continue;
            }

            var name = DefaultJwtIssuer.variableClaimName(entry);

            if (!("@" + name).equals(entry)) {
                problems.add("jwtConfigProvider/" + key + ": \"" + entry + "\" must be the bare variable \"@"
                        + name + "\", a claim carries the whole of it");
            } else if (!AclVarsInterpolator.isRegisteredVar(entry)) {
                problems.add("jwtConfigProvider/" + key + ": \"" + entry + "\" names no registered ACL variable; "
                        + "enable the plugin that registers @" + name + " (restheart-accounts for @team) or remove the entry");
            }
        }
    }
}
