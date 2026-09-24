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
package org.restheart.ai.interceptors;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.types.ObjectId;
import org.restheart.ai.chunking.CodeAwareSplitter;
import org.restheart.ai.chunking.CodeLanguage;
import org.restheart.ai.util.BucketChunkingConfig;
import org.restheart.ai.util.BucketChunkingConfig.ChunkingRule;
import org.restheart.ai.util.RequestOverrides;
import org.restheart.exchange.ExchangeKeys.TYPE;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.exchange.Request;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.ACLRegistry;
import org.restheart.utils.BsonUtils;
import org.restheart.utils.InProcessDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClient;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.model.Filters;

import io.undertow.util.AttachmentKey;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;

/**
 * Splits the files uploaded to a GridFS bucket into text chunks and writes them to a collection,
 * as the bucket's {@code chunking} rules say (#754). It computes no vectors: the collection the
 * chunks go to embeds them with its own {@code vectorSearch} rules, as every collection does.
 *
 * <h2>Rules per bucket</h2>
 * <p>Only a bucket with {@code chunking} in its metadata is chunked; a bucket without is never
 * touched. Each rule has a filter on the file (detected content type, filename extension, a
 * MongoDB filter on the file's {@code metadata}), a {@code target-collection}, and optionally
 * {@code chunk-size}, {@code chunk-overlap} and {@code splitter}; the first rule the file matches
 * is applied, and a file that matches none is not chunked. See {@link BucketChunkingConfig}.
 *
 * <pre>{@code
 * PATCH /mydb/docs.files
 * { "chunking": [
 *     { "name": "manuals", "filter": { "contentType": ["application/pdf"] }, "target-collection": "manuals_chunks" },
 *     { "name": "code", "filter": { "extension": [".java", ".py"] }, "target-collection": "code_chunks", "splitter": "text" } ] }
 *
 * PATCH /mydb/manuals_chunks
 * { "vectorSearch": [ { "textField": "text", "embeddingField": "vector",
 *     "provider": "voyageContextualEmbeddingProvider", "model": "voyage-context-4", "groupBy": "fileId" } ] }
 * }</pre>
 *
 * <h2>The write</h2>
 * <p>All the chunks of a file are written with one bulk {@code POST}, through RESTHeart's own
 * handler chain, in process ({@link InProcessDispatcher}), with the parameters the upload request
 * carries. One request, so the target collection's embedding rules see every chunk of the file
 * together: a rule with {@code "groupBy": "fileId"} embeds them as one document with a contextual
 * model. A collection that does not exist is created, empty, and so stores the chunks without
 * vectors until it is given a rule.
 *
 * <p>The account that uploaded the file needs to be able to write to the bucket, not to the chunks
 * collection: if a bucket asks for chunking, the chunking is done. The chunker's own requests are
 * signed in as that account and authorized by a rule in {@link ACLRegistry} that holds only for
 * an in-process exchange this interceptor marked, a mark that cannot come from the wire. Vetoes
 * still apply.
 *
 * <h2>The lifecycle of the chunks</h2>
 * <p>A file replaced with {@code PUT} has its chunks of before deleted first; a deleted file, one
 * by one or in bulk, has its chunks deleted. They are looked for in every target collection the
 * bucket's rules name, by {@code source}, which is the database, the bucket and the file id.
 *
 * <h2>Configuration</h2>
 * <pre>{@code
 * documentChunkingInterceptor:
 *   enabled: false      # must be explicitly enabled
 *   chunk-size: 1000    # default for a rule that sets none
 *   chunk-overlap: 200  # default for a rule that sets none
 * }</pre>
 *
 * <p>A multi-tenant deployment may attach {@link RequestOverrides#CHUNK_SIZE} and
 * {@link RequestOverrides#CHUNK_OVERLAP} per request, as the defaults of that tenant.
 *
 * <h2>A chunk</h2>
 * <pre>{@code
 * { "_id": ObjectId, "source": "mydb/docs.files/<fileId>", "fileId": <id>, "chunkIndex": 0,
 *   "text": "…", "filename": "manual.pdf", "contentType": "application/pdf",
 *   "metadata": { …the file's metadata… }, "rule": "manuals",
 *   "vector": [ … ] }   // written by the collection's embedding rules, when it has one on text
 * }</pre>
 *
 * <p>Source files are cut at function and class boundaries by {@link CodeAwareSplitter} unless the
 * rule's {@code splitter} is {@code text}; code gets no overlap unless the rule sets one.
 */
