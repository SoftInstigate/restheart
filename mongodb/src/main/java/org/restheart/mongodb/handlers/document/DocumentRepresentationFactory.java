package org.restheart.mongodb.handlers.document;

import io.undertow.server.HttpServerExchange;
import java.time.Instant;
import java.util.List;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.types.ObjectId;
import org.restheart.handlers.exchange.BsonRequest;
import org.restheart.handlers.exchange.BsonResponse;
import static org.restheart.handlers.exchange.ExchangeKeys.BINARY_CONTENT;
import org.restheart.handlers.exchange.ExchangeKeys.TYPE;
import org.restheart.mongodb.handlers.IllegalQueryParamenterException;
import org.restheart.mongodb.handlers.metadata.InvalidMetadataException;
import org.restheart.mongodb.metadata.Relationship;
import org.restheart.mongodb.representation.Link;
import org.restheart.mongodb.representation.RepUtils;
import org.restheart.mongodb.representation.Resource;
import org.restheart.mongodb.representation.UnsupportedDocumentIdException;
import org.restheart.mongodb.utils.URLUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class DocumentRepresentationFactory {

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

        Object etag = data.get("_etag");

        if (etag != null && etag instanceof ObjectId) {
            if (data.get("_lastupdated_on") == null) {
                // add the _lastupdated_on in case the _etag field is present and its value is an ObjectId
                rep.addProperty("_lastupdated_on",
                        new BsonString(Instant.ofEpochSecond(((ObjectId) etag).getTimestamp()).toString()));
            }
        }

        Object id = data.get("_id");

        // generate the _created_on timestamp from the _id if this is an instance of ObjectId
        if (data.get("_created_on") == null && id != null && id instanceof ObjectId) {
            rep.addProperty("_created_on",
                    new BsonString(Instant.ofEpochSecond(((ObjectId) id).getTimestamp()).toString()));
        }
    }

    private static void addRelationshipsLinks(Resource rep,
            HttpServerExchange exchange,
            BsonDocument data) {
        var request = BsonRequest.wrap(exchange);
        var response = BsonResponse.wrap(exchange);

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
                    LOGGER.debug(ex.getMessage());
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
     * @throws IllegalQueryParamenterException
     */
    public Resource getRepresentation(String href, HttpServerExchange exchange,
            BsonDocument data)
            throws IllegalQueryParamenterException {
        var request = BsonRequest.wrap(exchange);
        var response = BsonResponse.wrap(exchange);

        Resource rep;

        BsonValue id = data.get("_id");

        if (request.isFullHalMode()) {
            rep = new Resource(RepUtils.getReferenceLink(response, URLUtils.getParentPath(href), id));
        } else {
            rep = new Resource();
        }

        data.keySet()
                .stream().forEach((key) -> rep.addProperty(key, data.get(key)));

        addRelationshipsLinks(rep, exchange, data);

        // link templates and curies
        String requestPath = URLUtils.removeTrailingSlashes(exchange.getRequestPath());

        String parentPath;

        // the document (file) representation can be asked for requests to collection (bucket)
        boolean isEmbedded = TYPE.COLLECTION.equals(request.getType())
                || TYPE.FILES_BUCKET.equals(request.getType())
                || TYPE.SCHEMA_STORE.equals(request.getType());

        if (isEmbedded) {
            parentPath = requestPath;
        } else {
            parentPath = URLUtils.getParentPath(requestPath);
        }

        if (isBinaryFile(data)) {
            rep.addLink(new Link("rh:data",
                    String.format("%s/%s", href, BINARY_CONTENT)));
        }

        // link templates
        if (!isEmbedded && request.isFullHalMode()) {

            addSpecialProperties(rep, request.getType(), data);

            if (isBinaryFile(data)) {
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
