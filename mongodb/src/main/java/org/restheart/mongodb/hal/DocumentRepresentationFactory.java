/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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
package org.restheart.mongodb.hal;

import io.undertow.server.HttpServerExchange;
import java.time.Instant;
import java.util.List;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.types.ObjectId;
import static org.restheart.exchange.ExchangeKeys.BINARY_CONTENT;
import org.restheart.exchange.ExchangeKeys.TYPE;
import org.restheart.exchange.IllegalQueryParameterException;
import org.restheart.exchange.InvalidMetadataException;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.exchange.UnsupportedDocumentIdException;
import org.restheart.mongodb.metadata.Relationship;
import org.restheart.mongodb.utils.MongoURLUtils;
import org.restheart.utils.RepresentationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DocumentRepresentationFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentRepresentationFactory.class);

    private static boolean isBinaryFile(BsonDocument data) {
        return data.containsKey("filename") && data.containsKey("chunkSize");
    }

    /**
     *
     * @param rep
     * @param type
     * @param data
     */
    public static void addSpecialProperties(final Resource rep,
            TYPE type,
            BsonDocument data) {
        rep.addProperty("_type", new BsonString(type.name()));

        if (data != null) {
            var etag = data.get("_etag");

            if (etag != null && etag.isObjectId()) {
                if (data.get("_lastupdated_on") == null) {
                    // add the _lastupdated_on in case the _etag field is present and its value is an ObjectId
                    rep.addProperty("_lastupdated_on",
                            new BsonString(Instant.ofEpochSecond(etag.asObjectId()
                                    .getValue().getTimestamp()).toString()));
                }
            }

            Object id = data.get("_id");

            // generate the _created_on timestamp from the _id if this is an instance of ObjectId
            if (data.get("_created_on") == null && id != null && id instanceof ObjectId) {
                rep.addProperty("_created_on",
                        new BsonString(Instant.ofEpochSecond(((ObjectId) id).getTimestamp()).toString()));
            }
        }
    }

    private static void addRelationshipsLinks(Resource rep,
            HttpServerExchange exchange,
            BsonDocument data) {
        var request = MongoRequest.of(exchange);

        List<Relationship> rels = null;

        try {
            rels = Relationship.getFromJson(request.getCollectionProps());
        } catch (InvalidMetadataException ex) {
            rep.addWarning("collection " + request.getDBName()
                    + "/" + request.getCollectionName()
                    + " has invalid relationships definition");
        }

        if (rels != null) {
            for (Relationship rel : rels) {
                try {
                    String link = rel.getRelationshipLink(request,
                            request.getDBName(),
                            request.getCollectionName(), data);

                    if (link != null) {
                        rep.addLink(new Link(rel.getRel(), link));
                    }
                } catch (IllegalArgumentException | UnsupportedDocumentIdException ex) {
                    rep.addWarning(ex.getMessage());
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(ex.getMessage());
                    }
                }
            }
        }
    }

    /**
     *
     */
    public DocumentRepresentationFactory() {
    }

    /**
     *
     * @param href
     * @param exchange
     * @param data
     * @return
     * @throws IllegalQueryParameterException
     */
    public Resource getRepresentation(String href,
            HttpServerExchange exchange,
            BsonDocument data)
            throws IllegalQueryParameterException {
        var request = MongoRequest.of(exchange);
        var response = MongoResponse.of(exchange);

        var rep = request.isFullHalMode() && data != null
                ? new Resource(RepresentationUtils
                        .getReferenceLink(response,
                                MongoURLUtils.getParentPath(href),
                                data.get("_id")))
                : new Resource();

        if (data != null) {
            data.keySet()
                    .stream().forEach((key) -> rep.addProperty(key, data.get(key)));

            addRelationshipsLinks(rep, exchange, data);
        }

        // link templates and curies
        String requestPath = MongoURLUtils.removeTrailingSlashes(exchange.getRequestPath());

        String parentPath;

        // the document (file) representation can be asked for requests to collection (bucket)
        boolean isEmbedded = TYPE.COLLECTION.equals(request.getType())
                || TYPE.FILES_BUCKET.equals(request.getType())
                || TYPE.SCHEMA_STORE.equals(request.getType());

        if (isEmbedded) {
            parentPath = requestPath;
        } else {
            parentPath = MongoURLUtils.getParentPath(requestPath);
        }

        if (data != null && isBinaryFile(data)) {
            rep.addLink(new Link("rh:data",
                    String.format("%s/%s", href, BINARY_CONTENT)));
        }

        // link templates
        if (!isEmbedded && request.isFullHalMode()) {

            addSpecialProperties(rep, request.getType(), data);

            if (data != null && isBinaryFile(data)) {
                if (request.isParentAccessible()) {
                    rep.addLink(new Link("rh:bucket", parentPath));
                }

                rep.addLink(new Link("rh:file", parentPath + "/{fileid}{?id_type}", true));
            } else {
                if (request.isParentAccessible()) {
                    rep.addLink(new Link("rh:coll", parentPath));
                }

                rep.addLink(new Link("rh:document", parentPath + "/{docid}{?id_type}", true));
            }
        }

        return rep;
    }
}
