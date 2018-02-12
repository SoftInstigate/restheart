package org.restheart.handlers.document;

import io.undertow.server.HttpServerExchange;
import java.time.Instant;
import java.util.List;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.types.ObjectId;
import org.restheart.Configuration;
import org.restheart.hal.Link;
import org.restheart.hal.Representation;
import org.restheart.hal.UnsupportedDocumentIdException;
import org.restheart.handlers.IllegalQueryParamenterException;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.RequestContext.TYPE;
import org.restheart.handlers.metadata.InvalidMetadataException;
import org.restheart.metadata.Relationship;
import org.restheart.utils.URLUtils;
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

    public static void addSpecialProperties(final Representation rep, RequestContext.TYPE type, BsonDocument data) {
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

    private static void addRelationshipsLinks(Representation rep, RequestContext context, BsonDocument data) {
        List<Relationship> rels = null;

        try {
            rels = Relationship.getFromJson(context.getCollectionProps());
        } catch (InvalidMetadataException ex) {
            rep.addWarning("collection " + context.getDBName()
                    + "/" + context.getCollectionName()
                    + " has invalid relationships definition");
        }

        if (rels != null) {
            for (Relationship rel : rels) {
                try {
                    String link = rel.getRelationshipLink(context, context.getDBName(), context.getCollectionName(), data);

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

    public DocumentRepresentationFactory() {

    }

    /**
     *
     * @param href
     * @param exchange
     * @param context
     * @param data
     * @return
     * @throws IllegalQueryParamenterException
     */
    public Representation getRepresentation(String href, HttpServerExchange exchange, RequestContext context, BsonDocument data)
            throws IllegalQueryParamenterException {
        Representation rep;

        BsonValue id = data.get("_id");

        if (context.isFullHalMode()) {
            rep = new Representation(URLUtils.getReferenceLink(context, URLUtils.getParentPath(href), id));
        } else {
            rep = new Representation();
        }

        data.keySet()
                .stream().forEach((key) -> rep.addProperty(key, data.get(key)));

        addRelationshipsLinks(rep, context, data);

        // link templates and curies
        String requestPath = URLUtils.removeTrailingSlashes(exchange.getRequestPath());

        String parentPath;

        // the document (file) representation can be asked for requests to collection (bucket)
        boolean isEmbedded = TYPE.COLLECTION.equals(context.getType())
                || TYPE.FILES_BUCKET.equals(context.getType())
                || TYPE.SCHEMA_STORE.equals(context.getType());

        if (isEmbedded) {
            parentPath = requestPath;
        } else {
            parentPath = URLUtils.getParentPath(requestPath);
        }

        String _docIdType = null;
        if (isBinaryFile(data)) {
            rep.addLink(new Link("rh:data",
                    String.format("%s/%s", href, RequestContext.BINARY_CONTENT)));
        }

        // link templates
        if (!isEmbedded && context.isFullHalMode()) {

            addSpecialProperties(rep, context.getType(), data);

            if (isBinaryFile(data)) {
                if (context.isParentAccessible()) {
                    rep.addLink(new Link("rh:bucket", parentPath));
                }

                rep.addLink(new Link("rh:file", parentPath + "/{fileid}{?id_type}", true));
            } else {
                if (context.isParentAccessible()) {
                    rep.addLink(new Link("rh:coll", parentPath));
                }

                rep.addLink(new Link("rh:document", parentPath + "/{docid}{?id_type}", true));
            }

            if (!isEmbedded) {
                rep.addLink(new Link("rh", "curies", Configuration.RESTHEART_ONLINE_DOC_URL
                        + "/{rel}.html", true), true);
            }
        }

        return rep;
    }
}
