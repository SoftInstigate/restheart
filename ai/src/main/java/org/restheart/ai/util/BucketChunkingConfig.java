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
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;

/**
 * The chunking rules of a GridFS bucket, read from its {@code chunking} metadata (#754).
 *
 * <p>A rule says which files it applies to, how they are cut and where their chunks go. It says
 * nothing about vectors: the collection the chunks go to embeds them with its own
 * {@code vectorSearch} rules, as any collection does.
 *
 * <pre>{@code
 * { "chunking": [
 *     { "name": "manuals",
 *       "filter": { "contentType": ["application/pdf", "application/vnd.openxmlformats-officedocument.*"] },
 *       "target-collection": "manuals_chunks", "chunk-size": 1000, "chunk-overlap": 200 },
 *     { "name": "code", "filter": { "extension": [".java", ".ts", ".py"] },
 *       "target-collection": "code_chunks", "splitter": "text" }
 * ] }
 * }</pre>
 *
 * <p>The filter has three keys, all optional, all matched case-insensitively and all required to
 * match when present: {@code contentType}, the type Tika detects, a trailing {@code *} matching any
 * suffix; {@code extension}, of the uploaded filename; {@code metadata}, a MongoDB filter on the
 * file's {@code metadata} document, so an application routes a file by what it sets at upload. A
 * rule without a filter matches every file. The first rule that matches, in list order, is the one
 * applied, and a file that matches none is not chunked. A single rule not wrapped in a list reads
 * as a list of one, as {@code vectorSearch} does.
 *
 * <p>{@code splitter} chooses how the text is cut: {@code auto}, the default, at function and class
 * boundaries for a recognized source file and in character windows otherwise; {@code text}, always
 * in character windows, for a model that wants plain windows even of code; {@code code}, at code
 * boundaries whenever the language is recognized. {@code chunk-size} and {@code chunk-overlap}
 * apply to every splitter; code gets no overlap unless the rule sets one.
 *
 * <p>A rule's values win over the node's configuration and over the per-request overrides, and a
 * missing one falls back to them.
 */
public final class BucketChunkingConfig {
    public static final String CHUNKING = "chunking";
    static final String NAME = "name";
    static final String FILTER = "filter";
    static final String CONTENT_TYPE = "contentType";
    static final String EXTENSION = "extension";
    static final String METADATA = "metadata";
    static final String TARGET_COLLECTION = "target-collection";
    /** Keys of an embedding rule, refused here with a pointer to where they belong. */
    private static final Set<String> EMBEDDING_KEYS = Set.of("provider", "model", "dimensions", "textField", "embeddingField", "groupBy");
    static final String CHUNK_SIZE = "chunk-size";
    static final String CHUNK_OVERLAP = "chunk-overlap";
    static final String SPLITTER = "splitter";
    /** How a file's text is cut: {@code auto} at function and class boundaries for recognized source files, character windows otherwise; {@code text} always character windows; {@code code} at code boundaries when the language is recognized. */
    public static final Set<String> SPLITTERS = Set.of("auto", "text", "code");

    private static final Set<String> RULE_KEYS = Set.of(NAME, FILTER, TARGET_COLLECTION, CHUNK_SIZE, CHUNK_OVERLAP, SPLITTER);
    private static final Set<String> FILTER_KEYS = Set.of(CONTENT_TYPE, EXTENSION, METADATA);
    /** Operators that would run code in the database: never in a filter a tenant writes. */
    private static final Set<String> CODE_OPERATORS = Set.of("$where", "$function", "$accumulator");

    /**
     * One chunking rule of a bucket.
     *
     * @param name a label, recorded on each chunk as {@code rule}; may be null
     * @param contentTypes the detected types it applies to, empty for any
     * @param extensions the filename extensions it applies to, lower case with the dot, empty for any
     * @param metadata a MongoDB filter on the file's {@code metadata}, or null for any
     * @param targetCollection where the chunks go
     * @param chunkSize the chunk size, or null for the node's
     * @param chunkOverlap the overlap, or null for the node's; applied to code too when set here
     * @param splitter {@code auto}, {@code text} or {@code code}, see {@link BucketChunkingConfig#SPLITTERS}
     */
    public record ChunkingRule(String name, List<String> contentTypes, List<String> extensions, BsonDocument metadata,
                               String targetCollection, Integer chunkSize, Integer chunkOverlap, String splitter) {

        /** Whether the file's detected type and name pass the rule's filter; {@link #metadata} is checked by the caller, against the database. */
        public boolean matches(String contentType, String filename) {
            return (contentTypes.isEmpty() || contentTypes.stream().anyMatch(p -> contentTypeMatches(p, contentType)))
                    && (extensions.isEmpty() || extensions.stream().anyMatch(e -> extensionMatches(e, filename)));
        }
    }

