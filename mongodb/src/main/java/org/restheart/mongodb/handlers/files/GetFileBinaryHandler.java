/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
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
package org.restheart.mongodb.handlers.files;

import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSFile;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import java.io.IOException;
import org.bson.BsonObjectId;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.db.GridFs;
import org.restheart.mongodb.db.MongoClientSingleton;
import org.restheart.mongodb.utils.RequestHelper;
import org.restheart.mongodb.utils.ResponseHelper;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class GetFileBinaryHandler extends PipelinedHandler {

    /**
     *
     */
    public static final String APPLICATION_OCTET_STREAM = "application/octet-stream";

    /**
     *
     */
    public static final String CONTENT_TRANSFER_ENCODING_BINARY = "binary";

    private static final Logger LOGGER = LoggerFactory.getLogger(GetFileBinaryHandler.class);

    private final GridFs gridFs = GridFs.get();

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
    public GetFileBinaryHandler(PipelinedHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = MongoRequest.of(exchange);
        var response = MongoResponse.of(exchange);

        if (request.isInError()) {
            next(exchange);
            return;
        }

        LOGGER.trace("GET " + exchange.getRequestURL());
        final var bucket = gridFs.extractBucketName(request.getCollectionName());

        var gridFSBucket = GridFSBuckets.create(MongoClientSingleton.getInstance().getClient().getDatabase(request.getDBName()), bucket);

        Bson filter;

        var filterQparam = request.getFiltersDocument();

        if (filterQparam != null && filterQparam.isNull()) {
            filter = and(eq("_id", request.getDocumentId()), filterQparam);
        } else {
            filter = eq("_id", request.getDocumentId());
        }

        var dbsfile = gridFSBucket.find(filter).limit(1).iterator().tryNext();

        if (dbsfile == null) {
            fileNotFound(request, exchange);
        } else if (!checkEtag(exchange, dbsfile)) {
            sendBinaryContent(request, response, gridFSBucket, dbsfile, exchange);
        }

        next(exchange);
    }

    private boolean checkEtag(HttpServerExchange exchange, GridFSFile dbsfile) {
        if (dbsfile != null) {
            Object etag;

            if (dbsfile.getMetadata() != null && dbsfile.getMetadata().containsKey("_etag")) {
                etag = dbsfile.getMetadata().get("_etag");
            } else {
                etag = null;
            }

            if (etag != null && etag instanceof ObjectId) {
                var _etag = (ObjectId) etag;

                var __etag = new BsonObjectId(_etag);

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
            MongoRequest request,
            HttpServerExchange exchange) throws Exception {
        final String errMsg = String.format(
                "File with ID <%s> not found", request.getDocumentId());
        LOGGER.trace(errMsg);
        MongoResponse.of(exchange).setInError(HttpStatus.SC_NOT_FOUND, errMsg);
        next(exchange);
    }

    private void sendBinaryContent(
            final MongoRequest request,
            final MongoResponse response,
            final GridFSBucket gridFSBucket,
            final GridFSFile file,
            final HttpServerExchange exchange)
            throws IOException {
        LOGGER.trace("Filename = {}", file.getFilename());
        LOGGER.trace("Content length = {}", file.getLength());

        if (file.getMetadata() != null && file.getMetadata().get("contentType") != null) {
            response.getHeaders().put(Headers.CONTENT_TYPE, file.getMetadata().get("contentType").toString());
        } else if (file.getMetadata() != null && file.getMetadata().get("contentType") != null) {
            response.getHeaders().put(Headers.CONTENT_TYPE, file.getMetadata().get("contentType").toString());
        } else {
            response.getHeaders().put(Headers.CONTENT_TYPE, APPLICATION_OCTET_STREAM);
        }

        response.getHeaders().put(Headers.CONTENT_LENGTH, file.getLength());

        response.getHeaders().put(Headers.CONTENT_DISPOSITION, String.format("inline; filename=\"%s\"", extractFilename(file)));

        response.getHeaders().put(Headers.CONTENT_TRANSFER_ENCODING,CONTENT_TRANSFER_ENCODING_BINARY);

        ResponseHelper.injectEtagHeader(exchange, file.getMetadata());

        response.setStatusCode(HttpStatus.SC_OK);

        response.setCustomSender(() -> {
            if (request.getClientSession() != null) {
                gridFSBucket.downloadToStream(request.getClientSession(), file.getId(), exchange.getOutputStream());
            } else {
                gridFSBucket.downloadToStream(file.getId(), exchange.getOutputStream());
            }
        });
    }

    private String extractFilename(final GridFSFile dbsfile) {
        return dbsfile.getFilename() != null
                ? dbsfile.getFilename()
                : dbsfile.getId().toString();
    }
}
