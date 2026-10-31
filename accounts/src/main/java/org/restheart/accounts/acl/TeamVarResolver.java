/*-
 * ========================LICENSE_START=================================
 * restheart-accounts
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
package org.restheart.accounts.acl;

import org.bson.BsonDocument;
import org.bson.BsonNull;
import org.bson.BsonValue;
import org.restheart.accounts.AccountsService;
import org.restheart.exchange.Request;
import org.restheart.plugins.accounts.TeamClaim;
import org.restheart.security.VarResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The caller's active team, as {@code @team} (or whatever {@code team-claim-name} says): the
 * {@code { _id, role }} document the accounts endpoints put in the JWT, built here from
 * {@code MembershipProvider.activeMembership} — never read from the token, nor from whatever
 * the provider stores on the user document.
 *
 * <p>It serves twice:
 * <ul>
 * <li>in ACL rules, as {@code @team._id} and {@code @team.role}: a {@code readFilter} on
 * {@code @team._id} returns the same documents whether the caller logged in with a password or
 * through OAuth, and whatever shape its token carries;</li>
 * <li>in tokens, through {@code jwtConfigProvider/account-properties-claims: ["@team"]}: {@code
 * POST /token} then issues the same claim {@code POST /auth/switch-team} does, with the default
 * provider and with one that stores a bare team id (#765).</li>
 * </ul>
 *
 * <h2>Failure semantics</h2>
 * <p>No authenticated account, no active membership, or any failure resolving it answers
 * {@link BsonNull#VALUE}: unresolved, which denies rather than grants in a rule and adds no claim
 * to a token. This resolver never throws.
 *
 * <h2>Cost</h2>
 * <p>The membership is read once per request and kept on it, whichever of {@code @team},
 * {@code @team._id} and {@code @team.role} a rule names, since the framework memoizes per exact
 * expression only. It is not kept across requests: a switch of team must be seen by the very next
 * request, and the read is one user document by its id.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 9.12.0
 */
public class TeamVarResolver implements VarResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(TeamVarResolver.class);

    private static final String CACHE_KEY = "__accounts_team_var_cache";

    private final AccountsService accountsService;
    private final String name;

    /**
     * @param accountsService where the membership provider of a request comes from
     * @param name            the variable's name, without {@code @}: {@code team-claim-name}, so
     *                        that the claim and the variable are called the same
     */
    public TeamVarResolver(AccountsService accountsService, String name) {
        this.accountsService = accountsService;
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public BsonValue resolve(Request<?> request, String var) {
        if (request == null || var == null) {
            return BsonNull.VALUE;
        }

        try {
            var team = cached(request);

            if (team == null) {
                return BsonNull.VALUE;
            }

            var prefix = "@" + name;

            if (prefix.equals(var)) {
                return team;
            }

            if (var.startsWith(prefix + ".")) {
                return navigate(team, var.substring(prefix.length() + 1));
            }

            return BsonNull.VALUE;
        } catch (Exception e) {
            // a broken resolver must deny, never grant: see VarResolver's failure semantics
            LOGGER.warn("@{} resolution failed for {}: {}", name, var, e.toString());
            return BsonNull.VALUE;
        }
    }

    private BsonDocument cached(Request<?> request) {
        Object cached = request.attachedParam(CACHE_KEY);

        if (cached instanceof BsonDocument d) {
            return d;
        }

        if (cached instanceof NotResolved) {
            return null;
        }

        var team = build(request);
        request.attachParam(CACHE_KEY, team != null ? team : NotResolved.INSTANCE);
        return team;
    }

    private BsonDocument build(Request<?> request) {
        var account = request.getAuthenticatedAccount();

        if (account == null || account.getPrincipal() == null || account.getPrincipal().getName() == null) {
            return null;
        }

        return accountsService.getMembershipProvider(request)
                .activeMembership(account.getPrincipal().getName())
                .map(m -> TeamClaim.of(m.teamId(), m.role()))
                .orElse(null);
    }

    private static BsonValue navigate(BsonDocument doc, String dottedPath) {
        BsonValue current = doc;

        for (var segment : dottedPath.split("\\.")) {
            if (!(current instanceof BsonDocument d) || !d.containsKey(segment)) {
                return BsonNull.VALUE;
            }
            current = d.get(segment);
        }

        return current;
    }

    /** Kept on the request when the caller resolves to no team, so the lookup is not repeated. */
    private enum NotResolved {
        INSTANCE
    }
}
