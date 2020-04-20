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

import io.undertow.server.HttpServerExchange;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import static org.restheart.exchange.ExchangeKeys.FILENAME;
import static org.restheart.exchange.ExchangeKeys.FILE_METADATA;
import static org.restheart.exchange.ExchangeKeys.PROPERTIES;
import static org.restheart.exchange.ExchangeKeys._ID;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.exchange.OperationResult;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.db.FileMetadataDAO;
import org.restheart.mongodb.db.FileMetadataRepository;
import org.restheart.mongodb.utils.RequestHelper;
import org.restheart.utils.HttpStatus;

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

    private final FileMetadataRepository fileMetadataDAO;

    /**
     * Creates a new instance of PatchFileMetadataHandler
     */
    public FileMetadataHandler() {
        this(null, new FileMetadataDAO());
    }

    /**
     *
     * @param fileMetadataDAO
     */
    public FileMetadataHandler(FileMetadataRepository fileMetadataDAO) {
        super(null);
        this.fileMetadataDAO = fileMetadataDAO;
    }

    /**
     *
     * @param next
     */
    public FileMetadataHandler(PipelinedHandler next) {
        super(next);
        this.fileMetadataDAO = new FileMetadataDAO();
    }

    /**
     *
     * @param next
     * @param fileMetadataDAO
     */
    public FileMetadataHandler(PipelinedHandler next, FileMetadataRepository fileMetadataDAO) {
        super(next);
        this.fileMetadataDAO = fileMetadataDAO;
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

        BsonValue _content = request.getContent();

        if (RequestHelper.isNotAcceptableContent(_content, exchange)) {
            next(exchange);
            return;
        }

        if (request.getFilePath() != null) {
            // PUT request with non null data will be dealt with by previous handler (PutFileHandler)
            if (request.isPatch()) {
                response.setIError(
                        HttpStatus.SC_NOT_ACCEPTABLE,
                        "only metadata is allowed, not binary data");
            }
            next(exchange);
            return;
        }

        // Ensure the passed content whether already within a metadata/properties document or just plain
        // is wrapped in a metadata document.
        BsonDocument content = _content.asDocument();
        if (content.containsKey(PROPERTIES)) {
            content = new BsonDocument(FILE_METADATA, content.get(PROPERTIES));
        } else if (!content.containsKey(FILE_METADATA)) {
            content = new BsonDocument(FILE_METADATA, content);
        }

        // Remove any _id field from the metadata.
        final BsonDocument _metadata = content.get(FILE_METADATA).asDocument();
        _metadata.remove(_ID);

        // Update main document filename to match metadata
        final BsonValue filename = _metadata.get(FILENAME);
        if (filename != null && filename.isString()) {
            content.put(FILENAME, filename);
        }

        BsonValue id = request.getDocumentId();

        if (content.get(_ID) == null) {
            content.put(_ID, id);
        } else if (!content.get(_ID).equals(id)) {
            response.setIError(
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "_id in json data cannot be different than id in URL");
            next(exchange);
            return;
        }

        OperationResult result = fileMetadataDAO.updateMetadata(
                request.getClientSession(),
                request.getDBName(),
                request.getCollectionName(),
                request.getDocumentId(),
                request.getFiltersDocument(),
                request.getShardKey(),
                content,
                request.getETag(),
                request.isPatch(),
                request.isETagCheckRequired());

        if (RequestHelper.isResponseInConflict(result, exchange)) {
            next(exchange);
            return;
        }

        response.setStatusCode(result.getHttpCode());

        next(exchange);
    }
}
