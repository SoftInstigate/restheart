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
package org.restheart.accounts.spi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.restheart.utils.BsonUtils;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

/**
 * The one-off migration of existing data to the 9.12.0 membership model (#766): the user
 * documents lose the {@code teams} array and the {@code team.role} copy, a bare pre-9.6.0
 * {@code team} id becomes {@code team: { _id }}, and every membership the old {@code teams}
 * listed that the team's {@code members} does not confirm is reported, never added: the team
 * is the source now, and a disagreement is for an operator to look at.
 *
 * <p>Planning is separated from writing, so the plan can be checked on documents in the old
 * shape without a database: {@link #plan} reads, {@link #apply} writes.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 9.12.0
 */
public final class MembershipMigration {
    private MembershipMigration() {
    }

    /** A membership the user document claimed and the team does not confirm. */
    public record Mismatch(String userId, BsonValue teamId, String role, String why) {
        @Override
        public String toString() {
            return "user <" + userId + "> listed team " + BsonUtils.toJson(teamId) + " as " + role + ": " + why;
        }
    }

    /** The update to apply to one user document. */
    public record UserUpdate(String userId, BsonDocument update) {
    }

    /** What the migration would do. */
    public record Plan(List<UserUpdate> updates, List<Mismatch> mismatches, int usersScanned) {
    }

    /**
     * Plans the migration of {@code users}, resolving teams through {@code teamById}.
     *
     * @param users    the user documents to look at: only those still carrying {@code teams},
     *                 {@code team.role} or a bare {@code team} id get an update
     * @param teamById how to load a team document by its id; empty when it does not exist
     */
    public static Plan plan(Iterable<BsonDocument> users, Function<BsonValue, Optional<BsonDocument>> teamById) {
        var updates = new ArrayList<UserUpdate>();
        var mismatches = new ArrayList<Mismatch>();
        var scanned = 0;

        // a team is read once, however many users list it
        var teams = new HashMap<BsonValue, Optional<BsonDocument>>();
        Function<BsonValue, Optional<BsonDocument>> cached = id -> teams.computeIfAbsent(id, teamById);

        for (var user : users) {
            scanned++;

            if (!user.containsKey("_id") || !user.get("_id").isString()) {
                continue;
            }
            var userId = user.getString("_id").getValue();

            var unset = new BsonDocument();
            var set = new BsonDocument();

            if (user.containsKey("teams")) {
                if (user.get("teams").isArray()) {
                    for (var entry : user.getArray("teams")) {
                        if (!entry.isDocument() || !entry.asDocument().containsKey("id")) {
                            continue;
                        }
                        var e = entry.asDocument();
                        var teamId = e.get("id");
                        var role = e.containsKey("role") && e.get("role").isString() ? e.getString("role").getValue() : "member";

                        var team = cached.apply(teamId);
                        if (team.isEmpty()) {
                            mismatches.add(new Mismatch(userId, teamId, role, "the team does not exist"));
                        } else {
                            var member = DefaultMembershipProvider.member(team.get(), userId);
                            if (member.isEmpty()) {
                                mismatches.add(new Mismatch(userId, teamId, role, "the team's members do not include the user"));
                            } else {
                                var teamRole = member.get().containsKey("role") && member.get().get("role").isString()
                                        ? member.get().getString("role").getValue() : "member";
                                if (!role.equals(teamRole)) {
                                    mismatches.add(new Mismatch(userId, teamId, role, "the team's members say " + teamRole + ", which is what applies now"));
                                }
                            }
                        }
                    }
                }
                unset.put("teams", new BsonString(""));
            }

            if (user.containsKey("team") && !user.get("team").isNull()) {
                var team = user.get("team");
                if (team.isDocument()) {
                    if (team.asDocument().containsKey("role")) {
                        unset.put("team.role", new BsonString(""));
                    }
                } else {
                    // the pre-9.6.0 bare id
                    set.put("team", new BsonDocument("_id", team));
                }
            }

            if (unset.isEmpty() && set.isEmpty()) {
                continue;
            }

            var update = new BsonDocument();
            if (!set.isEmpty()) {
                update.put("$set", set);
            }
            if (!unset.isEmpty()) {
                update.put("$unset", unset);
            }
            updates.add(new UserUpdate(userId, update));
        }

        return new Plan(updates, mismatches, scanned);
    }

    /** Applies {@code plan} to {@code users}, one update per document. */
    public static int apply(Plan plan, MongoCollection<BsonDocument> users) {
        var applied = 0;

        for (var u : plan.updates()) {
            applied += users.updateOne(Filters.eq("_id", new BsonString(u.userId())), u.update()).getMatchedCount();
        }

        return applied;
    }

    /** Plans against the users with something to migrate and the teams of the same database. */
    public static Plan plan(MongoCollection<BsonDocument> users, MongoCollection<BsonDocument> teams) {
        var candidates = users.find(Filters.or(
                Filters.exists("teams"),
                Filters.exists("team.role"),
                Filters.and(Filters.exists("team"), Filters.not(Filters.type("team", "object")), Filters.not(Filters.type("team", "null")))))
                .into(new ArrayList<>());

        return plan(candidates, id -> Optional.ofNullable(teams.find(Filters.eq("_id", id)).first()));
    }

    /** A readable summary for the log. */
    public static String summary(Plan plan, int applied) {
        return "membership migration: " + plan.usersScanned() + " user documents looked at, " + applied
                + " updated, " + plan.mismatches().size() + " memberships not confirmed by their team";
    }
}
