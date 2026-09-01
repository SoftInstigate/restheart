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
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.types.ObjectId;
import org.restheart.ai.util.PluginModelResolver;
import org.restheart.ai.util.RequestOverrides;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.ai.ContextualEmbeddingModel;
import org.restheart.plugins.ai.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClient;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.model.InsertManyOptions;

/**
 * After a file is successfully uploaded to a GridFS bucket, this interceptor
 * extracts plain text from the binary content using Apache Tika, splits it into
 * overlapping chunks and stores each chunk as a separate document in a
 * configurable target collection.
 *
 * <p>MongoDB Atlas can then automatically generate vector embeddings for the
 * stored chunks via an {@code autoEmbed} Vector Search index, making the
 * content immediately searchable with {@code $vectorSearch}.
 *
 * <h2>Configuration (plugins-args)</h2>
 * <pre>{@code
 * plugins-args:
 *   documentChunkingInterceptor:
 *     enabled: false             # must be explicitly enabled
 *     chunk-size: 1000           # target chunk size in characters (default: 1000)
 *     chunk-overlap: 200         # overlap between consecutive chunks (default: 200)
 *     target-collection: _chunks # collection where chunks are stored (default: _chunks)
 *     embedding-provider: ""     # optional; name of a configured Provider<EmbeddingModel>
 * }</pre>
 *
 * <h2>Phase 2: embedding chunks (optional)</h2>
 * <p>When {@code embedding-provider} names a configured {@code Provider<EmbeddingModel>}
 * (the same one {@code autoEmbeddingInterceptor}/{@code $vectorize} use — shared
 * {@link RequestOverrides#EMBEDDING_PROVIDER} override), each chunk's text is embedded
 * (a single batched call for all of a file's chunks, not one call per chunk) and the
 * result stored alongside it as {@code vector}. When unset — the default — behavior is
 * unchanged from Phase 1: text-only segments, embeddings left to MongoDB's own
 * {@code autoEmbed} index type. A failed embedding call does not lose the chunks — they
 * are still stored, just without a {@code vector} field, exactly as if no provider were
 * configured.
 *
 * <p>When the resolved provider also implements {@code ContextualEmbeddingModel}
 * (e.g. {@code voyageContextualEmbeddingProvider}), all of a file's chunks are embedded
 * together via that capability instead of the plain {@code EmbeddingModel.embed} call —
 * each chunk's vector is then computed with awareness of the other chunks in the same
 * file, rather than in isolation.
 *
 * <h2>Multi-tenant</h2>
 * <p>Per request, a deployment's tenant-config interceptor may attach
 * {@link RequestOverrides#CHUNK_SIZE}, {@link RequestOverrides#CHUNK_OVERLAP},
 * {@link RequestOverrides#TARGET_COLLECTION}, {@link RequestOverrides#EMBEDDING_PROVIDER}
 * to use different values for that tenant.
 *
 * <h2>Stored chunk document shape</h2>
 * <pre>{@code
 * {
 *   "_id":        ObjectId,
 *   "source":     "db/bucket.files/fileId",
 *   "fileId":     <BsonValue>,
 *   "chunkIndex": 0,
 *   "text":       "…chunk text…",
 *   "vector":     [0.123, -0.456, ...]   // only when embedding-provider is configured
 * }
 * }</pre>
 */
