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
package org.restheart.representation;

import io.undertow.server.HttpServerExchange;
import java.util.Date;
import java.util.TreeMap;
import org.bson.BsonBoolean;
import org.bson.BsonDateTime;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonMaxKey;
import org.bson.BsonMinKey;
import org.bson.BsonNull;
import org.bson.BsonNumber;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.types.MaxKey;
import org.bson.types.MinKey;
import org.bson.types.ObjectId;
import static org.restheart.db.DAOUtils.LOGGER;
import org.restheart.handlers.IllegalQueryParamenterException;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.RequestContext.DOC_ID_TYPE;
import static org.restheart.handlers.RequestContext.DOC_ID_TYPE_QPARAM_KEY;
import org.restheart.utils.URLUtils;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RepUtils {

    /**
     *
     * @param exchange
     * @param context
     * @param size
     * @return
     * @throws IllegalQueryParamenterException
     */
    public static TreeMap<String, String> getPaginationLinks(
            HttpServerExchange exchange,
            RequestContext context,
            long size) throws IllegalQueryParamenterException {

        String requestPath = URLUtils.removeTrailingSlashes(exchange.getRequestPath());
        String queryString = URLUtils.decodeQueryString(exchange.getQueryString());

        int page = context.getPage();
        int pagesize = context.getPagesize();
        long totalPages = 0;

        if (size >= 0) {
            float _size = size + 0f;
            float _pagesize = pagesize + 0f;

            totalPages = Math.max(1, Math.round(Math.ceil(_size / _pagesize)));
        }

        TreeMap<String, String> links = new TreeMap<>();

        if (queryString == null || queryString.isEmpty()) {
            // i.e. the url contains the count paramenter and there is a next page
            if (totalPages > 0 && page < totalPages) {
                links.put("next", requestPath + "?page=" + (page + 1) + "&pagesize=" + pagesize);
            }
        } else {
            String queryStringNoPagingProps = URLUtils.decodeQueryString(
                    URLUtils.getQueryStringRemovingParams(exchange, "page", "pagesize")
            );

            if (queryStringNoPagingProps == null || queryStringNoPagingProps.isEmpty()) {
                links.put("first", requestPath + "?pagesize=" + pagesize);
                links.put("next", requestPath + "?page=" + (page + 1) + "&pagesize=" + pagesize);

                // i.e. the url contains the count paramenter
                if (totalPages > 0) {
                    if (page < totalPages) {
                        links.put("last", requestPath
                                + (totalPages != 1 ? "?page=" + totalPages : "")
                                + "&pagesize=" + pagesize);
                        links.put("next", requestPath + "?page=" + (page + 1)
                                + "&pagesize=" + pagesize + "&" + queryStringNoPagingProps);
                    } else {
                        links.put("last", requestPath + (totalPages != 1 ? "?page=" + totalPages : "")
                                + "&pagesize=" + pagesize);
                    }
                }

                if (page > 1) {
                    links.put("previous", requestPath + "?page=" + (page - 1) + "&pagesize=" + pagesize);
                }
            } else {
                links.put("first", requestPath + "?pagesize=" + pagesize + "&" + queryStringNoPagingProps);

                if (totalPages <= 0) {
                    links.put("next", requestPath + "?page=" + (page + 1)
                            + "&pagesize=" + pagesize + "&" + queryStringNoPagingProps);
                }

                // i.e. the url contains the count paramenter
                if (totalPages > 0) {
                    if (page < totalPages) {
                        links.put("last", requestPath + (totalPages != 1 ? "?page=" + totalPages : "")
                                + (totalPages == 1 ? "?pagesize=" : "&pagesize=") + pagesize
                                + "&" + queryStringNoPagingProps);
                        links.put("next", requestPath + "?page=" + (page + 1)
                                + "&pagesize=" + pagesize + "&" + queryStringNoPagingProps);
                    } else {
                        links.put("last", requestPath + (totalPages != 1 ? "?page=" + totalPages : "")
                                + (totalPages == 1 ? "?pagesize=" : "&pagesize=")
                                + pagesize + "&" + queryStringNoPagingProps);
                    }
                }

                if (page > 1) {
                    links.put("previous", requestPath + "?page=" + (page - 1)
                            + "&pagesize=" + pagesize + "&" + queryStringNoPagingProps);
                }
            }
        }

        return links;
    }
    
    public static String getReferenceLink(
            RequestContext context,
            String parentUrl,
            BsonValue docId) {
        if (context == null || parentUrl == null) {
            LOGGER.error("error creating URI, null arguments: "
                    + "context = {}, parentUrl = {}, docId = {}",
                    context,
                    parentUrl,
                    docId);
            return "";
        }

        String uri = "#";

        if (docId == null) {
            uri = URLUtils.removeTrailingSlashes(parentUrl)
                    .concat("/")
                    .concat("_null");
        } else if (docId.isString()
                && ObjectId.isValid(docId.asString().getValue())) {
            uri = URLUtils.removeTrailingSlashes(parentUrl)
                    .concat("/")
                    .concat(docId.asString().getValue())
                    .concat("?")
                    .concat(DOC_ID_TYPE_QPARAM_KEY)
                    .concat("=")
                    .concat(DOC_ID_TYPE.STRING.name());
        } else if (docId.isString()) {
            uri = URLUtils.removeTrailingSlashes(parentUrl)
                    .concat("/")
                    .concat(docId.asString().getValue());
        } else if (docId.isObjectId()) {
            uri = URLUtils.removeTrailingSlashes(parentUrl)
                    .concat("/")
                    .concat(docId.asObjectId().getValue().toString());
        } else if (docId.isBoolean()) {
            uri = URLUtils.removeTrailingSlashes(parentUrl)
                    .concat("/")
                    .concat("_" + docId.asBoolean().getValue());
        } else if (docId.isInt32()) {
            uri = URLUtils.removeTrailingSlashes(parentUrl)
                    .concat("/")
                    .concat("" + docId.asInt32().getValue())
                    .concat("?")
                    .concat(DOC_ID_TYPE_QPARAM_KEY)
                    .concat("=")
                    .concat(DOC_ID_TYPE.NUMBER.name());
        } else if (docId.isInt64()) {
            uri = URLUtils.removeTrailingSlashes(parentUrl)
                    .concat("/")
                    .concat("" + docId.asInt64().getValue())
                    .concat("?")
                    .concat(DOC_ID_TYPE_QPARAM_KEY)
                    .concat("=")
                    .concat(DOC_ID_TYPE.NUMBER.name());
        } else if (docId.isDouble()) {
            uri = URLUtils.removeTrailingSlashes(parentUrl)
                    .concat("/")
                    .concat("" + docId.asDouble().getValue())
                    .concat("?")
                    .concat(DOC_ID_TYPE_QPARAM_KEY)
                    .concat("=")
                    .concat(DOC_ID_TYPE.NUMBER.name());
        } else if (docId.isNull()) {
            uri = URLUtils.removeTrailingSlashes(parentUrl)
                    .concat("/_null");
        } else if (docId instanceof BsonMaxKey) {
            uri = URLUtils.removeTrailingSlashes(parentUrl)
                    .concat("/_MaxKey");
        } else if (docId instanceof BsonMinKey) {
            uri = URLUtils.removeTrailingSlashes(parentUrl)
                    .concat("/_MinKey");
        } else if (docId.isDateTime()) {
            uri = URLUtils.removeTrailingSlashes(parentUrl)
                    .concat("/")
                    .concat("" + docId.asDateTime().getValue())
                    .concat("?")
                    .concat(DOC_ID_TYPE_QPARAM_KEY)
                    .concat("=")
                    .concat(DOC_ID_TYPE.DATE.name());
        } else {
            String _id;

            try {
                _id = URLUtils.getIdString(docId);
            } catch (UnsupportedDocumentIdException uie) {
                _id = docId.toString();
            }

            context.addWarning("resource with _id: "
                    + _id + " does not have an URI "
                    + "since the _id is of type "
                    + docId.getClass().getSimpleName());
        }

        return uri;
    }

    public static String getReferenceLink(String parentUrl, Object docId) {
        if (parentUrl == null) {
            LOGGER.error("error creating URI, null arguments: "
                    + "parentUrl = {}, docId = {}",
                    parentUrl,
                    docId);
            return "";
        }

        String uri;

        if (docId == null) {
            uri = URLUtils.removeTrailingSlashes(parentUrl)
                    .concat("/")
                    .concat("_null");
        } else if (docId instanceof String && ObjectId.isValid((String) docId)) {
            uri = URLUtils.removeTrailingSlashes(parentUrl)
                    .concat("/")
                    .concat(docId.toString())
                    .concat("?")
                    .concat(DOC_ID_TYPE_QPARAM_KEY)
                    .concat("=")
                    .concat(DOC_ID_TYPE.STRING.name());
        } else if (docId instanceof String || docId instanceof ObjectId) {
            uri = URLUtils.removeTrailingSlashes(parentUrl)
                    .concat("/")
                    .concat(docId.toString());
        } else if (docId instanceof BsonObjectId) {
            uri = URLUtils.removeTrailingSlashes(parentUrl)
                    .concat("/")
                    .concat(((BsonObjectId) docId).getValue().toString());
        } else if (docId instanceof BsonString) {
            uri = URLUtils.removeTrailingSlashes(parentUrl)
                    .concat("/")
                    .concat(((BsonString) docId).getValue());
        } else if (docId instanceof BsonBoolean) {
            uri = URLUtils.removeTrailingSlashes(parentUrl)
                    .concat("/")
                    .concat("_" + ((BsonBoolean) docId).getValue());
        } else if (docId instanceof BsonInt32) {
            uri = URLUtils.removeTrailingSlashes(parentUrl)
                    .concat("/")
                    .concat("" + ((BsonNumber) docId).asInt32().getValue());
        } else if (docId instanceof BsonInt64) {
            uri = URLUtils.removeTrailingSlashes(parentUrl)
                    .concat("/")
                    .concat("" + ((BsonNumber) docId).asInt64().getValue());
        } else if (docId instanceof BsonDouble) {
            uri = URLUtils.removeTrailingSlashes(parentUrl)
                    .concat("/")
                    .concat("" + ((BsonDouble) docId).asDouble().getValue());
        } else if (docId instanceof BsonNull) {
            uri = URLUtils.removeTrailingSlashes(parentUrl)
                    .concat("/_null");
        } else if (docId instanceof BsonMaxKey) {
            uri = URLUtils.removeTrailingSlashes(parentUrl)
                    .concat("/_MaxKey");
        } else if (docId instanceof BsonMinKey) {
            uri = URLUtils.removeTrailingSlashes(parentUrl)
                    .concat("/_MinKey");
        } else if (docId instanceof BsonDateTime) {
            uri = URLUtils.removeTrailingSlashes(parentUrl)
                    .concat("/")
                    .concat("" + ((BsonDateTime) docId).getValue());
        } else if (docId instanceof Integer) {
            uri = URLUtils.removeTrailingSlashes(parentUrl)
                    .concat("/")
                    .concat(docId.toString())
                    .concat("?")
                    .concat(DOC_ID_TYPE_QPARAM_KEY)
                    .concat("=")
                    .concat(DOC_ID_TYPE.NUMBER.name());
        } else if (docId instanceof Long) {
            uri = URLUtils.removeTrailingSlashes(parentUrl)
                    .concat("/")
                    .concat(docId.toString())
                    .concat("?")
                    .concat(DOC_ID_TYPE_QPARAM_KEY)
                    .concat("=")
                    .concat(DOC_ID_TYPE.NUMBER.name());
        } else if (docId instanceof Float) {
            uri = URLUtils.removeTrailingSlashes(parentUrl)
                    .concat("/")
                    .concat(docId.toString())
                    .concat("?")
                    .concat(DOC_ID_TYPE_QPARAM_KEY)
                    .concat("=")
                    .concat(DOC_ID_TYPE.NUMBER.name());
        } else if (docId instanceof Double) {
            uri = URLUtils.removeTrailingSlashes(parentUrl)
                    .concat("/")
                    .concat(docId.toString())
                    .concat("?")
                    .concat(DOC_ID_TYPE_QPARAM_KEY)
                    .concat("=")
                    .concat(DOC_ID_TYPE.NUMBER.name());
        } else if (docId instanceof MinKey) {
            uri = URLUtils.removeTrailingSlashes(parentUrl)
                    .concat("/")
                    .concat("_MinKey");
        } else if (docId instanceof MaxKey) {
            uri = URLUtils.removeTrailingSlashes(parentUrl)
                    .concat("/")
                    .concat("_MaxKey");
        } else if (docId instanceof Date) {
            uri = URLUtils.removeTrailingSlashes(parentUrl)
                    .concat("/")
                    .concat(((Date) docId).getTime() + "")
                    .concat("?")
                    .concat(DOC_ID_TYPE_QPARAM_KEY)
                    .concat("=")
                    .concat(DOC_ID_TYPE.DATE.name());
        } else if (docId instanceof Boolean) {
            uri = URLUtils.removeTrailingSlashes(parentUrl)
                    .concat("/")
                    .concat("_" + (boolean) docId);
        } else {
            uri = URLUtils.removeTrailingSlashes(parentUrl)
                    .concat("/")
                    .concat("_? (unsuppored _id type)");
        }

        return uri;
    }

    private RepUtils() {
    }
}
