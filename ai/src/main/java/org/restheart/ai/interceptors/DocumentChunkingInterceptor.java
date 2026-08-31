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

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.types.ObjectId;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
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
 * }</pre>
 *
 * <h2>Stored chunk document shape</h2>
 * <pre>{@code
 * {
 *   "_id":        ObjectId,
 *   "source":     "db/bucket.files/fileId",
 *   "fileId":     <BsonValue>,
 *   "chunkIndex": 0,
 *   "text":       "…chunk text…"
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

    private int chunkSize        = 1000;
    private int chunkOverlap     = 200;
    private String targetCollection = "_chunks";

    @Inject("mclient")
    private MongoClient mclient;

    @Inject("config")
    private Map<String, Object> config;

    @OnInit
    public void setup() {
        this.chunkSize        = argOrDefault(config, "chunk-size", 1000);
        this.chunkOverlap     = argOrDefault(config, "chunk-overlap", 200);
        this.targetCollection = argOrDefault(config, "target-collection", "_chunks");
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
