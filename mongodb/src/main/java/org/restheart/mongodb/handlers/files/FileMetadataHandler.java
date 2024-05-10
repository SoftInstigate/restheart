/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2024 SoftInstigate
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

import java.util.Optional;

import org.bson.BsonDocument;
import static org.restheart.exchange.ExchangeKeys.FILENAME;
import static org.restheart.exchange.ExchangeKeys.FILE_METADATA;
import static org.restheart.exchange.ExchangeKeys.PROPERTIES;
import static org.restheart.exchange.ExchangeKeys._ID;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.db.GridFs;
import org.restheart.mongodb.utils.RequestHelper;
import org.restheart.utils.BsonUtils;
import org.restheart.utils.HttpStatus;

import io.undertow.server.HttpServerExchange;

/**
 * A customised and cut down version of the
 * {@link org.restheart.mongodb.handlers.document.PutDocumentHandler PutDocumentHandler}
 * or
 * {@link org.restheart.mongodb.handlers.document.PatchDocumentHandler PatchDocumentHandler},
 * this deals with both PUT and PATCHing of the metadata for a binary file.
 *
 * @author Nath Papadacis {@literal <nath@thirststudios.co.uk>}
 */
public class FileMetadataHandler extends PipelinedHandler {

    private final GridFs gridFs = GridFs.get();

    /**
     * Creates a new instance of PatchFileMetadataHandler
     */
    public FileMetadataHandler() {
        this(null);
    }

    /**
     *
     * @param next
     */
    public FileMetadataHandler(PipelinedHandler next) {
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

        var _content = request.getContent();

        if (RequestHelper.isNotAcceptableContent(_content, exchange)) {
            next(exchange);
            return;
        }

        if (request.getFileInputStream() != null) {
            // PUT request with non null data will be dealt with by previous handler (PutFileHandler)
            if (request.isPatch()) {
                response.setInError(HttpStatus.SC_BAD_REQUEST, "only metadata is allowed for PATCH requests, not binary data");
            }
            next(exchange);
            return;
        }

        if (!_content.isDocument()) {
            response.setInError(HttpStatus.SC_BAD_REQUEST, "request content must be a JSON object");
            next(exchange);
            return;
        }

        var content = _content.asDocument();

        if (BsonUtils.containsUpdateOperators(content, true)) {
            response.setInError(HttpStatus.SC_BAD_REQUEST, "request content cannot contain update operators");
            next(exchange);
            return;
        }

        // Ensure the passed content whether already within a metadata/properties document or just plain
        // is wrapped in a metadata document.
        if (content.containsKey(FILE_METADATA)) {
            content = new BsonDocument(FILE_METADATA, content.get(FILE_METADATA));
        } else if (content.containsKey(PROPERTIES)) {
            content = new BsonDocument(FILE_METADATA, content.get(PROPERTIES));
        } else if (!content.containsKey(FILE_METADATA)) {
            content = new BsonDocument(FILE_METADATA, content);
        }

        final var _metadata = content.get(FILE_METADATA).asDocument();

        // Remove any _id field from the metadata.
        _metadata.remove(_ID);

        // Update main document filename to match metadata
        final var filename = _metadata.get(FILENAME);
        if (filename != null && filename.isString()) {
            content.put(FILENAME, filename);
        }

        var id = request.getDocumentId();

        if (content.containsKey(_ID)) {
            if (!content.get(_ID).equals(id)) {
                response.setInError(HttpStatus.SC_BAD_REQUEST, "_id in json data cannot be different than id in URL");
                next(exchange);
                return;
            }
            content.remove(_ID);
        }


        var result = gridFs.updateFileMetadata(
            Optional.ofNullable(request.getClientSession()),
            request.rsOps(),
            request.getDBName(),
            request.getCollectionName(),
            request.getMethod(),
            Optional.of(id),
            Optional.ofNullable(request.getFiltersDocument()),
            Optional.ofNullable(request.getShardKey()),
            content,
            request.getETag(),
            request.isETagCheckRequired());

        if (RequestHelper.isResponseInConflict(result, exchange)) {
            next(exchange);
            return;
        }

        response.setStatusCode(result.getHttpCode());

        next(exchange);
    }
}
