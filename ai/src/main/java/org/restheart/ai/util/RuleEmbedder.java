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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonNull;
import org.bson.BsonValue;
import org.restheart.ai.util.CollectionEmbeddingConfig.EmbeddingRule;
import org.restheart.exchange.Request;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.ai.ContextualEmbeddingModel;
import org.restheart.plugins.ai.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Embeds documents by the embedding rules of the collection they are written to: the one place
 * that does it, for {@code autoEmbeddingInterceptor}, on a write through the API, and for
 * {@code documentChunkingInterceptor}, on the chunks it writes itself (#754).
 *
 * <p>For each rule, every document that carries the rule's text field as a string gets the rule's
 * vector field. The rule's provider, model and length are attached as request overrides for the
 * rule only, and put back. With {@code groupBy} and a contextual model, the documents that share
 * the value of that field are embedded together, one call per group, each vector aware of the
 * others; otherwise one call embeds them all, each on its own.
 *
 * <p>A failure does not stop the write: the documents are left without that vector, and the
 * returned warning says why, {@code auto-embedding of '<field>' failed: …} or {@code … skipped: …}.
 */
public final class RuleEmbedder {
    private static final Logger LOGGER = LoggerFactory.getLogger(RuleEmbedder.class);

    private final PluginsRegistry registry;
    private final String defaultProviderName;
    // resolved lazily, once every plugin is initialized, and cached per provider name
    private final Map<String, EmbeddingModel> resolvedModels = new ConcurrentHashMap<>();

    public RuleEmbedder(PluginsRegistry registry, String defaultProviderName) {
        this.registry = registry;
        this.defaultProviderName = defaultProviderName == null ? "" : defaultProviderName;
    }

    /**
     * Writes each rule's vector into the documents that carry its text field.
     *
     * @param request the request the write is made on, for the overrides; may be null
     * @param rules the embedding rules of the collection the documents go to
     * @param docs the documents, changed in place
     * @return the warnings, one per rule that could not embed; empty when all did
     */
    public List<String> embed(Request<?> request, List<EmbeddingRule> rules, List<BsonDocument> docs) {
        var warnings = new ArrayList<String>();
        for (var rule : rules) {
            var targets = new ArrayList<BsonDocument>();
            var texts = new ArrayList<String>();
            for (var doc : docs) {
                var text = doc.get(rule.textField());
                // a blank text has no meaning to embed, and a provider refuses the whole request that carries one
                if (text != null && text.isString() && !text.asString().getValue().isBlank()) {
                    targets.add(doc);
                    texts.add(text.asString().getValue());
                }
            }
            if (texts.isEmpty()) {
                continue;
            }
            var previous = request != null ? CollectionEmbeddingConfig.attach(request, rule, defaultProviderName) : Map.<String, Object>of();
            try {
                var warning = embed(request, rule, targets, texts);
                if (warning != null) {
                    warnings.add(warning);
                }
            } finally {
                if (request != null) {
                    CollectionEmbeddingConfig.restore(request, previous);
                }
            }
        }
        return warnings;
    }

    private String embed(Request<?> request, EmbeddingRule rule, List<BsonDocument> targets, List<String> texts) {
        var providerName = request != null ? RequestOverrides.str(request, RequestOverrides.EMBEDDING_PROVIDER, defaultProviderName)
                : rule.provider() != null ? rule.provider() : defaultProviderName;
        if (providerName.isBlank()) {
            LOGGER.debug("no embedding provider for '{}', neither on the rule nor configured nor overridden", rule.embeddingField());
            return null;
        }
        var model = PluginModelResolver.resolve(registry, resolvedModels, providerName, EmbeddingModel.class).orElse(null);
        if (model == null) {
            LOGGER.warn("embedding provider '{}' not found, not enabled, or does not supply an EmbeddingModel", providerName);
            return "auto-embedding of '" + rule.embeddingField() + "' skipped: embedding provider '" + providerName
                    + "' not found, not enabled, or does not supply an EmbeddingModel";
        }
        try {
            var missing = 0;
            if (rule.groupBy() != null && model instanceof ContextualEmbeddingModel contextual) {
                for (var group : groups(targets, rule.groupBy()).values()) {
                    var groupTexts = group.stream().map(i -> texts.get(i)).toList();
                    var vectors = contextual.embedChunks(groupTexts, request);
                    for (int k = 0;k < group.size();k++) {
                        var vector = k < vectors.size() ? vectors.get(k) : null;
                        missing += vector == null ? 1 : 0;
                        apply(targets.get(group.get(k)), vector, rule.embeddingField());
                    }
                }
            } else {
                var vectors = model.embed(texts, request);
                for (int i = 0;i < targets.size();i++) {
                    var vector = i < vectors.size() ? vectors.get(i) : null;
                    missing += vector == null ? 1 : 0;
                    apply(targets.get(i), vector, rule.embeddingField());
                }
            }
            // a provider that splits the work may lose part of it: the rest keeps its vectors
            return missing == 0 ? null
                    : "auto-embedding of '" + rule.embeddingField() + "' incomplete: " + missing + " of " + targets.size()
                    + " documents without a vector, a request to '" + providerName + "' failed";
        } catch (Exception e) {
            LOGGER.error("failed to generate embeddings for '{}' via '{}': {}", rule.embeddingField(), providerName, e.getMessage(), e);
            return "auto-embedding of '" + rule.embeddingField() + "' failed: " + e.getMessage();
        }
    }

    /** The positions of the targets, grouped by the value of {@code field}, in the order the groups first appear. */
    static Map<BsonValue, List<Integer>> groups(List<BsonDocument> targets, String field) {
        var groups = new LinkedHashMap<BsonValue, List<Integer>>();
        for (int i = 0;i < targets.size();i++) {
            var key = targets.get(i).get(field);
            groups.computeIfAbsent(key == null ? BsonNull.VALUE : key, k -> new ArrayList<>()).add(i);
        }
        return groups;
    }

    /** Writes {@code vector} as an array of doubles; a null vector leaves the document as it is. */
    static void apply(BsonDocument target, float[] vector, String field) {
        if (vector == null) {
            return;
        }
        var arr = new BsonArray();
        for (var f : vector) {
            arr.add(new BsonDouble(f));
        }
        target.append(field, arr);
    }
}
