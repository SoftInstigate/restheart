/*-
 * ========================LICENSE_START=================================
 * restheart-ai
 * %%
 * Copyright (C) 2024 - 2026 SoftInstigate
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
package org.restheart.ai.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.exchange.Request;

/**
 * The embedding rules of a collection, read from its {@code vectorSearch} metadata.
 *
 * <p>A rule embeds one text field into one vector field with one model. The model is a property
 * of the vector field: the vectors of a field and those of the questions searched against it must
 * come from the same model, and a collection may hold several such fields, each with its own —
 * {@code summary} with {@code voyage-law-2} and {@code body} with {@code voyage-context-4}, both
 * searchable (#752). The static configuration of the providers, and the {@code override-ai-*}
 * parameters a request carries, give the default; a rule that names its own wins.
 *
 * <pre>{@code
 * { "vectorSearch": [
 *     { "textField": "summary", "embeddingField": "summaryVector",
 *       "provider": "voyageEmbeddingProvider",            // optional: the default provider otherwise
 *       "model": "voyage-law-2",                          // optional: the provider's default otherwise
 *       "dimensions": 1024 },                             // optional: the model's default otherwise
 *     { "textField": "body", "embeddingField": "bodyVector",
 *       "provider": "voyageContextualEmbeddingProvider", "model": "voyage-context-4" }
 * ] }
 * }</pre>
 *
 * <p>The object form, one rule not wrapped in a list, is what every deployment had before the list
 * existed and stays valid: it reads as a list of one.
 *
 * <p>{@code groupBy} names a field whose value groups the documents of one write: those with the
 * same value are embedded together by a contextual model, each vector aware of the others, as the
 * chunks of a file are with {@code "groupBy": "fileId"}. Without it every document is embedded on
 * its own, and a model that is not contextual ignores it.
 *
 * <p>{@link #attach} turns a rule into the same request overrides the providers already read, so
 * nothing else changes: {@code autoEmbeddingInterceptor} attaches them before embedding a write,
 * {@code $vectorize} before embedding a question. The overrides live on the request, so a caller
 * that applies several rules in turn attaches each one and {@link #restore}s what it replaced.
 */
public final class CollectionEmbeddingConfig {
    public static final String VECTOR_SEARCH = "vectorSearch";
    static final String TEXT_FIELD = "textField";
    static final String EMBEDDING_FIELD = "embeddingField";
    static final String PROVIDER = "provider";
    static final String MODEL = "model";
    static final String DIMENSIONS = "dimensions";
    static final String GROUP_BY = "groupBy";

    /**
     * One embedding rule of a collection.
     *
     * @param textField the field whose text is embedded
     * @param embeddingField the field the vector is written to, and searched on
     * @param provider the {@code Provider<EmbeddingModel>} plugin name, or null for the default
     * @param model the model, or null for the provider's default
     * @param dimensions the vector length, or null for the model's default
     * @param groupBy a field whose value groups the documents of one write, embedded together by a
     *        contextual model, e.g. {@code fileId} on the chunks of a file; null for each on its own
     */
    public record EmbeddingRule(String textField, String embeddingField, String provider, String model, Integer dimensions, String groupBy) {
        public EmbeddingRule(String textField, String embeddingField, String provider, String model, Integer dimensions) {
            this(textField, embeddingField, provider, model, dimensions, null);
        }
    }

    private CollectionEmbeddingConfig() {
    }

    /**
     * The rules a collection declares. The object form is a list of one; the list form, as many
     * as it holds. A rule without a {@code textField} or an {@code embeddingField} is skipped: the
     * metadata checker refuses one when it is written, this only guards what was stored before.
     *
     * @param collectionProps the collection's properties, may be null
     * @return the rules, in the order declared; empty when there are none
     */
    public static List<EmbeddingRule> rules(BsonDocument collectionProps) {
        var vs = collectionProps == null ? null : collectionProps.get(VECTOR_SEARCH);
        var rules = new ArrayList<EmbeddingRule>();
        for (var item : asList(vs)) {
            if (item.isDocument()) {
                var rule = ruleOf(item.asDocument());
                if (rule != null) {
                    rules.add(rule);
                }
            }
        }
        return rules;
    }