@RegisterPlugin(
    name = "documentChunkingInterceptor",
    description = "Extracts text from uploaded files using Tika, chunks it and stores segments for vector search",
    interceptPoint = InterceptPoint.RESPONSE,
    requiresContent = false,
    enabledByDefault = false
)
public class DocumentChunkingInterceptor implements MongoInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentChunkingInterceptor.class);

    // static, single-tenant defaults; overridden per request via RequestOverrides
    private int defaultChunkSize        = 1000;
    private int defaultChunkOverlap     = 200;
    private String defaultTargetCollection = "_chunks";
    private String defaultEmbeddingProviderName = "";

    @Inject("mclient")
    private MongoClient mclient;

    @Inject("config")
    private Map<String, Object> config;

    @Inject("registry")
    private PluginsRegistry registry;

    // resolved lazily and cached per provider name, mirroring AutoEmbeddingInterceptor
    private final Map<String, EmbeddingModel> resolvedModels = new ConcurrentHashMap<>();

    @OnInit
    public void setup() {
        this.defaultChunkSize        = argOrDefault(config, "chunk-size", 1000);
        this.defaultChunkOverlap     = argOrDefault(config, "chunk-overlap", 200);
        this.defaultTargetCollection = argOrDefault(config, "target-collection", "_chunks");
        this.defaultEmbeddingProviderName = argOrDefault(config, "embedding-provider", "");
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return (request.isFilesBucket() && request.isPost()
                || request.isFile() && request.isPut())
            && !response.isInError()
            && (response.getStatusCode() == 201 || response.getStatusCode() == 200);
    }

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var dbName     = request.getDBName();
        var collName   = request.getCollectionName(); // e.g. "fs.files"
        var bucketName = collName.endsWith(".files")
            ? collName.substring(0, collName.length() - 6)
            : collName;

        BsonValue fileId = resolveFileId(request, response);
        if (fileId == null) {
            LOGGER.warn("documentChunkingInterceptor: could not determine file id for {}/{}", dbName, collName);
            return;
        }

        // Download the file bytes from GridFS.
        byte[] fileBytes;
        try {
            var bucket = GridFSBuckets.create(mclient.getDatabase(dbName), bucketName);
            var out    = new ByteArrayOutputStream();
            bucket.downloadToStream(fileId, out);
            fileBytes = out.toByteArray();
        } catch (Exception e) {
            LOGGER.warn("documentChunkingInterceptor: could not download file {} from {}/{}: {}",
                fileId, dbName, bucketName, e.getMessage());
            return;
        }

        // Extract plain text using Apache Tika.
        String text;
        try {
            var parser   = new AutoDetectParser();
            var handler  = new BodyContentHandler(-1);
            var metadata = new Metadata();
            var context  = new ParseContext();
            parser.parse(new ByteArrayInputStream(fileBytes), handler, metadata, context);
            text = handler.toString();
        } catch (Exception e) {
            LOGGER.warn("documentChunkingInterceptor: Tika could not extract text from file {} in {}/{}: {}",
                fileId, dbName, bucketName, e.getMessage());
            return;
        }

        if (text == null || text.isBlank()) {
            LOGGER.debug("documentChunkingInterceptor: no text extracted from file {} in {}/{}", fileId, dbName, bucketName);
            return;
        }

        var chunkSize = RequestOverrides.intVal(request, RequestOverrides.CHUNK_SIZE, defaultChunkSize);
        var chunkOverlap = RequestOverrides.intVal(request, RequestOverrides.CHUNK_OVERLAP, defaultChunkOverlap);
        var targetCollection = RequestOverrides.str(request, RequestOverrides.TARGET_COLLECTION, defaultTargetCollection);

        var chunks = splitIntoChunks(text.strip(), chunkSize, chunkOverlap);
        if (chunks.isEmpty()) return;

        var sourceRef = dbName + "/" + collName + "/" + fileId;
        var documents = new ArrayList<BsonDocument>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            documents.add(new BsonDocument()
                .append("_id",        new BsonObjectId(new ObjectId()))
                .append("source",     new BsonString(sourceRef))
                .append("fileId",     fileId)
                .append("chunkIndex", new BsonInt32(i))
                .append("text",       new BsonString(chunks.get(i))));
        }

        var embeddingProviderName = RequestOverrides.str(request, RequestOverrides.EMBEDDING_PROVIDER, defaultEmbeddingProviderName);
        if (!embeddingProviderName.isBlank()) {
            embedChunks(documents, chunks, embeddingProviderName, request, fileId, dbName);
        }

        try {
            mclient.getDatabase(dbName)
                .getCollection(targetCollection, BsonDocument.class)
                .insertMany(documents, new InsertManyOptions().ordered(false));

            LOGGER.info("documentChunkingInterceptor: stored {} chunks from file {} into {}/{}",
                documents.size(), fileId, dbName, targetCollection);
        } catch (Exception e) {
            LOGGER.error("documentChunkingInterceptor: failed to store chunks for file {} in {}: {}",
                fileId, dbName, e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------

    /**
     * Embeds all of {@code chunkTexts} in a single batched call and appends the result
     * to the matching {@code documents} entry as {@code vector}. A missing/not-enabled
     * provider, or a failed embedding call, is logged and otherwise ignored — the
     * chunks are still stored (by the caller), just without a {@code vector} field,
     * exactly as if no {@code embedding-provider} were configured.
     */
    void embedChunks(List<BsonDocument> documents, List<String> chunkTexts,
            String providerName, MongoRequest request, BsonValue fileId, String dbName) {
        var model = PluginModelResolver.resolve(registry, resolvedModels, providerName, EmbeddingModel.class);
        if (model.isEmpty()) {
            LOGGER.warn("documentChunkingInterceptor: embedding provider '{}' not found, not enabled, "
                + "or does not supply an EmbeddingModel — storing chunks for file {} in {} without vectors",
                providerName, fileId, dbName);
            return;
        }

        List<float[]> vectors;
        try {
            var resolved = model.get();
            // every chunk here belongs to the same file — when the provider supports
            // contextualized chunk embeddings, prefer it over the plain independent
            // embed() call for better retrieval quality (see ContextualEmbeddingModel)
            vectors = resolved instanceof ContextualEmbeddingModel contextual
                ? contextual.embedChunks(chunkTexts, request)
                : resolved.embed(chunkTexts, request);
        } catch (Exception e) {
            LOGGER.error("documentChunkingInterceptor: embedding call to '{}' failed for file {} in {}: {} "
                + "— storing chunks without vectors", providerName, fileId, dbName, e.getMessage(), e);
            return;
        }

        for (int i = 0; i < documents.size() && i < vectors.size(); i++) {
            var vector = vectors.get(i);
            if (vector == null) {
                continue;
            }
            var arr = new BsonArray();
            for (var f : vector) {
                arr.add(new BsonDouble(f));
            }
            documents.get(i).append("vector", arr);
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
     * Splits {@code text} into chunks of at most {@code size} characters with
     * {@code overlap} characters of context carried over between consecutive chunks.
     */
    static List<String> splitIntoChunks(String text, int size, int overlap) {
        var chunks = new ArrayList<String>();
        if (text == null || text.isEmpty() || size <= 0) return chunks;

        int start = 0;
        int len   = text.length();
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
