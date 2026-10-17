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
package org.restheart.mongodb.metadata;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.restheart.exchange.InvalidMetadataException;

/**
 * An integrity rule a collection declares, spanning documents.
 *
 * <p>{@code jsonSchema} validates one document in isolation; this validates the collection. The
 * rule is an aggregation over the collection, and what makes it hold or fail is whether that
 * aggregation returns anything:
 *
 * <pre>
 * { "constraints": [ {
 *     "name": "noNegativeBalance",
 *     "message": "an account balance cannot be negative",
 *     "holdsWhen": "empty",
 *     "stages": [ { "$match": { "balance": { "$lt": 0 } } } ]
 * } ] }
 * </pre>
 *
 * <p>Both directions are needed. {@code empty} states "no bad rows exist" and the documents the
 * pipeline returns name them; {@code notEmpty} states "something must exist" — at least one admin,
 * a parent for this child — which under {@code empty} would have to be written inside out. Any
 * condition over the collection fits one of the two, which is why there is no third form and no
 * boolean: an aggregation returns documents, not a verdict.
 *
 * @param name unique within the collection, and what the error body names
 * @param stages the pipeline, run over the collection the write touched
 * @param holdsWhen which pipeline result means the rule holds
 * @param message returned on violation; the whole diagnostic for a notEmpty rule, which has no rows
 * to show
 * @param enabled false turns the rule off without deleting it
 */
public record Constraint(String name, BsonArray stages, HoldsWhen holdsWhen, String message, boolean enabled) {
    public static final String CONSTRAINTS_ELEMENT_NAME = "constraints";

    private static final String NAME = "name";
    private static final String STAGES = "stages";
    private static final String HOLDS_WHEN = "holdsWhen";
    private static final String MESSAGE = "message";
    private static final String ENABLED = "enabled";

    public enum HoldsWhen {
        EMPTY, NOT_EMPTY;

        static HoldsWhen of(String value) throws InvalidMetadataException {
            return switch (value) {
                case "empty" -> EMPTY;
                case "notEmpty" -> NOT_EMPTY;
                default -> throw new InvalidMetadataException(
                        "invalid '" + HOLDS_WHEN + "': " + value + ", must be 'empty' or 'notEmpty'");
            };
        }
    }

    /** Whether a pipeline that returned {@code rows} documents breaks this rule. */
    public boolean violatedBy(int rows) {
        return holdsWhen == HoldsWhen.EMPTY ? rows > 0 : rows == 0;
    }

    /**
     * Reads the {@code constraints} of a collection, or an empty list when it declares none.
     *
     * @throws InvalidMetadataException if the element is there but malformed — a duplicate name, an
     * empty pipeline, an unknown {@code holdsWhen}. Thrown when the metadata is written rather than
     * when a write first meets it: a collection that looks configured and silently enforces nothing
     * is the worst of the three outcomes.
     */
    public static List<Constraint> getFromJson(BsonDocument collProps) throws InvalidMetadataException {
        final var ret = new ArrayList<Constraint>();

        if (collProps == null) {
            return ret;
        }

        final var declared = collProps.get(CONSTRAINTS_ELEMENT_NAME);

        if (declared == null) {
            return ret;
        }

        if (!declared.isArray()) {
            throw new InvalidMetadataException("'" + CONSTRAINTS_ELEMENT_NAME + "' must be an array");
        }

        final var names = new HashSet<String>();

        for (var declaredConstraint : declared.asArray()) {
            if (!declaredConstraint.isDocument()) {
                throw new InvalidMetadataException(
                        "'" + CONSTRAINTS_ELEMENT_NAME + "' must contain documents, found " + declaredConstraint);
            }

            final var constraint = of(declaredConstraint.asDocument());

            if (!names.add(constraint.name())) {
                throw new InvalidMetadataException("duplicated constraint name '" + constraint.name() + "'");
            }

            ret.add(constraint);
        }

        return ret;
    }

    private static Constraint of(BsonDocument declared) throws InvalidMetadataException {
        final var name = declared.get(NAME);

        if (name == null || !name.isString() || name.asString().getValue().isBlank()) {
            throw new InvalidMetadataException("constraint is missing a '" + NAME + "'");
        }

        final var stages = declared.get(STAGES);

        if (stages == null || !stages.isArray() || stages.asArray().isEmpty()) {
            throw new InvalidMetadataException(
                    "constraint '" + name.asString().getValue() + "' needs a non-empty '" + STAGES + "' array");
        }

        for (var stage : stages.asArray()) {
            if (!stage.isDocument()) {
                throw new InvalidMetadataException("constraint '" + name.asString().getValue()
                        + "': '" + STAGES + "' must contain documents, found " + stage);
            }
        }

        final var holdsWhen = declared.get(HOLDS_WHEN);

        if (holdsWhen != null && !holdsWhen.isString()) {
            throw new InvalidMetadataException("constraint '" + name.asString().getValue()
                    + "': '" + HOLDS_WHEN + "' must be a string");
        }

        final var message = declared.get(MESSAGE);

        if (message != null && !message.isString()) {
            throw new InvalidMetadataException("constraint '" + name.asString().getValue()
                    + "': '" + MESSAGE + "' must be a string");
        }

        final var enabled = declared.get(ENABLED);

        if (enabled != null && !enabled.isBoolean()) {
            throw new InvalidMetadataException("constraint '" + name.asString().getValue()
                    + "': '" + ENABLED + "' must be a boolean");
        }

        return new Constraint(
                name.asString().getValue(),
                stages.asArray(),
                holdsWhen == null ? HoldsWhen.EMPTY : HoldsWhen.of(holdsWhen.asString().getValue()),
                message == null ? null : message.asString().getValue(),
                enabled == null || enabled.asBoolean().getValue());
    }
}