    /**
     * The one rule of a collection, when it declares exactly one.
     *
     * @param collectionProps the collection's properties, may be null
     * @return the rule; empty when there are none or more than one
     */
    public static Optional<EmbeddingRule> soleRule(BsonDocument collectionProps) {
        var rules = rules(collectionProps);
        return rules.size() == 1 ? Optional.of(rules.get(0)) : Optional.empty();
    }

    /**
     * The rule that writes the given vector field.
     *
     * @param collectionProps the collection's properties, may be null
     * @param embeddingField the vector field
     * @return the rule; empty when no rule writes that field
     */
    public static Optional<EmbeddingRule> ruleFor(BsonDocument collectionProps, String embeddingField) {
        return rules(collectionProps).stream().filter(r -> r.embeddingField().equals(embeddingField)).findFirst();
    }

    /**
     * Why a {@code vectorSearch} value cannot be stored: null when it can.
     *
     * <p>Refused, with the rule named: a value that is neither an object nor a list of objects; a
     * rule without a non-blank {@code textField} or {@code embeddingField}; a {@code provider} or
     * {@code model} that is not a non-blank string; {@code dimensions} that are not a positive
     * number; two rules writing the same {@code embeddingField}. A {@code null} value is the
     * metadata being removed and is fine.
     *
     * @param vectorSearch the value being written under {@code vectorSearch}, may be null
     * @return the reason, or null when the value is valid
     */
    public static String invalidReason(BsonValue vectorSearch) {
        if (vectorSearch == null || vectorSearch.isNull()) {
            return null;
        }
        if (!vectorSearch.isDocument() && !vectorSearch.isArray()) {
            return "vectorSearch must be an object (one embedding rule) or a list of objects (one rule each)";
        }

        var items = asList(vectorSearch);
        var seen = new HashMap<String, Integer>();
        for (int i = 0;i < items.size();i++) {
            var name = vectorSearch.isArray() ? "vectorSearch[" + i + "]" : "vectorSearch";
            var item = items.get(i);
            if (!item.isDocument()) {
                return name + " must be an object with textField and embeddingField";
            }
            var rule = item.asDocument();
            var reason = invalidRuleReason(name, rule);
            if (reason != null) {
                return reason;
            }
            var embeddingField = rule.getString(EMBEDDING_FIELD).getValue();
            var earlier = seen.putIfAbsent(embeddingField, i);
            if (earlier != null) {
                return name + " writes its vector to '" + embeddingField + "', as vectorSearch[" + earlier + "] already does: one rule per vector field";
            }
        }
        return null;
    }

    private static String invalidRuleReason(String name, BsonDocument rule) {
        for (var required : List.of(TEXT_FIELD, EMBEDDING_FIELD)) {
            if (str(rule.get(required)) == null) {
                return name + " needs a non-blank string " + required;
            }
        }
        for (var optional : List.of(PROVIDER, MODEL, GROUP_BY)) {
            var v = rule.get(optional);
            if (v != null && !v.isNull() && str(v) == null) {
                return name + " (embeddingField '" + rule.getString(EMBEDDING_FIELD).getValue() + "'): " + optional + " must be a non-blank string";
            }
        }
        var d = rule.get(DIMENSIONS);
        if (d != null && !d.isNull() && !(d.isNumber() && d.asNumber().intValue() > 0)) {
            return name + " (embeddingField '" + rule.getString(EMBEDDING_FIELD).getValue() + "'): dimensions must be a positive number";
        }
        return null;
    }

    /**
     * Attaches to the request the overrides a rule calls for, and returns the values they
     * replaced so the caller can {@link #restore} them once the rule has been applied.
     *
     * @param request the request being served
     * @param rule the rule to apply
     * @param defaultProvider the provider the request would use otherwise
     * @return the previous value of every parameter attached, null included, for {@link #restore}
     */
    public static Map<String, Object> attach(Request<?> request, EmbeddingRule rule, String defaultProvider) {
        var current = RequestOverrides.str(request, RequestOverrides.EMBEDDING_PROVIDER, defaultProvider);
        var previous = new LinkedHashMap<String, Object>();
        overridesFor(rule, current).forEach((key, value) -> {
            previous.put(key, request.attachedParam(key));
            request.attachParam(key, value);
        });
        return previous;
    }

