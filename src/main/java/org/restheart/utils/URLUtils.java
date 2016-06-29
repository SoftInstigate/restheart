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
package org.restheart.utils;

import io.undertow.server.HttpServerExchange;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Date;
import java.util.Deque;
import java.util.Objects;
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
import org.bson.BsonType;
import org.bson.BsonValue;
import org.bson.types.MaxKey;
import org.bson.types.MinKey;
import org.bson.types.ObjectId;
import org.restheart.hal.UnsupportedDocumentIdException;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.RequestContext.DOC_ID_TYPE;
import static org.restheart.handlers.RequestContext.DOC_ID_TYPE.STRING;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.restheart.handlers.RequestContext.DOC_ID_TYPE_QPARAM_KEY;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class URLUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(URLUtils.class);

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
                _id = getIdString(docId);
            } catch(UnsupportedDocumentIdException uie) {
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

    public static DOC_ID_TYPE checkId(BsonValue id)
            throws UnsupportedDocumentIdException {
        Objects.nonNull(id);

        BsonType type = id.getBsonType();

        switch (type) {
            case STRING:
                return DOC_ID_TYPE.STRING;
            case OBJECT_ID:
                return DOC_ID_TYPE.OID;
            case BOOLEAN:
                return DOC_ID_TYPE.BOOLEAN;
            case NULL:
                return DOC_ID_TYPE.NULL;
            case INT32:
                return DOC_ID_TYPE.NUMBER;
            case INT64:
                return DOC_ID_TYPE.NUMBER;
            case DOUBLE:
                return DOC_ID_TYPE.NUMBER;
            case MAX_KEY:
                return DOC_ID_TYPE.MAXKEY;
            case MIN_KEY:
                return DOC_ID_TYPE.MINKEY;
            case DATE_TIME:
                return DOC_ID_TYPE.DATE;
            case TIMESTAMP:
                return DOC_ID_TYPE.DATE;
            default:
                throw new UnsupportedDocumentIdException(
                        "unknown _id type: "
                        + id.getClass()
                        .getSimpleName());
        }
    }

    /**
     * Gets the id as object from its string representation in the document URI
     * NOTE: for POST the special string id are checked by
     * BodyInjectorHandler.checkSpecialStringId()
     *
     * @param id
     * @param type
     * @return
     * @throws UnsupportedDocumentIdException
     */
    public static BsonValue getDocumentIdFromURI(String id, DOC_ID_TYPE type)
            throws UnsupportedDocumentIdException {
        if (id == null) {
            return null;
        }

        if (type == null) {
            type = DOC_ID_TYPE.STRING_OID;
        }

        // MaxKey can be also determined from the _id
        if (RequestContext.MAX_KEY_ID.equalsIgnoreCase(id)) {
            return new BsonMaxKey();
        }

        // MaxKey can be also determined from the _id
        if (RequestContext.MIN_KEY_ID.equalsIgnoreCase(id)) {
            return new BsonMinKey();
        }

        // null can be also determined from the _id
        if (RequestContext.NULL_KEY_ID.equalsIgnoreCase(id)) {
            return new BsonNull();
        }

        // true can be also determined from the _id
        if (RequestContext.TRUE_KEY_ID.equalsIgnoreCase(id)) {
            return new BsonBoolean(true);
        }

        // false can be also determined from the _id
        if (RequestContext.FALSE_KEY_ID.equalsIgnoreCase(id)) {
            return new BsonBoolean(false);
        }

        try {
            switch (type) {
                case STRING_OID:
                    return getIdAsStringOrObjectId(id);
                case OID:
                    return getIdAsObjectId(id);
                case STRING:
                    return new BsonString(id);
                case NUMBER:
                    return getIdAsNumber(id);
                case MINKEY:
                    return new BsonMinKey();
                case MAXKEY:
                    return new BsonMaxKey();
                case DATE:
                    return getIdAsDate(id);
                case BOOLEAN:
                    return getIdAsBoolean(id);
            }
        } catch (IllegalArgumentException iar) {
            throw new UnsupportedDocumentIdException(iar);
        }

        return new BsonString(id);
    }

    /**
     * given string /ciao/this/has/trailings///// returns
     * /ciao/this/has/trailings
     *
     * @param s
     * @return the string s without the trailing slashes
     */
    static public String removeTrailingSlashes(String s) {
        if (s == null || s.length() < 2) {
            return s;
        }

        if (s.trim().charAt(s.length() - 1) == '/') {
            return removeTrailingSlashes(s.substring(0, s.length() - 1));
        } else {
            return s.trim();
        }
    }

    /**
     * decode the percent encoded query string
     *
     * @param qs
     * @return the undecoded string
     */
    static public String decodeQueryString(String qs) {
        try {
            return URLDecoder.decode(
                    qs.replace("+", "%2B"), "UTF-8").replace("%2B", "+");
        } catch (UnsupportedEncodingException ex) {
            return null;
        }
    }

    /**
     *
     * @param path
     * @return
     */
    static public String getParentPath(String path) {
        if (path == null || path.isEmpty() || path.equals("/")) {
            return path;
        }

        int lastSlashPos = path.lastIndexOf('/');

        if (lastSlashPos > 0) {
            return path.substring(0, lastSlashPos); //strip off the slash
        } else if (lastSlashPos == 0) {
            return "/";
        } else {
            return ""; //we expect people to add  + "/somedir on their own
        }
    }

    /**
     *
     * @param exchange
     * @return
     */
    static public String getPrefixUrl(HttpServerExchange exchange) {
        return exchange.getRequestURL()
                .replaceAll(exchange.getRelativePath(), "");
    }

    /**
     *
     * @param context
     * @param dbName
     * @param collName
     * @param id
     * @return
     * @throws org.restheart.hal.UnsupportedDocumentIdException
     */
    static public String getUriWithDocId(
            RequestContext context,
            String dbName,
            String collName,
            BsonValue id)
            throws UnsupportedDocumentIdException {
        DOC_ID_TYPE docIdType = URLUtils.checkId(id);

        StringBuilder sb = new StringBuilder();

        sb
                .append("/")
                .append(dbName)
                .append("/")
                .append(collName)
                .append("/")
                .append(getIdAsStringNoBrachets(id));

        if (docIdType == DOC_ID_TYPE.STRING
                && ObjectId.isValid(id.asString().getValue())) {
            sb.append("?id_type=STRING");
        } else if (docIdType != DOC_ID_TYPE.STRING
                && docIdType != DOC_ID_TYPE.OID) {
            sb.append("?id_type=").append(docIdType.name());
        }

        return context.mapUri(sb.toString());
    }

    /**
     *
     * @param context
     * @param dbName
     * @param collName
     * @param ids
     * @return
     * @throws org.restheart.hal.UnsupportedDocumentIdException
     */
    static public String getUriWithFilterMany(
            RequestContext context,
            String dbName,
            String collName,
            BsonValue[] ids)
            throws UnsupportedDocumentIdException {
        StringBuilder sb = new StringBuilder();

        ///db/coll/?filter={"ref":{"$in":{"a","b","c"}}
        sb.append("/").append(dbName).append("/").append(collName).append("?")
                .append("filter={").append("'")
                .append("_id").append("'").append(":")
                .append("{'$in'").append(":")
                .append(getIdsString(ids)).append("}}");

        return context.mapUri(sb.toString());
    }

    /**
     *
     * @param context
     * @param dbName
     * @param collName
     * @param referenceField
     * @param id
     * @return
     * @throws org.restheart.hal.UnsupportedDocumentIdException
     */
    static public String getUriWithFilterOne(
            RequestContext context,
            String dbName,
            String collName,
            String referenceField,
            BsonValue id)
            throws UnsupportedDocumentIdException {
        StringBuilder sb = new StringBuilder();

        ///db/coll/?filter={"ref":{"$in":{"a","b","c"}}
        sb.append("/").append(dbName).append("/").append(collName).append("?")
                .append("filter={").append("'")
                .append(referenceField).append("'")
                .append(":")
                .append(getIdString(id))
                .append("}");

        return context.mapUri(sb.toString());
    }

    /**
     *
     * @param context
     * @param dbName
     * @param collName
     * @param referenceField
     * @param id
     * @return
     * @throws org.restheart.hal.UnsupportedDocumentIdException
     */
    static public String getUriWithFilterManyInverse(
            RequestContext context,
            String dbName,
            String collName,
            String referenceField,
            BsonValue id)
            throws UnsupportedDocumentIdException {
        StringBuilder sb = new StringBuilder();

        ///db/coll/?filter={'referenceField':{"$elemMatch":{'ids'}}}
        sb.append("/").append(dbName).append("/").append(collName).append("?")
                .append("filter={'").append(referenceField)
                .append("':{").append("'$elemMatch':{'$eq':")
                .append(getIdString(id)).append("}}}");

        return JsonUtils.minify(context.mapUri(sb.toString()));
    }

    /**
     *
     * @param exchange
     * @param paramsToRemove
     * @return
     */
    public static String getQueryStringRemovingParams(HttpServerExchange exchange, String... paramsToRemove) {
        String ret = exchange.getQueryString();

        if (ret == null || ret.isEmpty() || paramsToRemove == null) {
            return ret;
        }

        for (String key : paramsToRemove) {
            Deque<String> values = exchange.getQueryParameters().get(key);

            if (values != null) {
                for (String value : values) {
                    ret = ret.replaceAll(key + "=" + value + "&", "");
                    ret = ret.replaceAll(key + "=" + value + "$", "");
                }
            }
        }

        return ret;
    }

    private static BsonNumber getIdAsNumber(String id) throws IllegalArgumentException {
        BsonValue ret = JsonUtils.parse(id);

        if (ret.isNumber()) {
            return ret.asNumber();
        } else {
            throw new IllegalArgumentException("The id is not a valid number " + id);
        }
    }

    private static BsonDateTime getIdAsDate(String id) throws IllegalArgumentException {
        BsonValue ret = JsonUtils.parse(id);

        if (ret.isDateTime()) {
            return ret.asDateTime();
        } else if (ret.isInt32()) {
            return new BsonDateTime(0l + ret.asInt32().getValue());
        } else if (ret.isInt64()) {
            return new BsonDateTime(ret.asInt64().getValue());
        } else {
            throw new IllegalArgumentException("The id is not a valid number " + id);
        }

    }

    private static BsonBoolean getIdAsBoolean(String id) throws IllegalArgumentException {
        if (id.equals(RequestContext.TRUE_KEY_ID)) {
            return new BsonBoolean(true);
        }

        if (id.equals(RequestContext.FALSE_KEY_ID)) {
            return new BsonBoolean(false);
        }

        return null;
    }

    private static BsonObjectId getIdAsObjectId(String id)
            throws IllegalArgumentException {
        if (!ObjectId.isValid(id)) {
            throw new IllegalArgumentException("The id is not a valid ObjectId " + id);
        }

        return new BsonObjectId(new ObjectId(id));
    }

    private static BsonValue getIdAsStringOrObjectId(String id) {
        if (ObjectId.isValid(id)) {
            return getIdAsObjectId(id);
        }

        return new BsonString(id);
    }

    private static String getIdAsStringNoBrachets(BsonValue id)
            throws UnsupportedDocumentIdException {
        if (id == null) {
            return null;
        } else if (id.isString()) {
            return id.asString().getValue();
        } else {
            return JsonUtils.minify(
                    JsonUtils.toJson(id));
        }
    }

    private static String getIdString(BsonValue id)
            throws UnsupportedDocumentIdException {
        if (id == null) {
            return null;
        } else if (id.isString()) {
            return "'" + id.asString().getValue() + "'";
        } else {
            return JsonUtils.minify(JsonUtils.toJson(id)
                    .replace("\"", "'"));
        }
    }

    private static String getIdsString(BsonValue[] ids)
            throws UnsupportedDocumentIdException {
        if (ids == null) {
            return null;
        }

        int cont = 0;
        String[] _ids = new String[ids.length];

        for (BsonValue id : ids) {
            _ids[cont] = getIdString(id);
            cont++;
        }

        return JsonUtils.minify(Arrays.toString(_ids));
    }

    private URLUtils() {
    }
}
