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

import io.undertow.server.HttpServerExchange;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.db.FileMetadataDAO;
import org.restheart.db.FileMetadataRepository;
import org.restheart.db.OperationResult;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.RequestContext.METHOD;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;

/**
 * A customised and cut down version of the
 * {@link org.restheart.handlers.document.PutDocumentHandler PutDocumentHandler}
 * or
 * {@link org.restheart.handlers.document.PatchDocumentHandler PatchDocumentHandler},
 * this deals with both PUT and PATCHing of the metadata for a binary file.
 *
 * @author Nath Papadacis {@literal <nath@thirststudios.co.uk>}
 */
public class FileMetadataHandler extends PipedHttpHandler {

    private final FileMetadataRepository fileMetadataDAO;

    /**
     * Creates a new instance of PatchFileMetadataHandler
     */
    public FileMetadataHandler() {
        this(null, new FileMetadataDAO());
    }

    public FileMetadataHandler(FileMetadataRepository fileMetadataDAO) {
        super(null);
        this.fileMetadataDAO = fileMetadataDAO;
    }

    public FileMetadataHandler(PipedHttpHandler next) {
        super(next);
        this.fileMetadataDAO = new FileMetadataDAO();
    }

    public FileMetadataHandler(PipedHttpHandler next, FileMetadataRepository fileMetadataDAO) {
        super(next);
        this.fileMetadataDAO = fileMetadataDAO;
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

        BsonValue _content = context.getContent();

        if (isNotAcceptableContent(_content, exchange, context)) {
            return;
        }

        if (context.getFilePath() != null) {
            // PUT request with non null data will be dealt with by previous handler (PutFileHandler)
            if (context.getMethod() == METHOD.PATCH) {
                ResponseHelper.endExchangeWithMessage(
                        exchange,
                        context,
                        HttpStatus.SC_NOT_ACCEPTABLE,
                        "only metadata is allowed, not binary data");
            }
            next(exchange, context);
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

        BsonValue id = context.getDocumentId();

        if (content.get(_ID) == null) {
            content.put(_ID, id);
        } else if (!content.get(_ID).equals(id)) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "_id in json data cannot be different than id in URL");
            next(exchange, context);
            return;
        }

        OperationResult result = fileMetadataDAO.updateMetadata(
                context.getClientSession(),
                context.getDBName(),
                context.getCollectionName(),
                context.getDocumentId(),
                context.getFiltersDocument(),
                context.getShardKey(),
                content,
                context.getETag(),
                context.getMethod() == METHOD.PATCH,
                context.isETagCheckRequired());

        if (isResponseInConflict(context, result, exchange)) {
            return;
        }

        context.setResponseStatusCode(result.getHttpCode());

        next(exchange, context);
    }
}
