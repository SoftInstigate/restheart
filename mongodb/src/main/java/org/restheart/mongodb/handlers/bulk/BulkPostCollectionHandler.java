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
package org.restheart.mongodb.handlers.bulk;

import java.util.Optional;

import org.bson.BsonArray;
import org.bson.BsonValue;
import org.restheart.exchange.ExchangeKeys.DOC_ID_TYPE;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.db.Documents;
import org.restheart.mongodb.utils.ResponseHelper;
import org.restheart.utils.HttpStatus;

import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class BulkPostCollectionHandler extends PipelinedHandler {

    private final Documents documents = Documents.get();

    /**
     * Creates a new instance of BulkPostCollectionHandler
     */
    public BulkPostCollectionHandler() {
        this(null);
    }

    /**
     * Creates a new instance of BulkPostCollectionHandler
     *
     * @param next
     */
    public BulkPostCollectionHandler(PipelinedHandler next) {
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

        // expects an an array
        if (_content == null || !_content.isArray()) {
            throw new RuntimeException("error, this handler expects an array of objects");
        }

        var content = _content.asArray();

        if (!checkIds(exchange, content)) {
            // if check fails, exchange has been closed
            return;
        }

        var result = this.documents.bulkPostDocuments(Optional.ofNullable(request.getClientSession()),
            request.rsOps(),
            request.getDBName(),
            request.getCollectionName(),
            content,
            Optional.ofNullable(request.getFiltersDocument()),
            Optional.ofNullable(request.getShardKey()),
            request.getWriteMode());

        response.setDbOperationResult(result);

        // inject the etag
        if (result.getEtag() != null) {
            ResponseHelper.injectEtagHeader(exchange, result.getEtag());
        }

        response.setStatusCode(result.getHttpCode());

        var bprf = new BulkResultRepresentationFactory();

        response.setContent(bprf.getRepresentation(request.getPath(), result));

        next(exchange);
    }

    private boolean checkIds(HttpServerExchange exchange, BsonArray documents) throws Exception {
        boolean ret = true;

        for (var document : documents) {
            if (!checkId(exchange, document)) {
                ret = false;
                break;
            }
        }

        return ret;
    }

    private boolean checkId(HttpServerExchange exchange, BsonValue document) throws Exception {
        var request = MongoRequest.of(exchange);

        if (document.isDocument()
                && document.asDocument().containsKey("_id")
                && document.asDocument().get("_id").isString()
                && MongoRequest.isReservedDocumentId(request.getType(), document.asDocument().get("_id"))) {
            MongoResponse.of(exchange).setInError(HttpStatus.SC_FORBIDDEN, "id is reserved: " + document.asDocument().get("_id").asString().getValue());
            next(exchange);
            return false;
        }

        if (document.isDocument() && document.asDocument().containsKey("_id")) {
            if (!(request.getDocIdType() == DOC_ID_TYPE.OID || request.getDocIdType() == DOC_ID_TYPE.STRING_OID)) {
                MongoResponse.of(exchange).setInError(HttpStatus.SC_BAD_REQUEST, "_id in content body is mandatory for documents with id type " + request.getDocIdType().name());
                next(exchange);
                return false;
            }
        }

        return true;
    }
}
