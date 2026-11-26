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

import java.util.List;

import org.bson.BsonArray;
import org.bson.BsonNull;
import org.bson.BsonValue;
import org.restheart.accounts.AccountsService;
import org.restheart.exchange.Request;
import org.restheart.plugins.accounts.Membership;
import org.restheart.plugins.accounts.TeamClaim;
import org.restheart.security.VarResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * All the caller's teams, as {@code @teams} (or {@code team-claim-name} followed by {@code s}):
 * one {@code { _id, role }} for each membership, from {@code MembershipProvider.listMemberships}.
 *
 * <p>{@code @team} is the active team, the one the user last switched to. It fits a person in a
 * browser; it does not fit a credential that should act in every team of its user, as a GitHub
 * token acts in every organization of its owner. With {@code @teams} the team is kept in the
 * document and checked against all the caller's teams (#832):
 *
 * <ul>
 * <li>{@code @teams}: the array of {@code { _id, role }};</li>
 * <li>{@code @teams._id}: the array of the ids of all the caller's teams;</li>
 * <li>{@code @teams.<role>}: the array of the ids of the teams where the caller has that role,
 * e.g. {@code @teams.owner}. {@code _id} is not a role name, so the two forms never collide.</li>
 * </ul>
 *
 * <pre>{@code
 * # reads and changes: the documents of the caller's teams, of those it owns
 * { "readFilter":  { "team": { "$in": "@teams._id" } },
 *   "writeFilter": { "team": { "$in": "@teams.owner" } } }
 *
 * # an insert, which no filter covers: the team in the body must be one the caller owns
 * in(value=@request.body.team, array=@teams.owner)
 * }</pre>
 *
 * <p>The predicate needs nothing new: {@code @request.body.team} and an array variable are both
 * written into a predicate as literals, an ObjectId as its hex string on both sides. A body that
 * is an array leaves {@code @request.body} unbound, so a bulk insert is refused by that rule.
 *
 * <h2>Failure semantics</h2>
 * <p>No authenticated account, a user in no team, or a failure reading the memberships resolves
 * to an empty array: in a filter {@code $in: []} matches nothing, in a predicate
 * {@code in(..., array={})} is false. An empty array rather than an unresolved value because an
 * unresolved one becomes a random token, and {@code $in} of a string is an error, not a denial. A
 * failure is not kept on the request, so the next request reads again. An expression this
 * resolver does not know, such as {@code @teams.owner.x}, answers {@link BsonNull#VALUE}. This
 * resolver never throws.
 *
 * <h2>Cost</h2>
 * <p>The memberships are read once per request and kept on it, whichever form a rule names. They
 * are not kept across requests: a user who leaves a team, or whose role changes, must be seen by
 * the very next request.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 9.18.0
 */
public class TeamsVarResolver implements VarResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(TeamsVarResolver.class);

    private static final String CACHE_KEY = "__accounts_teams_var_cache";
    private static final String ID = "_id";

    private final AccountsService accountsService;
    private final String name;

    /**
     * @param accountsService where the membership provider of a request comes from
     * @param name            the variable's name, without {@code @}: {@code team-claim-name}
     *                        followed by {@code s}, so that {@code @team} and {@code @teams} go
     *                        together whatever the claim is called
     */
    public TeamsVarResolver(AccountsService accountsService, String name) {
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

        var prefix = "@" + name;
        String suffix;

        if (var.equals(prefix)) {
            suffix = null;
        } else if (var.startsWith(prefix + ".")) {
            suffix = var.substring(prefix.length() + 1);

            // one segment only: @teams._id or @teams.<role>
            if (suffix.isEmpty() || suffix.contains(".")) {
                return BsonNull.VALUE;
            }
        } else {
            return BsonNull.VALUE;
        }

        BsonArray teams;

        try {
            teams = cached(request);
        } catch (Exception e) {
            // a broken resolver must deny, never grant: no team at all, see the failure semantics
            LOGGER.warn("@{} resolution failed for {}: {}", name, var, e.toString());
            teams = new BsonArray();
        }

        if (suffix == null) {
            return teams;
        }

        return ID.equals(suffix) ? ids(teams, null) : ids(teams, suffix);
    }

    /** The ids of the teams, of all of them when {@code role} is null. */
    private static BsonArray ids(BsonArray teams, String role) {
        var ret = new BsonArray();

        for (var t : teams) {
            var team = t.asDocument();
            var teamRole = team.get("role");

            if (role == null || (teamRole != null && teamRole.isString() && role.equals(teamRole.asString().getValue()))) {
                ret.add(team.get(ID));
            }
        }

        return ret;
    }

    /** The teams of the caller, read once per request; a failure throws and is not kept. */
    private BsonArray cached(Request<?> request) {
        Object cached = request.attachedParam(CACHE_KEY);

        if (cached instanceof BsonArray a) {
            return a;
        }

        var teams = build(request);
        request.attachParam(CACHE_KEY, teams);

        return teams;
    }

    private BsonArray build(Request<?> request) {
        var account = request.getAuthenticatedAccount();

        if (account == null || account.getPrincipal() == null || account.getPrincipal().getName() == null) {
            return new BsonArray();
        }

        List<Membership> memberships = accountsService.getMembershipProvider(request)
                .listMemberships(account.getPrincipal().getName());

        var ret = new BsonArray();

        if (memberships != null) {
            for (var m : memberships) {
                if (m != null && m.teamId() != null && !m.teamId().isNull()) {
                    ret.add(TeamClaim.of(m.teamId(), m.role()));
                }
            }
        }

        return ret;
    }
}