    private BucketChunkingConfig() {
    }

    /** Whether the bucket declares chunking at all: an empty list declares that nothing is chunked. */
    public static boolean declares(BsonDocument bucketProps) {
        return bucketProps != null && bucketProps.containsKey(CHUNKING) && !bucketProps.get(CHUNKING).isNull();
    }

    /**
     * The rules a bucket declares, in order. A rule without {@code target-collection} is skipped:
     * the metadata checker refuses one when it is written, this only guards what was stored before.
     */
    public static List<ChunkingRule> rules(BsonDocument bucketProps) {
        var rules = new ArrayList<ChunkingRule>();
        for (var item : asList(bucketProps == null ? null : bucketProps.get(CHUNKING))) {
            if (item.isDocument() && invalidRuleReason("", item.asDocument()) == null) {
                rules.add(ruleOf(item.asDocument()));
            }
        }
        return rules;
    }

    /**
     * Why a {@code chunking} value cannot be stored: null when it can. Refused, naming the rule: a
     * value that is neither an object nor a list of objects; a rule without a non-blank
     * {@code target-collection}; an unknown key in a rule or in its filter; a filter value of the
     * wrong kind; a code-running operator in the {@code metadata} filter; sizes that are not
     * positive, or an overlap not smaller than the size.
     */
    public static String invalidReason(BsonValue chunking) {
        if (chunking == null || chunking.isNull()) {
            return null;
        }
        if (!chunking.isDocument() && !chunking.isArray()) {
            return "chunking must be an object (one rule) or a list of objects (one rule each)";
        }
        var items = asList(chunking);
        for (int i = 0;i < items.size();i++) {
            var name = chunking.isArray() ? "chunking[" + i + "]" : "chunking";
            if (!items.get(i).isDocument()) {
                return name + " must be an object with target-collection";
            }
            var reason = invalidRuleReason(name, items.get(i).asDocument());
            if (reason != null) {
                return reason;
            }
        }
        return null;
    }

    private static String invalidRuleReason(String name, BsonDocument rule) {
        for (var key : rule.keySet()) {
            if (EMBEDDING_KEYS.contains(key)) {
                return name + ": '" + key + "' does not belong in a chunking rule: the chunks are embedded by the "
                        + "vectorSearch rules of the target collection, set it there";
            }
            if (!RULE_KEYS.contains(key)) {
                return name + ": unknown key '" + key + "', the keys are " + RULE_KEYS.stream().sorted().toList();
            }
        }
        if (str(rule.get(TARGET_COLLECTION)) == null) {
            return name + " needs a non-blank string target-collection";
        }
        for (var key : List.of(NAME)) {
            var v = rule.get(key);
            if (v != null && !v.isNull() && str(v) == null) {
                return name + ": " + key + " must be a non-blank string";
            }
        }
        for (var key : List.of(CHUNK_SIZE)) {
            var v = rule.get(key);
            if (v != null && !v.isNull() && !(v.isNumber() && v.asNumber().intValue() > 0)) {
                return name + ": " + key + " must be a positive number";
            }
        }
        var splitter = rule.get(SPLITTER);
        if (splitter != null && !splitter.isNull()
                && !(str(splitter) != null && SPLITTERS.contains(str(splitter).toLowerCase(Locale.ROOT)))) {
            return name + ": splitter must be one of " + SPLITTERS.stream().sorted().toList();
        }
        var overlap = rule.get(CHUNK_OVERLAP);
        if (overlap != null && !overlap.isNull()) {
            if (!(overlap.isNumber() && overlap.asNumber().intValue() >= 0)) {
                return name + ": chunk-overlap must be zero or a positive number";
            }
            var size = rule.get(CHUNK_SIZE);
            if (size != null && size.isNumber() && overlap.asNumber().intValue() >= size.asNumber().intValue()) {
                return name + ": chunk-overlap must be smaller than chunk-size";
            }
        }
        var filter = rule.get(FILTER);
        if (filter == null || filter.isNull()) {
            return null;
        }
        if (!filter.isDocument()) {
            return name + ": filter must be an object with contentType, extension and metadata, each optional";
        }
        for (var key : filter.asDocument().keySet()) {
            if (!FILTER_KEYS.contains(key)) {
                return name + ": unknown filter key '" + key + "', the keys are " + FILTER_KEYS.stream().sorted().toList();
            }
        }
        for (var key : List.of(CONTENT_TYPE, EXTENSION)) {
            var v = filter.asDocument().get(key);
            if (v != null && strings(v) == null) {
                return name + ": filter." + key + " must be a string or a list of strings";
            }
        }
        var metadata = filter.asDocument().get(METADATA);
        if (metadata != null) {
            if (!metadata.isDocument()) {
                return name + ": filter.metadata must be a MongoDB filter, an object";
            }
            var code = codeOperatorIn(metadata);
            if (code != null) {
                return name + ": filter.metadata may not use " + code;
            }
        }
        return null;
    }

