/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.restheart.handlers.files;

import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSFile;
import static com.mongodb.client.model.Filters.eq;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import java.io.IOException;
import org.bson.BsonObjectId;
import org.bson.types.ObjectId;
import org.restheart.db.MongoDBClientSingleton;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.RequestHelper;
import org.restheart.utils.ResponseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class GetFileBinaryHandler extends PipedHttpHandler {

    public static final String APPLICATION_OCTET_STREAM
            = "application/octet-stream";

    public static final String CONTENT_TRANSFER_ENCODING_BINARY
            = "binary";

    private static final Logger LOGGER
            = LoggerFactory.getLogger(GetFileBinaryHandler.class);

    static String extractBucketName(final String collectionName) {
        return collectionName.split("\\.")[0];
    }

    /**
     * Creates a new instance of GetFileBinaryHandler
     *
     */
    public GetFileBinaryHandler() {
        super();
    }

    /**
     * Creates a new instance of GetFileBinaryHandler
     *
     * @param next
     */
    public GetFileBinaryHandler(PipedHttpHandler next) {
        super(next);
    }

    GetFileBinaryHandler(Object object, Object object0) {
        super(null, null);
    }

    @Override
    public void handleRequest(
            HttpServerExchange exchange,
            RequestContext context)
            throws Exception {
        if (context.isInError()) {
            next(exchange, context);
            return;
        }

        LOGGER.trace("GET " + exchange.getRequestURL());
        final String bucket = extractBucketName(context.getCollectionName());

        GridFSBucket gridFSBucket = GridFSBuckets.create(
                MongoDBClientSingleton.getInstance().getClient()
                        .getDatabase(context.getDBName()),
                bucket);

        GridFSFile dbsfile = gridFSBucket
                .find(eq("_id", context.getDocumentId()))
                .limit(1).iterator().tryNext();

        if (dbsfile == null) {
            fileNotFound(context, exchange);
        } else if (!checkEtag(exchange, dbsfile)) {
            sendBinaryContent(context, gridFSBucket, dbsfile, exchange);
        }

        next(exchange, context);
    }

    private boolean checkEtag(HttpServerExchange exchange, GridFSFile dbsfile) {
        if (dbsfile != null) {
            Object etag;

            if (dbsfile.getMetadata() != null
                    && dbsfile.getMetadata().containsKey("_etag")) {
                etag = dbsfile.getMetadata().get("_etag");
            } else {
                etag = null;
            }

            if (etag != null && etag instanceof ObjectId) {
                ObjectId _etag = (ObjectId) etag;

                BsonObjectId __etag = new BsonObjectId(_etag);

                // in case the request contains the IF_NONE_MATCH header with the current etag value,
                // just return 304 NOT_MODIFIED code
                if (RequestHelper.checkReadEtag(exchange, __etag)) {
                    exchange.setStatusCode(HttpStatus.SC_NOT_MODIFIED);
                    exchange.endExchange();
                    return true;
                }
            }
        }

        return false;
    }

    private void fileNotFound(
            RequestContext context,
            HttpServerExchange exchange) throws Exception {
        final String errMsg = String.format(
                "File with ID <%s> not found", context.getDocumentId());
        LOGGER.trace(errMsg);
        ResponseHelper.endExchangeWithMessage(
                exchange,
                context,
                HttpStatus.SC_NOT_FOUND,
                errMsg);
        next(exchange, context);
    }

    private void sendBinaryContent(
            final RequestContext context,
            final GridFSBucket gridFSBucket,
            final GridFSFile file,
            final HttpServerExchange exchange)
            throws IOException {
        LOGGER.trace("Filename = {}", file.getFilename());
        LOGGER.trace("Content length = {}", file.getLength());

        if (file.getMetadata() != null
                && file.getMetadata().get("contentType") != null) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE,
                    file.getMetadata().get("contentType").toString());
        } else if (file.getMetadata() != null
                && file.getMetadata().get("contentType") != null) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE,
                    file.getMetadata().get("contentType").toString());
        } else {
            exchange.getResponseHeaders().put(
                    Headers.CONTENT_TYPE,
                    APPLICATION_OCTET_STREAM);
        }

        exchange.getResponseHeaders().put(
                Headers.CONTENT_LENGTH,
                file.getLength());

        exchange.getResponseHeaders().put(
                Headers.CONTENT_DISPOSITION,
                String.format("inline; filename=\"%s\"",
                        extractFilename(file)));

        exchange.getResponseHeaders().put(
                Headers.CONTENT_TRANSFER_ENCODING,
                CONTENT_TRANSFER_ENCODING_BINARY);

        ResponseHelper.injectEtagHeader(exchange, file.getMetadata());

        context.setResponseStatusCode(HttpStatus.SC_OK);

        gridFSBucket.downloadToStream(
                file.getId(),
                exchange.getOutputStream());
    }

    private String extractFilename(final GridFSFile dbsfile) {
        return dbsfile.getFilename() != null
                ? dbsfile.getFilename()
                : dbsfile.getId().toString();
    }

}
