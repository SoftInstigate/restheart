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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.tika.Tika;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.bson.BsonArray;
import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.types.ObjectId;
import org.restheart.ai.chunking.ChunkingGate;
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
import org.restheart.utils.ThreadsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClient;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import io.undertow.security.idm.Account;

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
 * <h2>In the background (#758)</h2>
 * <p>With {@code async}, the upload answers once the file is stored, and the file is chunked on a
 * virtual thread afterwards: extraction and embedding can take seconds on a large PDF. What the
 * chunker did is written on the file's own document, beside {@code metadata}:
 * <pre>{@code
 * "chunking": { "status": "pending" | "done" | "failed" | "skipped", "job": ObjectId,
 *               "rule": "manuals", "chunks": 423, "warnings": [ … ], "at": Date }
 * }</pre>
 * {@code pending} is written before the upload answers, {@code running} when the job starts. A job this
 * node, or another, lost by stopping is resumed: every {@code resume-scan-minutes} the buckets with
 * rules are scanned for files still {@code pending} or {@code running} after
 * {@code resume-after-minutes}, and each is claimed atomically with a new job id and chunked again.
 * The pending status keeps what that needs, never a secret: the uploader's name and roles, the host
 * and mount, the sizes; a resumed job's requests get the tenant's overrides from the deployment's
 * own interceptors, by host, as any request does. Without {@code async} the upload waits as
 * before, its response carries the warnings, and the status is written too. At most
 * {@code max-concurrent} files are chunked at once on the node, and at most the per-request
 * {@link RequestOverrides#CHUNKING_MAX_CONCURRENT} of one database, see {@link ChunkingGate}. A file
 * replaced or deleted while its chunking runs supersedes it: the late job writes nothing, or takes
 * back what it wrote.
 *
 * <h2>Configuration</h2>
 * <pre>{@code
 * documentChunkingInterceptor:
 *   enabled: false      # must be explicitly enabled
 *   chunk-size: 1000    # default for a rule that sets none
 *   chunk-overlap: 200  # default for a rule that sets none
 *   async: false        # chunk after the upload has answered
 *   max-concurrent: 2   # files chunked at once on the node, when async
 *   resume-after-minutes: 15  # a job pending or running longer than this was lost, and is resumed
 *   resume-scan-minutes: 5    # how often lost jobs are looked for; 0 turns it off
 * }</pre>
 *
 * <p>A multi-tenant deployment may attach {@link RequestOverrides#CHUNK_SIZE},
 * {@link RequestOverrides#CHUNK_OVERLAP}, {@link RequestOverrides#CHUNKING_ASYNC} and
 * {@link RequestOverrides#CHUNKING_MAX_CONCURRENT} per request, as the settings of that tenant.
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
    private boolean defaultAsync = false;
    private int maxConcurrent = 2;
    private ChunkingGate gate = new ChunkingGate(2);
    private int resumeAfterMinutes = 15;
    private int resumeScanMinutes = 5;

    /** The field of a GridFS files document that says what the chunker did with the file. */
    static final String STATUS_FIELD = "chunking";

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
        this.defaultAsync = argOrDefault(config, "async", false);
        this.maxConcurrent = Math.max(1, argOrDefault(config, "max-concurrent", 2));
        this.gate = new ChunkingGate(maxConcurrent);
        this.resumeAfterMinutes = Math.max(1, argOrDefault(config, "resume-after-minutes", 15));
        this.resumeScanMinutes = argOrDefault(config, "resume-scan-minutes", 5);
        if (resumeScanMinutes > 0) {
            ThreadsUtils.virtualThreadsExecutor().execute(this::resumeLoop);
        }
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
        Consumer<String> warn = message -> warn(response, message);
        if (isFileDelete(request)) {
            deleteChunks(Caller.of(request), request.getDBName(), request.getCollectionName(), targets, List.of(request.getDocumentId()), warn);
        } else if (isBulkFilesDelete(request)) {
            deleteChunks(Caller.of(request), request.getDBName(), request.getCollectionName(), targets, deletedOf(request), warn);
        } else {
            var fileId = resolveFileId(request, response);
            if (fileId == null) {
                LOGGER.warn("documentChunkingInterceptor: could not determine file id for {}/{}", request.getDBName(), request.getCollectionName());
                return;
            }
            var async = RequestOverrides.boolVal(request, RequestOverrides.CHUNKING_ASYNC, defaultAsync);
            var upload = new Upload(Caller.of(request), request.getDBName(), request.getCollectionName(), fileId, request.isPut(), rules, targets,
                    RequestOverrides.intVal(request, RequestOverrides.CHUNK_SIZE, defaultChunkSize),
                    RequestOverrides.intVal(request, RequestOverrides.CHUNK_OVERLAP, defaultChunkOverlap),
                    async ? new ObjectId() : null);
            if (async) {
                var dbLimit = Math.max(1, RequestOverrides.intVal(request, RequestOverrides.CHUNKING_MAX_CONCURRENT, maxConcurrent));
                // pending before the upload answers, so whoever reads the file right after sees it; with what a
                // resumed job needs if this node stops before the job is done (no secret: the overrides are not kept)
                writeStatus(upload, new Outcome("pending", null, null), List.of(), resumeOf(upload, dbLimit));
                ThreadsUtils.virtualThreadsExecutor().execute(() -> chunkInBackground(upload, dbLimit));
            } else {
                var warnings = new ArrayList<String>();
                var outcome = chunk(upload, warnings::add);
                warnings.forEach(w -> response.addWarning(w));
                writeStatus(upload, outcome, warnings, null);
            }
        }
    }

    /** Who uploaded, and where: what the chunker's own requests carry, captured while the exchange is there. */
    record Caller(String host, Account principal, Map<String, Object> attached, String base) {
        static Caller of(MongoRequest request) {
            Map<String, Object> attached = request.getExchange().getAttachment(Request.ATTACHED_PARAMS_KEY);
            return new Caller(request.getExchange().getRequestHeaders().getFirst(Headers.HOST), request.getAuthenticatedAccount(),
                    attached == null ? Map.of() : java.util.Collections.unmodifiableMap(new HashMap<>(attached)), baseOf(request));
        }
    }

    /** The part of the request's path before the bucket: the mount the other collections of its database are under. */
    static String baseOf(MongoRequest request) {
        var path = request.getPath();
        var at = path.lastIndexOf("/" + request.getCollectionName());
        return at >= 0 ? path.substring(0, at) : "";
    }

    /** A file to chunk, with all it needs once the upload has answered; {@code job} only when in the background. */
    record Upload(Caller caller, String db, String filesColl, BsonValue fileId, boolean replaced, List<ChunkingRule> rules,
            List<String> targets, int chunkSize, int chunkOverlap, ObjectId job) {
        String bucket() {
            return filesColl.endsWith(".files") ? filesColl.substring(0, filesColl.length() - 6) : filesColl;
        }
    }

    /** What the chunker did with a file: the status written on it, the rule applied, how many chunks. */
    record Outcome(String status, String rule, Integer chunks) {
    }

    /** Waits for a slot of its database and of the node, chunks, and writes the outcome on the file. */
    private void chunkInBackground(Upload upload, int dbLimit) {
        var warnings = new ArrayList<String>();
        Outcome outcome;
        try {
            gate.acquire(upload.db(), dbLimit);
            try {
                // superseded while it waited: a newer upload, a deletion, or another node that resumed it
                if (!current(upload)) {
                    return;
                }
                writeStatus(upload, new Outcome("running", null, null), List.of(), null);
                outcome = chunk(upload, warnings::add);
            } finally {
                gate.release(upload.db());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            warnings.add("chunking interrupted");
            outcome = new Outcome("failed", null, null);
        } catch (Throwable t) {
            LOGGER.error("documentChunkingInterceptor: chunking file {} in {}/{} failed", idString(upload.fileId()), upload.db(), upload.filesColl(), t);
            warnings.add("chunking failed: " + t.getMessage());
            outcome = new Outcome("failed", null, null);
        }
        warnings.forEach(w -> LOGGER.warn("documentChunkingInterceptor: {}", w));
        writeStatus(upload, outcome, warnings, null);
    }

    /**
     * Writes what the chunker did on the file's own document. In the background, only while the job
     * is still the file's: a newer upload of the same file owns the field from its own pending on.
     */
    private void writeStatus(Upload upload, Outcome outcome, List<String> warnings, BsonDocument resume) {
        var status = new BsonDocument("status", new BsonString(outcome.status()));
        if (upload.job() != null) {
            status.append("job", new BsonObjectId(upload.job()));
        }
        if (outcome.rule() != null) {
            status.append("rule", new BsonString(outcome.rule()));
        }
        if (outcome.chunks() != null) {
            status.append("chunks", new BsonInt32(outcome.chunks()));
        }
        var ws = new BsonArray();
        warnings.forEach(w -> ws.add(new BsonString(w)));
        status.append("warnings", ws).append("at", new BsonDateTime(System.currentTimeMillis()));
        if (resume != null) {
            status.append("resume", resume);
        }
        var filter = "pending".equals(outcome.status()) || upload.job() == null
                ? Filters.eq("_id", upload.fileId())
                : Filters.and(Filters.eq("_id", upload.fileId()), Filters.eq(STATUS_FIELD + ".job", upload.job()));
        // running keeps the pending status's resume data: a job that dies running is resumed like a queued one
        var update = "running".equals(outcome.status())
                ? Updates.combine(Updates.set(STATUS_FIELD + ".status", "running"), Updates.set(STATUS_FIELD + ".at", new BsonDateTime(System.currentTimeMillis())))
                : Updates.set(STATUS_FIELD, status);
        try {
            mclient.getDatabase(upload.db()).getCollection(upload.filesColl(), BsonDocument.class)
                    .updateOne(filter, update);
        } catch (Exception e) {
            LOGGER.warn("documentChunkingInterceptor: could not write the chunking status of file {} in {}/{}: {}",
                    idString(upload.fileId()), upload.db(), upload.filesColl(), e.getMessage());
        }
    }

    /** What a job needs to be resumed by any node: who uploaded, where, and the sizes the request resolved; never a secret. */
    static BsonDocument resumeOf(Upload upload, int dbLimit) {
        var resume = new BsonDocument("base", new BsonString(upload.caller().base()))
                .append("chunkSize", new BsonInt32(upload.chunkSize()))
                .append("chunkOverlap", new BsonInt32(upload.chunkOverlap()))
                .append("dbLimit", new BsonInt32(dbLimit));
        if (upload.caller().host() != null) {
            resume.append("host", new BsonString(upload.caller().host()));
        }
        var principal = upload.caller().principal();
        if (principal != null && principal.getPrincipal() != null) {
            var roles = new BsonArray();
            if (principal.getRoles() != null) {
                principal.getRoles().forEach(r -> roles.add(new BsonString(r)));
            }
            resume.append("user", new BsonString(principal.getPrincipal().getName())).append("roles", roles);
        }
        return resume;
    }

    /**
     * Every {@code resume-scan-minutes}, resumes the jobs a node lost: a file still {@code pending} or
     * {@code running} after {@code resume-after-minutes} is not being worked on anywhere, since this
     * node stopped, or another did, with it queued or halfway. The scan looks at the buckets with
     * chunking rules of every database the client sees.
     */
    private void resumeLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(java.time.Duration.ofMinutes(resumeScanMinutes));
                resumeLost();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable t) {
                LOGGER.warn("documentChunkingInterceptor: resuming lost chunking jobs failed: {}", t.getMessage());
            }
        }
    }

    private static final java.util.Set<String> SYSTEM_DBS = java.util.Set.of("admin", "local", "config");

    /** Finds the files whose chunking was lost, claims each one and chunks it again. */
    void resumeLost() {
        var before = new BsonDateTime(System.currentTimeMillis() - java.time.Duration.ofMinutes(resumeAfterMinutes).toMillis());
        for (var db : mclient.listDatabaseNames()) {
            if (SYSTEM_DBS.contains(db)) {
                continue;
            }
            var props = mclient.getDatabase(db).getCollection(org.restheart.exchange.ExchangeKeys.META_COLLNAME, BsonDocument.class);
            var buckets = props.find(Filters.and(Filters.regex("_id", "^_properties\\..*\\.files$"), Filters.exists(BucketChunkingConfig.CHUNKING)));
            for (var bucketProps : buckets) {
                var filesColl = bucketProps.getString("_id").getValue().substring("_properties.".length());
                var rules = BucketChunkingConfig.rules(bucketProps);
                if (rules.isEmpty()) {
                    continue;
                }
                var files = mclient.getDatabase(db).getCollection(filesColl, BsonDocument.class);
                var lost = files.find(Filters.and(Filters.in(STATUS_FIELD + ".status", "pending", "running"), Filters.lt(STATUS_FIELD + ".at", before)))
                        .projection(new BsonDocument(STATUS_FIELD, new BsonInt32(1)));
                for (var file : lost) {
                    resume(db, filesColl, rules, file);
                }
            }
        }
    }

    /**
     * Claims one lost job with a new job id, atomically, so two nodes never resume the same file, and
     * runs it. The old job, if it is somewhere after all, finds itself superseded and writes nothing.
     */
    private void resume(String db, String filesColl, List<ChunkingRule> rules, BsonDocument file) {
        if (!(file.get(STATUS_FIELD) instanceof BsonDocument st) || !(st.get("resume") instanceof BsonDocument resume) || !st.containsKey("job")) {
            return;
        }
        var job = new ObjectId();
        var claimed = mclient.getDatabase(db).getCollection(filesColl, BsonDocument.class).updateOne(
                Filters.and(Filters.eq("_id", file.get("_id")), Filters.eq(STATUS_FIELD + ".job", st.get("job"))),
                Updates.combine(Updates.set(STATUS_FIELD + ".job", new BsonObjectId(job)), Updates.set(STATUS_FIELD + ".status", "pending"),
                        Updates.set(STATUS_FIELD + ".at", new BsonDateTime(System.currentTimeMillis()))));
        if (claimed.getModifiedCount() == 0) {
            return; // another node claimed it, or the file moved on
        }
        Account principal = null;
        if (resume.get("user") instanceof BsonString user) {
            var roles = new java.util.HashSet<String>();
            if (resume.get("roles") instanceof BsonArray rs) {
                rs.forEach(r -> roles.add(r.isString() ? r.asString().getValue() : r.toString()));
            }
            principal = new org.restheart.security.BaseAccount(user.getValue(), roles);
        }
        var caller = new Caller(resume.get("host") instanceof BsonString h ? h.getValue() : null, principal, Map.of(),
                resume.get("base") instanceof BsonString b ? b.getValue() : "");
        // replaced: whatever a job stopped halfway wrote for this file goes first
        var upload = new Upload(caller, db, filesColl, file.get("_id"), true, rules, targetsOf(rules),
                intOf(resume, "chunkSize", defaultChunkSize), intOf(resume, "chunkOverlap", defaultChunkOverlap), job);
        LOGGER.info("documentChunkingInterceptor: resuming the lost chunking of file {} in {}/{}", idString(upload.fileId()), db, filesColl);
        ThreadsUtils.virtualThreadsExecutor().execute(() -> chunkInBackground(upload, intOf(resume, "dbLimit", maxConcurrent)));
    }

    private static int intOf(BsonDocument doc, String key, int defaultValue) {
        return doc.get(key) != null && doc.get(key).isNumber() ? doc.get(key).asNumber().intValue() : defaultValue;
    }

    /** Whether the job is still the file's: the file is there, and no newer upload replaced it. Always true when not in the background. */
    private boolean current(Upload upload) {
        if (upload.job() == null) {
            return true;
        }
        var file = mclient.getDatabase(upload.db()).getCollection(upload.filesColl(), BsonDocument.class)
                .find(Filters.eq("_id", upload.fileId())).projection(new BsonDocument(STATUS_FIELD + ".job", new BsonInt32(1))).first();
        return file != null && file.get(STATUS_FIELD) instanceof BsonDocument st && new BsonObjectId(upload.job()).equals(st.get("job"));
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

    /** Chunks one file as its bucket's rules say; the warnings go to {@code warn}. */
    private Outcome chunk(Upload upload, Consumer<String> warn) {
        var dbName = upload.db();
        var collName = upload.filesColl(); // e.g. "docs.files"
        var bucketName = upload.bucket();
        var fileId = upload.fileId();

        // a replaced file's chunks of before go first, wherever its rule of then sent them
        if (upload.replaced()) {
            deleteChunks(upload.caller(), dbName, collName, upload.targets(), List.of(fileId), warn);
        }

        // the file is streamed from the bucket, never held whole: TikaInputStream marks and resets for the
        // detection, and spools to a temporary file only for a parser that needs random access, as PDF's does
        String filename;
        BsonDocument fileMetadata;
        String contentType;
        ChunkingRule rule;
        String text;
        try (var in = GridFSBuckets.create(mclient.getDatabase(dbName), bucketName).openDownloadStream(fileId);
                var tis = TikaInputStream.get(in)) {
            filename = in.getGridFSFile().getFilename();
            var md = in.getGridFSFile().getMetadata();
            fileMetadata = md == null ? null : BsonDocument.parse(md.toJson());

            contentType = detect(tis, filename);
            rule = ruleFor(upload.rules(), contentType, filename, dbName, collName, fileId);
            if (rule == null) {
                // only the first bytes were read, for the detection
                LOGGER.debug("documentChunkingInterceptor: file {} in {}/{} matches no chunking rule, not chunked", fileId, dbName, bucketName);
                return new Outcome("skipped", null, null);
            }

            try {
                var handler = new BodyContentHandler(-1);
                var tikaMetadata = new Metadata();
                if (filename != null) {
                    tikaMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
                }
                new AutoDetectParser().parse(tis, handler, tikaMetadata, new ParseContext());
                text = handler.toString();
            } catch (Exception e) {
                LOGGER.warn("documentChunkingInterceptor: Tika could not extract text from file {} in {}/{}: {}", fileId, dbName, bucketName, e.getMessage());
                warn.accept("no text could be extracted from file " + idString(fileId) + ": " + e.getMessage());
                return new Outcome("failed", rule.name(), null);
            }
        } catch (Exception e) {
            LOGGER.warn("documentChunkingInterceptor: could not read file {} from {}/{}: {}", fileId, dbName, bucketName, e.getMessage());
            warn.accept("file " + idString(fileId) + " not chunked: it could not be read (" + e.getMessage() + ")");
            return new Outcome("failed", null, null);
        }
        if (text == null || text.isBlank()) {
            LOGGER.debug("documentChunkingInterceptor: no text extracted from file {} in {}/{}", fileId, dbName, bucketName);
            return new Outcome("done", rule.name(), 0);
        }

        var chunkSize = rule.chunkSize() != null ? rule.chunkSize() : upload.chunkSize();
        var chunkOverlap = rule.chunkOverlap() != null ? rule.chunkOverlap() : upload.chunkOverlap();
        // code keeps its zero overlap unless the rule asks for one
        var codeOverlap = rule.chunkOverlap() != null ? rule.chunkOverlap() : 0;
        var chunks = chunkText(text.strip(), filename, chunkSize, chunkOverlap, rule.splitter(), codeOverlap);
        if (chunks.isEmpty()) {
            return new Outcome("done", rule.name(), 0);
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

        // a newer upload of the file, or its deletion, supersedes this job: nothing to write
        if (!current(upload)) {
            return new Outcome("superseded", rule.name(), 0);
        }
        var written = write(upload, rule.targetCollection(), documents, warn);
        // superseded while writing: what this job wrote goes back
        if (written && !current(upload)) {
            takeBack(upload, rule.targetCollection(), documents);
            return new Outcome("superseded", rule.name(), 0);
        }
        return written ? new Outcome("done", rule.name(), documents.size()) : new Outcome("failed", rule.name(), 0);
    }

    /** Removes the chunks a superseded job wrote, by their ids. */
    private void takeBack(Upload upload, String targetCollection, List<BsonDocument> documents) {
        try {
            var ids = new BsonArray();
            documents.forEach(d -> ids.add(d.get("_id")));
            mclient.getDatabase(upload.db()).getCollection(targetCollection, BsonDocument.class).deleteMany(Filters.in("_id", ids));
        } catch (Exception e) {
            LOGGER.warn("documentChunkingInterceptor: could not take back the chunks of superseded file {} from {}: {}", idString(upload.fileId()), targetCollection, e.getMessage());
        }
    }

    /**
     * Writes a file's chunks with one bulk {@code POST} through the handler chain, so the target
     * collection's embedding rules see them together. A collection that does not exist is created
     * empty. A refused write, or the warnings of the embedding rules, reach the upload's response.
     */
    private boolean write(Upload upload, String targetCollection, List<BsonDocument> documents, Consumer<String> warn) {
        var fileId = upload.fileId();
        var target = pathOf(upload.caller().base(), targetCollection);
        try {
            var body = new BsonArray(documents);
            var answer = dispatch(upload.caller(), Methods.POST, target, body);
            if (answer.status() == 404) {
                dispatch(upload.caller(), Methods.PUT, target, new BsonDocument());
                answer = dispatch(upload.caller(), Methods.POST, target, body);
            }
            if (answer.status() >= 400) {
                warn.accept("chunks of file " + idString(fileId) + " not written to '" + targetCollection + "': HTTP " + answer.status() + " " + answer.bodyAsString());
                return false;
            }
            copyWarnings(answer, warn);
            LOGGER.info("documentChunkingInterceptor: wrote {} chunks of file {} to {}", documents.size(), idString(fileId), target);
            return true;
        } catch (Exception e) {
            LOGGER.error("documentChunkingInterceptor: could not write the chunks of file {} to {}", idString(fileId), target, e);
            warn.accept("chunks of file " + idString(fileId) + " not written to '" + targetCollection + "': " + e.getMessage());
            return false;
        }
    }

    /** Deletes the chunks of the given files from every target collection of the bucket. */
    private void deleteChunks(Caller caller, String db, String filesColl, List<String> targets, List<BsonValue> fileIds, Consumer<String> warn) {
        if (fileIds.isEmpty()) {
            return;
        }
        var sources = new BsonArray();
        fileIds.forEach(id -> sources.add(new BsonString(sourceOf(db, filesColl, id))));
        var filter = new BsonDocument("source", new BsonDocument("$in", sources));
        for (var target : targets) {
            try {
                var answer = dispatch(caller, Methods.DELETE, pathOf(caller.base(), target) + "/*?filter=" + encode(BsonUtils.toJson(filter)), null);
                // 404: the collection was never written to, so there is nothing to delete
                if (answer.status() >= 400 && answer.status() != 404) {
                    warn.accept("chunks not deleted from '" + target + "': HTTP " + answer.status() + " " + answer.bodyAsString());
                }
            } catch (Exception e) {
                LOGGER.error("documentChunkingInterceptor: could not delete chunks from {}", target, e);
                warn.accept("chunks not deleted from '" + target + "': " + e.getMessage());
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
    private InProcessDispatcher.Response dispatch(Caller caller, HttpString method, String target, BsonValue body) throws Exception {
        var headers = new HeaderMap();
        if (caller.host() != null) {
            headers.put(Headers.HOST, caller.host());
        }
        headers.put(Headers.CONTENT_TYPE, "application/json");
        var bytes = body == null ? new byte[0] : BsonUtils.toJson(body).getBytes(StandardCharsets.UTF_8);
        var principal = caller.principal();
        var attached = caller.attached();
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

    private static void copyWarnings(InProcessDispatcher.Response answer, Consumer<String> warn) {
        var written = answer.bodyAsString();
        if (written == null || written.isBlank()) {
            return;
        }
        try {
            if (BsonDocument.parse(written).get("_warnings") instanceof BsonArray warnings) {
                warnings.forEach(w -> warn.accept(w.isString() ? w.asString().getValue() : w.toString()));
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
        return pathOf(baseOf(request), collection);
    }

    /** The path of a collection under {@code base}, the part of the upload's path before the bucket. */
    static String pathOf(String base, String collection) {
        return base + "/" + encode(collection);
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

    private static final Tika TIKA = new Tika();

    /** The type Tika detects from the first bytes of the stream, and the name when the bytes do not say; the stream is reset after. */
    static String detect(TikaInputStream stream, String filename) {
        try {
            return TIKA.detect(stream, filename);
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
     * The boundaries a text is cut at, from the one that keeps the most meaning together to the one
     * that keeps the least: paragraph, line, sentence, word. Each splits after the separator, so the
     * pieces put back together are the text as it was.
     */
    private static final java.util.regex.Pattern[] SEPARATORS = {
        java.util.regex.Pattern.compile("(?<=\\n\\n)"),
        java.util.regex.Pattern.compile("(?<=\\n)"),
        java.util.regex.Pattern.compile("(?<=[.!?\u2026]\\s)"),
        java.util.regex.Pattern.compile("(?<=\\s)")
    };

    /**
     * Splits {@code text} into chunks of at most {@code size} characters, the way LangChain's
     * RecursiveCharacterTextSplitter does: the text is cut at paragraphs, a piece still longer than
     * {@code size} at lines, then sentences, then words; the pieces are then put back together into
     * chunks as full as {@code size} allows. The overlap is made of whole pieces of the chunk before,
     * up to {@code overlap} characters, so a chunk never starts in the middle of a word.
     *
     * <p>A word is never cut: one longer than {@code size}, a URL or a hash, is a chunk of its own,
     * longer than the rest. A window of whitespace alone, as a blank page leaves, is no chunk; one
     * with too little in it to mean anything is joined to its neighbour, see {@link #joinTooSmall}.
     */
    static List<String> splitIntoChunks(String text, int size, int overlap) {
        var chunks = new ArrayList<String>();
        if (text == null || text.isEmpty() || size <= 0) return chunks;

        var pieces = new ArrayList<String>();
        piecesOf(text, size, 0, pieces);

        // put the pieces back together, as many as fit, keeping whole pieces of the chunk before as overlap
        var current = new java.util.ArrayDeque<String>();
        int length = 0;
        for (var piece : pieces) {
            if (length + piece.length() > size && !current.isEmpty()) {
                addChunk(chunks, current);
                while (!current.isEmpty() && (length > overlap || length + piece.length() > size)) {
                    length -= current.removeFirst().length();
                }
            }
            current.addLast(piece);
            length += piece.length();
        }
        addChunk(chunks, current);

        return joinTooSmall(chunks, Math.min(MIN_MEANINGFUL_CHARS, size / 5));
    }

    /** Cuts {@code text} at the separator of {@code level}, and a piece still longer than {@code size} at the next ones. */
    private static void piecesOf(String text, int size, int level, List<String> out) {
        if (text.length() <= size || level == SEPARATORS.length) {
            out.add(text); // fits, or a single word longer than a chunk: whole, never cut
            return;
        }
        var parts = SEPARATORS[level].split(text);
        if (parts.length == 1) {
            piecesOf(text, size, level + 1, out);
            return;
        }
        for (var part : parts) {
            if (part.length() <= size) {
                out.add(part);
            } else {
                piecesOf(part, size, level + 1, out);
            }
        }
    }

    /** The pieces as one chunk, without the whitespace at its ends; nothing when they are whitespace alone. */
    private static void addChunk(List<String> chunks, java.util.Collection<String> pieces) {
        var chunk = String.join("", pieces).strip();
        if (!chunk.isEmpty()) {
            chunks.add(chunk);
        }
    }

    /** The letters and digits a chunk needs to mean something on its own, with the default chunk size and above. */
    static final int MIN_MEANINGFUL_CHARS = 20;

    /**
     * Joins every chunk with fewer than {@code min} letters and digits to the one before it, or to
     * the one after when it is the first: a page number, a running header, the tail of a file after
     * the last full window. On its own such a chunk is noise in a search, close to everything and to
     * nothing; joined, its text is kept and the indexes stay without holes. A document that is all
     * one small chunk stays as it is.
     */
    static List<String> joinTooSmall(List<String> chunks, int min) {
        if (chunks.size() < 2 || min <= 0) {
            return chunks;
        }
        var out = new ArrayList<String>();
        String pending = null; // small chunks before the first big enough one
        for (var chunk : chunks) {
            if (meaningfulChars(chunk) >= min) {
                out.add(pending == null ? chunk : pending + " " + chunk);
                pending = null;
            } else if (!out.isEmpty()) {
                out.set(out.size() - 1, out.get(out.size() - 1) + " " + chunk);
            } else {
                pending = pending == null ? chunk : pending + " " + chunk;
            }
        }
        if (pending != null) {
            out.add(pending); // nothing was big enough: the whole text in one chunk
        }
        return out;
    }

    /** How many letters and digits {@code s} has. */
    static int meaningfulChars(String s) {
        int n = 0;
        for (int i = 0;i < s.length();) {
            int cp = s.codePointAt(i);
            if (Character.isLetterOrDigit(cp)) n++;
            i += Character.charCount(cp);
        }
        return n;
    }

}
