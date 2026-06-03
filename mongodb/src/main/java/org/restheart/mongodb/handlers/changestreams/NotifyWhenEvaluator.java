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

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.utils.BsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * Evaluates the {@code notify_when} predicate defined in a change stream definition.
 *
 * <p>Supported forms:
 * <ul>
 *   <li><b>Scalar equality</b>: {@code { "fullDocument::tenantId": { "$var": "tid" } }}
 *       — event is dispatched if {@code event.fullDocument.tenantId == boundVars["tid"]}</li>
 *   <li><b>Array membership</b>: {@code { "fullDocument::recipients": { "$var": "userId" } }}
 *       — event is dispatched if {@code boundVars["userId"] ∈ event.fullDocument.recipients}</li>
 * </ul>
 *
 * <p>Field path notation: {@code "topLevel::a.b.c"} where {@code ::} separates the top-level
 * change event field (e.g. {@code fullDocument}) from the dot-notation path within it.
 *
 * <p>When {@code notify_when} is absent, {@link #from} returns {@code null} and the caller
 * should broadcast to all sessions (no filtering).
 */
public class NotifyWhenEvaluator {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotifyWhenEvaluator.class);

    private final String topLevelField; // e.g. "fullDocument"
    private final String fieldPath;     // e.g. "tenantId" or "a.b.c"
    private final String varName;       // e.g. "tid"

    private NotifyWhenEvaluator(String topLevelField, String fieldPath, String varName) {
        this.topLevelField = topLevelField;
        this.fieldPath = fieldPath;
        this.varName = varName;
    }

    /**
     * Parses a {@code notify_when} BsonDocument and returns the corresponding evaluator,
     * or {@code null} if {@code notifyWhen} is {@code null} (broadcast mode).
     *
     * @throws IllegalArgumentException if the document format is invalid
     */
    public static NotifyWhenEvaluator from(BsonDocument notifyWhen) {
        if (notifyWhen == null) return null;

        if (notifyWhen.size() != 1) {
            throw new IllegalArgumentException("notify_when must have exactly one entry; got: " + notifyWhen);
        }

        var entry = notifyWhen.entrySet().iterator().next();
        var fieldSpec = entry.getKey();   // "fullDocument::tenantId"
        var varSpec   = entry.getValue(); // { "$var": "tid" }

        if (!varSpec.isDocument() || !varSpec.asDocument().containsKey("$var")) {
            throw new IllegalArgumentException(
                "notify_when value must be { \"$var\": \"<varName>\" }; got: " + varSpec);
        }
        var varName = varSpec.asDocument().getString("$var").getValue();

        var parts = fieldSpec.split("::", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException(
                "notify_when field must use '::' separator, e.g. 'fullDocument::tenantId'; got: " + fieldSpec);
        }

        return new NotifyWhenEvaluator(parts[0].strip(), parts[1].strip(), varName);
    }

    /**
     * Returns {@code true} if {@code event} should be dispatched to a session whose bound
     * variables are {@code boundVars}.
     *
     * <ul>
     *   <li>Returns {@code true} when {@code boundVars} is {@code null} or does not contain
     *       the required variable (pass-through: don't filter what we can't evaluate).</li>
     *   <li>Returns {@code false} when the target field is absent in the event.</li>
     *   <li>Scalar field: returns {@code true} iff field value equals the bound variable.</li>
     *   <li>Array field: returns {@code true} iff the bound variable value is in the array.</li>
     * </ul>
     *
     * @param event     the change event document (as returned by {@code ChangeStreamWorker.getDocument})
     * @param boundVars the session's bound variables (from query params)
     */
    public boolean matches(BsonDocument event, Map<String, String> boundVars) {
        if (boundVars == null || !boundVars.containsKey(varName)) {
            return true; // unbound variable → pass-through
        }

        var varValue = boundVars.get(varName);

        var topLevel = event.get(topLevelField);
        if (topLevel == null || !topLevel.isDocument()) {
            return false; // top-level field absent
        }

        Optional<BsonValue> fieldValue = BsonUtils.get(topLevel.asDocument(), fieldPath);
        if (fieldValue.isEmpty()) {
            return false; // nested field absent
        }

        var fv = fieldValue.get();

        if (fv.isArray()) {
            // array membership: varValue must be a string element of the array
            return fv.asArray().getValues().stream()
                    .filter(BsonValue::isString)
                    .anyMatch(v -> v.asString().getValue().equals(varValue));
        } else if (fv.isString()) {
            return fv.asString().getValue().equals(varValue);
        } else {
            // Non-string, non-array field — no match
            LOGGER.debug("notify_when field '{}::{}' value is neither string nor array — no match",
                    topLevelField, fieldPath);
            return false;
        }
    }

    /** The query parameter name whose value is used for filtering. */
    public String getVarName() {
        return varName;
    }

    @Override
    public String toString() {
        return topLevelField + "::" + fieldPath + " == $var(" + varName + ")";
    }
}