@RegisterPlugin(
        name = "documentChunkingInterceptor",
        description = "Splits files uploaded to a GridFS bucket into text chunks and writes them to a collection, as the bucket's chunking rules say",
        interceptPoint = InterceptPoint.RESPONSE,
        requiresContent = false,
        enabledByDefault = false
)
public class DocumentChunkingInterceptor implements MongoInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentChunkingInterceptor.class);

    /** Marks an in-process exchange as the chunker's own write: set only by {@link #dispatch}, never from the wire. */
    static final AttachmentKey<Boolean> CHUNKER_WRITE = AttachmentKey.create(Boolean.class);

    // defaults for a rule that sets none; overridden per request via RequestOverrides
    private int defaultChunkSize = 1000;
    private int defaultChunkOverlap = 200;

    @Inject("mclient")
    private MongoClient mclient;

    @Inject("config")
    private Map<String, Object> config;

    @Inject("in-process-dispatcher")
    private InProcessDispatcher dispatcher;

    @Inject("acl-registry")
    private ACLRegistry aclRegistry;

    @OnInit
    public void setup() {
        this.defaultChunkSize = argOrDefault(config, "chunk-size", 1000);
        this.defaultChunkOverlap = argOrDefault(config, "chunk-overlap", 200);
        // the chunker's writes go through whatever the uploader may do on the chunks collection
        aclRegistry.registerAllow(r -> Boolean.TRUE.equals(r.getExchange().getAttachment(CHUNKER_WRITE))
                && Boolean.TRUE.equals(r.getExchange().getAttachment(InProcessDispatcher.IN_PROCESS)));
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        if (response.isInError() || response.getStatusCode() >= 300 || !BucketChunkingConfig.declares(request.getCollectionProps())) {
            return false;
        }
        return isUpload(request) || isFileDelete(request) || isBulkFilesDelete(request);
    }

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var rules = BucketChunkingConfig.rules(request.getCollectionProps());
        var targets = targetsOf(rules);
        if (isFileDelete(request)) {
            deleteChunks(request, response, targets, List.of(request.getDocumentId()));
        } else if (isBulkFilesDelete(request)) {
            deleteChunks(request, response, targets, deletedOf(request));
        } else {
            chunk(request, response, rules, targets);
        }
    }

    static boolean isUpload(MongoRequest request) {
        return request.isFilesBucket() && request.isPost() || request.isFile() && request.isPut();
    }

    static boolean isFileDelete(MongoRequest request) {
        return request.isFile() && request.isDelete();
    }

    static boolean isBulkFilesDelete(MongoRequest request) {
        return request.getType() == TYPE.BULK_FILES && request.isDelete();
    }

    // -------------------------------------------------------------------------

    private void chunk(MongoRequest request, MongoResponse response, List<ChunkingRule> rules, List<String> targets) {
        var dbName = request.getDBName();
        var collName = request.getCollectionName(); // e.g. "docs.files"
        var bucketName = collName.endsWith(".files") ? collName.substring(0, collName.length() - 6) : collName;

        var fileId = resolveFileId(request, response);
        if (fileId == null) {
            LOGGER.warn("documentChunkingInterceptor: could not determine file id for {}/{}", dbName, collName);
            return;
        }

        byte[] fileBytes;
        String filename;
        BsonDocument fileMetadata;
        try {
            var bucket = GridFSBuckets.create(mclient.getDatabase(dbName), bucketName);
            try (var in = bucket.openDownloadStream(fileId)) {
                filename = in.getGridFSFile().getFilename();
                var md = in.getGridFSFile().getMetadata();
                fileMetadata = md == null ? null : BsonDocument.parse(md.toJson());
                fileBytes = in.readAllBytes();
            }
        } catch (Exception e) {
            LOGGER.warn("documentChunkingInterceptor: could not download file {} from {}/{}: {}", fileId, dbName, bucketName, e.getMessage());
            return;
        }

        // a replaced file's chunks of before go first, wherever its rule of then sent them
        if (request.isPut()) {
            deleteChunks(request, response, targets, List.of(fileId));
        }

        var contentType = detect(fileBytes, filename);
        var rule = ruleFor(rules, contentType, filename, dbName, collName, fileId);
        if (rule == null) {
            LOGGER.debug("documentChunkingInterceptor: file {} in {}/{} matches no chunking rule, not chunked", fileId, dbName, bucketName);
            return;
        }

        String text;
        try {
            var handler = new BodyContentHandler(-1);
            new AutoDetectParser().parse(new ByteArrayInputStream(fileBytes), handler, new Metadata(), new ParseContext());
            text = handler.toString();
        } catch (Exception e) {
            LOGGER.warn("documentChunkingInterceptor: Tika could not extract text from file {} in {}/{}: {}", fileId, dbName, bucketName, e.getMessage());
            return;
        }
        if (text == null || text.isBlank()) {
            LOGGER.debug("documentChunkingInterceptor: no text extracted from file {} in {}/{}", fileId, dbName, bucketName);
            return;
        }

        var chunkSize = rule.chunkSize() != null ? rule.chunkSize() : RequestOverrides.intVal(request, RequestOverrides.CHUNK_SIZE, defaultChunkSize);
        var chunkOverlap = rule.chunkOverlap() != null ? rule.chunkOverlap() : RequestOverrides.intVal(request, RequestOverrides.CHUNK_OVERLAP, defaultChunkOverlap);
        // code keeps its zero overlap unless the rule asks for one
        var codeOverlap = rule.chunkOverlap() != null ? rule.chunkOverlap() : 0;
        var chunks = chunkText(text.strip(), filename, chunkSize, chunkOverlap, rule.splitter(), codeOverlap);
        if (chunks.isEmpty()) {
            return;
        }

        var source = sourceOf(dbName, collName, fileId);
        var documents = new ArrayList<BsonDocument>(chunks.size());
        for (int i = 0;i < chunks.size();i++) {
            var chunk = new BsonDocument()
                    .append("_id", new BsonObjectId(new ObjectId()))
                    .append("source", new BsonString(source))
                    .append("fileId", fileId)
                    .append("chunkIndex", new BsonInt32(i))
                    .append("text", new BsonString(chunks.get(i)));
            // the file's own description, so a search can narrow by what the application set at upload
            if (filename != null) {
                chunk.append("filename", new BsonString(filename));
            }
            if (contentType != null) {
                chunk.append("contentType", new BsonString(contentType));
            }
            if (fileMetadata != null) {
                chunk.append("metadata", fileMetadata.clone());
            }
            if (rule.name() != null) {
                chunk.append("rule", new BsonString(rule.name()));
            }
            documents.add(chunk);
        }

        write(request, response, rule.targetCollection(), fileId, documents);
    }

    /**
     * Writes a file's chunks with one bulk {@code POST} through the handler chain, so the target
     * collection's embedding rules see them together. A collection that does not exist is created
     * empty. A refused write, or the warnings of the embedding rules, reach the upload's response.
     */
    private void write(MongoRequest request, MongoResponse response, String targetCollection, BsonValue fileId, List<BsonDocument> documents) {
        var target = pathOf(request, targetCollection);
        try {
            var body = new BsonArray(documents);
            var answer = dispatch(request, Methods.POST, target, body);
            if (answer.status() == 404) {
                dispatch(request, Methods.PUT, target, new BsonDocument());
                answer = dispatch(request, Methods.POST, target, body);
            }
            if (answer.status() >= 400) {
                warn(response, "chunks of file " + idString(fileId) + " not written to '" + targetCollection + "': HTTP " + answer.status() + " " + answer.bodyAsString());
                return;
            }
            copyWarnings(answer, response);
            LOGGER.info("documentChunkingInterceptor: wrote {} chunks of file {} to {}", documents.size(), idString(fileId), target);
        } catch (Exception e) {
            LOGGER.error("documentChunkingInterceptor: could not write the chunks of file {} to {}", idString(fileId), target, e);
            warn(response, "chunks of file " + idString(fileId) + " not written to '" + targetCollection + "': " + e.getMessage());
        }
    }

    /** Deletes the chunks of the given files from every target collection of the bucket. */
    private void deleteChunks(MongoRequest request, MongoResponse response, List<String> targets, List<BsonValue> fileIds) {
        if (fileIds.isEmpty()) {
            return;
        }
        var sources = new BsonArray();
        fileIds.forEach(id -> sources.add(new BsonString(sourceOf(request.getDBName(), request.getCollectionName(), id))));
        var filter = new BsonDocument("source", new BsonDocument("$in", sources));
        for (var target : targets) {
            try {
                var answer = dispatch(request, Methods.DELETE, pathOf(request, target) + "/*?filter=" + encode(BsonUtils.toJson(filter)), null);
                // 404: the collection was never written to, so there is nothing to delete
                if (answer.status() >= 400 && answer.status() != 404) {
                    warn(response, "chunks not deleted from '" + target + "': HTTP " + answer.status() + " " + answer.bodyAsString());
                }
            } catch (Exception e) {
                LOGGER.error("documentChunkingInterceptor: could not delete chunks from {}", target, e);
                warn(response, "chunks not deleted from '" + target + "': " + e.getMessage());
            }
        }
    }

    /** The files a bulk delete removed: of those it could have, the ones no longer there. */
    @SuppressWarnings("unchecked")
    private List<BsonValue> deletedOf(MongoRequest request) {
        List<BsonValue> candidates = request.attachedParam(ChunkedFilesDeleteCollector.CANDIDATES);
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        var files = mclient.getDatabase(request.getDBName()).getCollection(request.getCollectionName(), BsonDocument.class);
        var stillThere = new ArrayList<BsonValue>();
        files.find(Filters.in("_id", candidates)).projection(new BsonDocument("_id", new BsonInt32(1)))
                .forEach(d -> stillThere.add(d.get("_id")));
        return candidates.stream().filter(id -> !stillThere.contains(id)).toList();
    }

    /** A request through the handler chain, in process, signed in as the uploader and marked as the chunker's. */
    private InProcessDispatcher.Response dispatch(MongoRequest request, HttpString method, String target, BsonValue body) throws Exception {
        var headers = new HeaderMap();
        var host = request.getExchange().getRequestHeaders().getFirst(Headers.HOST);
        if (host != null) {
            headers.put(Headers.HOST, host);
        }
        headers.put(Headers.CONTENT_TYPE, "application/json");
        var bytes = body == null ? new byte[0] : BsonUtils.toJson(body).getBytes(StandardCharsets.UTF_8);
        var principal = request.getAuthenticatedAccount();
        Map<String, Object> attached = request.getExchange().getAttachment(Request.ATTACHED_PARAMS_KEY);
        return dispatcher.dispatch(method, target, headers, bytes, exchange -> {
            exchange.putAttachment(CHUNKER_WRITE, Boolean.TRUE);
            if (principal != null) {
                exchange.putAttachment(InProcessDispatcher.PRINCIPAL, principal);
            }
            if (attached != null && !attached.isEmpty()) {
                exchange.putAttachment(Request.ATTACHED_PARAMS_KEY, new HashMap<>(attached));
            }
        });
    }

    private static void copyWarnings(InProcessDispatcher.Response answer, MongoResponse response) {
        var written = answer.bodyAsString();
        if (written == null || written.isBlank()) {
            return;
        }
        try {
            if (BsonDocument.parse(written).get("_warnings") instanceof BsonArray warnings) {
                warnings.forEach(w -> response.addWarning(w.isString() ? w.asString().getValue() : w.toString()));
            }
        } catch (Exception e) {
            // not a document: nothing to copy
        }
    }

    private static void warn(MongoResponse response, String message) {
        LOGGER.warn("documentChunkingInterceptor: {}", message);
        response.addWarning(message);
    }

    /** The target collections the bucket's rules name, each once. */
    static List<String> targetsOf(List<ChunkingRule> rules) {
        var targets = new LinkedHashSet<String>();
        rules.forEach(r -> targets.add(r.targetCollection()));
        return List.copyOf(targets);
    }

    /** The path of a collection of the bucket's database, as the request addresses the bucket: mount included. */
    static String pathOf(MongoRequest request, String collection) {
        var path = request.getPath();
        var at = path.lastIndexOf("/" + request.getCollectionName());
        return (at >= 0 ? path.substring(0, at) : "") + "/" + encode(collection);
    }

    /** Where a chunk comes from: database, bucket and file id. */
    static String sourceOf(String db, String bucketColl, BsonValue fileId) {
        return db + "/" + bucketColl + "/" + idString(fileId);
    }

    static String idString(BsonValue id) {
        if (id == null) {
            return "null";
        }
        if (id.isObjectId()) {
            return id.asObjectId().getValue().toHexString();
        }
        return id.isString() ? id.asString().getValue() : BsonUtils.toJson(id);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * The first rule the file matches: its detected type and its name against each rule's filter,
     * then, only for a rule that also filters on {@code metadata}, the file's {@code metadata}
     * against that filter, in the database, where it is stored. Null when none matches.
     */
    private ChunkingRule ruleFor(List<ChunkingRule> rules, String contentType, String filename, String dbName, String filesColl, BsonValue fileId) {
        for (var rule : rules) {
            if (!rule.matches(contentType, filename)) {
                continue;
            }
            if (rule.metadata() != null && !metadataMatches(rule.metadata(), dbName, filesColl, fileId)) {
                continue;
            }
            return rule;
        }
        return null;
    }

    /** The type Tika detects from the bytes, and the name when the bytes do not say. */
    static String detect(byte[] fileBytes, String filename) {
        try {
            return new Tika().detect(fileBytes, filename);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean metadataMatches(BsonDocument metadataFilter, String dbName, String filesColl, BsonValue fileId) {
        try {
            return mclient.getDatabase(dbName)
                    .getCollection(filesColl, BsonDocument.class)
                    .countDocuments(Filters.and(Filters.eq("_id", fileId), BucketChunkingConfig.metadataQuery(metadataFilter))) > 0;
        } catch (Exception e) {
            LOGGER.warn("documentChunkingInterceptor: could not match the metadata filter of file {} in {}/{}: {}", idString(fileId), dbName, filesColl, e.getMessage());
            return false;
        }
    }

    private BsonValue resolveFileId(MongoRequest request, MongoResponse response) {
        if (request.isPut()) {
            return request.getDocumentId();
        }
        var opResult = response.getDbOperationResult();
        return opResult != null ? opResult.getNewId() : null;
    }

    /**
     * Chunks {@code text}, using {@link CodeAwareSplitter} at function/class boundaries
     * when {@code filename}'s extension identifies it as source code, and falling back
     * to plain character-window {@link #splitIntoChunks} for everything else (prose,
     * unrecognized extensions, or a missing filename).
     *
     * <p>Code chunking always uses zero overlap, regardless of the configured/overridden
     * {@code chunk-overlap}: a structural chunk (a function, a method) is already a
     * cohesive unit, so duplicating trailing text from the previous one adds noise
     * without adding context, unlike prose chunked at arbitrary character counts.
     */
    static List<String> chunkText(String text, String filename, int size, int overlap) {
        return chunkText(text, filename, size, overlap, "auto", 0);
    }

    /**
     * Chunks {@code text} with the splitter a bucket's rule chooses: {@code text} always cuts
     * character windows, for a model that wants them even for code; {@code auto} and {@code code}
     * cut at function and class boundaries when {@code filename} names a recognized language,
     * with {@code codeOverlap}, and character windows otherwise.
     */
    static List<String> chunkText(String text, String filename, int size, int overlap, String splitter, int codeOverlap) {
        var language = "text".equals(splitter) ? null : CodeLanguage.fromFilename(filename);
        return switch (language) {
            case BRACE_BASED -> CodeAwareSplitter.splitBraceBased(text, size, codeOverlap);
            case INDENT_BASED -> CodeAwareSplitter.splitIndentBased(text, size, codeOverlap);
            case null -> splitIntoChunks(text, size, overlap);
        };
    }

    /**
     * Splits {@code text} into chunks of at most {@code size} characters with
     * {@code overlap} characters of context carried over between consecutive chunks.
     */
    static List<String> splitIntoChunks(String text, int size, int overlap) {
        var chunks = new ArrayList<String>();
        if (text == null || text.isEmpty() || size <= 0) return chunks;

        int start = 0;
        int len = text.length();
        while (start < len) {
            int end = Math.min(start + size, len);
            if (end < len) {
                int boundary = text.lastIndexOf(' ', end);
                if (boundary > start) end = boundary;
            }
            chunks.add(text.substring(start, end).strip());
            int step = end - start - overlap;
            if (step <= 0) step = size;
            start += step;
        }
        return chunks;
    }
}