    /**
     * Puts back the parameters {@link #attach} replaced, so the next rule, or whatever runs after,
     * sees the request as it was.
     *
     * @param request the request being served
     * @param previous what {@link #attach} returned
     */
    public static void restore(Request<?> request, Map<String, Object> previous) {
        previous.forEach(request::attachParam);
    }

    /**
     * The overrides a rule calls for, keyed as {@link RequestOverrides} names them. Pure, so it
     * can be tested without an exchange.
     *
     * @param rule the rule
     * @param effectiveProvider the provider in force before the rule is read, may be blank
     * @return the parameters to attach, in the order they were derived; empty when there are none
     */
    public static Map<String, Object> overridesFor(EmbeddingRule rule, String effectiveProvider) {
        var overrides = new LinkedHashMap<String, Object>();
        if (rule == null) {
            return overrides;
        }

        if (rule.provider() != null) {
            overrides.put(RequestOverrides.EMBEDDING_PROVIDER, rule.provider());
        }
        var inForce = rule.provider() != null ? rule.provider() : effectiveProvider;

        var modelKey = modelKeyOf(inForce);
        if (rule.model() != null && modelKey != null) {
            overrides.put(modelKey, rule.model());
        }

        var dimensionsKey = dimensionsKeyOf(inForce);
        if (rule.dimensions() != null && rule.dimensions() > 0 && dimensionsKey != null) {
            overrides.put(dimensionsKey, rule.dimensions());
        }

        return overrides;
    }

    /** The override that sets the model of a provider; null for a provider that has none, or an unknown one. */
    static String modelKeyOf(String provider) {
        if (provider == null) {
            return null;
        }
        return switch (provider) {
            case "voyageEmbeddingProvider" -> RequestOverrides.VOYAGE_MODEL;
            case "voyageContextualEmbeddingProvider" -> RequestOverrides.VOYAGE_CONTEXTUAL_MODEL;
            case "openAIEmbeddingProvider" -> RequestOverrides.OPENAI_MODEL;
            case "ollamaEmbeddingProvider" -> RequestOverrides.OLLAMA_MODEL;
            default -> null;
        };
    }

    /** The override that sets the vector's length; null for a provider that takes none. */
    static String dimensionsKeyOf(String provider) {
        if (provider == null) {
            return null;
        }
        return switch (provider) {
            case "voyageEmbeddingProvider" -> RequestOverrides.VOYAGE_OUTPUT_DIMENSION;
            case "voyageContextualEmbeddingProvider" -> RequestOverrides.VOYAGE_CONTEXTUAL_OUTPUT_DIMENSION;
            case "openAIEmbeddingProvider" -> RequestOverrides.OPENAI_DIMENSIONS;
            default -> null;
        };
    }

    /** The object form as a list of one, the list form as is, anything else as nothing. */
    private static List<BsonValue> asList(BsonValue vs) {
        if (vs == null) {
            return List.of();
        }
        if (vs.isDocument()) {
            return List.of(vs);
        }
        if (vs.isArray()) {
            return vs.asArray().getValues();
        }
        return List.of();
    }

    /** The rule a document declares, or null when it lacks textField or embeddingField. */
    private static EmbeddingRule ruleOf(BsonDocument doc) {
        var textField = str(doc.get(TEXT_FIELD));
        var embeddingField = str(doc.get(EMBEDDING_FIELD));
        if (textField == null || embeddingField == null) {
            return null;
        }
        var d = doc.get(DIMENSIONS);
        var dimensions = d != null && d.isNumber() && d.asNumber().intValue() > 0 ? Integer.valueOf(d.asNumber().intValue()) : null;
        return new EmbeddingRule(textField, embeddingField, str(doc.get(PROVIDER)), str(doc.get(MODEL)), dimensions, str(doc.get(GROUP_BY)));
    }

    private static String str(BsonValue v) {
        return v != null && v.isString() && !v.asString().getValue().isBlank() ? v.asString().getValue() : null;
    }
}