    /** The filter on the file's {@code metadata} as a filter on the GridFS files document: each field under {@code metadata.}. */
    public static BsonDocument metadataQuery(BsonDocument metadataFilter) {
        var out = new BsonDocument();
        for (var e : metadataFilter.entrySet()) {
            var key = e.getKey();
            if (("$and".equals(key) || "$or".equals(key) || "$nor".equals(key)) && e.getValue().isArray()) {
                var clauses = new BsonArray();
                for (var clause : e.getValue().asArray()) {
                    clauses.add(clause.isDocument() ? metadataQuery(clause.asDocument()) : clause);
                }
                out.put(key, clauses);
            } else if (key.startsWith("$")) {
                out.put(key, e.getValue());
            } else {
                out.put(METADATA + "." + key, e.getValue());
            }
        }
        return out;
    }

    static boolean contentTypeMatches(String pattern, String contentType) {
        if (contentType == null) {
            return false;
        }
        var type = contentType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        var p = pattern.strip().toLowerCase(Locale.ROOT);
        return p.endsWith("*") ? type.startsWith(p.substring(0, p.length() - 1)) : type.equals(p);
    }

    static boolean extensionMatches(String extension, String filename) {
        if (filename == null) {
            return false;
        }
        var e = extension.strip().toLowerCase(Locale.ROOT);
        return filename.toLowerCase(Locale.ROOT).endsWith(e.startsWith(".") ? e : "." + e);
    }

    private static ChunkingRule ruleOf(BsonDocument doc) {
        var filter = doc.get(FILTER) != null && doc.get(FILTER).isDocument() ? doc.getDocument(FILTER) : new BsonDocument();
        var contentTypes = filter.containsKey(CONTENT_TYPE) ? strings(filter.get(CONTENT_TYPE)) : List.<String>of();
        var extensions = filter.containsKey(EXTENSION) ? strings(filter.get(EXTENSION)) : List.<String>of();
        var metadata = filter.get(METADATA) != null && filter.get(METADATA).isDocument() ? filter.getDocument(METADATA) : null;
        var splitter = str(doc.get(SPLITTER));
        return new ChunkingRule(str(doc.get(NAME)), contentTypes, extensions, metadata, str(doc.get(TARGET_COLLECTION)),
                positive(doc.get(CHUNK_SIZE)), nonNegative(doc.get(CHUNK_OVERLAP)), splitter == null ? "auto" : splitter.toLowerCase(Locale.ROOT));
    }

    private static String codeOperatorIn(BsonValue v) {
        if (v.isDocument()) {
            for (var e : v.asDocument().entrySet()) {
                if (CODE_OPERATORS.contains(e.getKey())) {
                    return e.getKey();
                }
                var inner = codeOperatorIn(e.getValue());
                if (inner != null) {
                    return inner;
                }
            }
        } else if (v.isArray()) {
            for (var item : v.asArray()) {
                var inner = codeOperatorIn(item);
                if (inner != null) {
                    return inner;
                }
            }
        }
        return null;
    }

    /** A string as a list of one, a list of strings as is, anything else null. */
    private static List<String> strings(BsonValue v) {
        if (v.isString()) {
            return List.of(v.asString().getValue());
        }
        if (v.isArray() && v.asArray().stream().allMatch(BsonValue::isString)) {
            return v.asArray().stream().map(s -> s.asString().getValue()).toList();
        }
        return null;
    }

    private static List<BsonValue> asList(BsonValue v) {
        if (v == null) {
            return List.of();
        }
        if (v.isDocument()) {
            return List.of(v);
        }
        return v.isArray() ? v.asArray().getValues() : List.of();
    }

    private static Integer positive(BsonValue v) {
        return v != null && v.isNumber() && v.asNumber().intValue() > 0 ? Integer.valueOf(v.asNumber().intValue()) : null;
    }

    private static Integer nonNegative(BsonValue v) {
        return v != null && v.isNumber() && v.asNumber().intValue() >= 0 ? Integer.valueOf(v.asNumber().intValue()) : null;
    }

    private static String str(BsonValue v) {
        return v != null && v.isString() && !v.asString().getValue().isBlank() ? v.asString().getValue() : null;
    }
}
