/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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
package org.restheart.mongodb.handlers.changestreams;

import java.util.List;
import java.util.Set;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;

/**
 * Applies the {@code mongo} section of the caller's permission to a change stream, as a REST
 * {@code GET} applies it to the documents it returns.
 *
 * <ul>
 * <li>{@code readFilter}: a change event carries the document under {@code fullDocument}, so
 * every field name of the filter is prefixed with {@code fullDocument.}; {@code $and},
 * {@code $or} and {@code $nor} are rewritten recursively. Events without a {@code fullDocument}
 * (e.g. deletes) do not match a filter on its fields and are not delivered. Other top-level
 * operators ({@code $expr}, {@code $where}, {@code $text}, ...) cannot be rewritten safely, so
 * the stream is refused.</li>
 * <li>{@code projectResponse}: an exclusion projection removes the properties from
 * {@code fullDocument}, {@code fullDocumentBeforeChange} and
 * {@code updateDescription.updatedFields}, whose keys are paths such as {@code "profile.secret"}.
 * An inclusion projection would also remove the {@code _id} of the event, its resume token, so the
 * stream is refused.</li>
 * </ul>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
final class ChangeStreamPermissions {
    private static final String FULL_DOCUMENT = "fullDocument.";
    private static final String FULL_DOCUMENT_BEFORE_CHANGE = "fullDocumentBeforeChange.";
    private static final String UPDATED_FIELDS = "updateDescription.updatedFields";
    private static final Set<String> LOGICAL_OPERATORS = Set.of("$and", "$or", "$nor");

    private ChangeStreamPermissions() {
    }

    /**
     * @param readFilter the interpolated readFilter of the caller's permission
     * @return the {@code $match} stage that applies it to the change events
     * @throws SecurityException if the filter uses an operator that cannot be applied to the events
     */
    static BsonDocument readFilterStage(BsonDocument readFilter) {
        return new BsonDocument("$match", onFullDocument(readFilter));
    }

    /**
     * @param projectResponse the projectResponse of the caller's permission, validated when the
     *        permission was loaded: all 0 (exclusion) or all 1 (inclusion)
     * @return the stages that remove the excluded properties from the change events
     * @throws SecurityException if the projection is an inclusion
     */
    static List<BsonDocument> projectResponseStages(BsonDocument projectResponse) {
        var inclusion = projectResponse.values().stream()
                .anyMatch(v -> !v.isNumber() || v.asNumber().intValue() != 0);

        if (inclusion) {
            throw new SecurityException("the permission's projectResponse is an inclusion, which cannot be applied to a change stream");
        }

        // a key nested in another excluded one is already removed with it, and $project refuses
        // the pair as a path collision
        var keys = projectResponse.keySet().stream()
                .filter(key -> projectResponse.keySet().stream().noneMatch(other -> key.startsWith(other + ".")))
                .toList();

        // nested properties of the documents, and of the objects set by an update
        var exclusions = new BsonDocument();
        for (var key : keys) {
            exclusions.put(FULL_DOCUMENT.concat(key), new BsonInt32(0));
            exclusions.put(FULL_DOCUMENT_BEFORE_CHANGE.concat(key), new BsonInt32(0));
            exclusions.put(UPDATED_FIELDS + "." + key, new BsonInt32(0));
        }

        // updatedFields keys are paths ("profile.secret"), which $project cannot address: drop
        // the entries whose key is an excluded property or one nested in it
        var keep = new BsonArray();
        for (var key : keys) {
            keep.add(new BsonDocument("$ne", new BsonArray(List.of(new BsonString("$$f.k"), new BsonString(key)))));
            keep.add(new BsonDocument("$ne", new BsonArray(List.of(
                    new BsonDocument("$indexOfCP", new BsonArray(List.of(new BsonString("$$f.k"), new BsonString(key + ".")))),
                    new BsonInt32(0)))));
        }

        var filteredUpdatedFields = new BsonDocument("$arrayToObject", new BsonDocument("$filter", new BsonDocument()
                .append("input", new BsonDocument("$objectToArray", new BsonString("$" + UPDATED_FIELDS)))
                .append("as", new BsonString("f"))
                .append("cond", new BsonDocument("$and", keep))));

        // only events that have updatedFields are rewritten; for the others "$updateDescription"
        // is missing and $set leaves it so
        var updateDescription = new BsonDocument("$cond", new BsonDocument()
                .append("if", new BsonDocument("$eq", new BsonArray(List.of(
                        new BsonDocument("$type", new BsonString("$" + UPDATED_FIELDS)),
                        new BsonString("object")))))
                .append("then", new BsonDocument("$mergeObjects", new BsonArray(List.of(
                        new BsonString("$updateDescription"),
                        new BsonDocument("updatedFields", filteredUpdatedFields)))))
                .append("else", new BsonString("$updateDescription")));

        return List.of(
                new BsonDocument("$project", exclusions),
                new BsonDocument("$set", new BsonDocument("updateDescription", updateDescription)));
    }

    private static BsonDocument onFullDocument(BsonDocument filter) {
        var ret = new BsonDocument();

        for (var key : filter.keySet()) {
            var value = filter.get(key);

            if (LOGICAL_OPERATORS.contains(key)) {
                if (!value.isArray()) {
                    throw new SecurityException("the readFilter operator " + key + " requires an array");
                }

                var clauses = new BsonArray();
                for (var clause : value.asArray()) {
                    if (!clause.isDocument()) {
                        throw new SecurityException("the readFilter operator " + key + " requires an array of documents");
                    }
                    clauses.add(onFullDocument(clause.asDocument()));
                }
                ret.put(key, clauses);
            } else if (key.startsWith("$")) {
                throw new SecurityException("the readFilter operator " + key + " cannot be applied to a change stream");
            } else {
                ret.put(FULL_DOCUMENT.concat(key), value);
            }
        }

        return ret;
    }
}
