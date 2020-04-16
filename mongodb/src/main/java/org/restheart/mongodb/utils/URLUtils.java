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
package org.restheart.mongodb.utils;

import io.undertow.server.HttpServerExchange;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Deque;
import java.util.Objects;
import org.bson.BsonBoolean;
import org.bson.BsonDateTime;
import org.bson.BsonMaxKey;
import org.bson.BsonMinKey;
import org.bson.BsonNull;
import org.bson.BsonNumber;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonType;
import static org.bson.BsonType.BOOLEAN;
import static org.bson.BsonType.STRING;
import org.bson.BsonValue;
import org.bson.types.ObjectId;
import org.restheart.exchange.BsonRequest;
import org.restheart.exchange.ExchangeKeys.DOC_ID_TYPE;
import static org.restheart.exchange.ExchangeKeys.DOC_ID_TYPE.MAXKEY;
import static org.restheart.exchange.ExchangeKeys.DOC_ID_TYPE.MINKEY;
import static org.restheart.exchange.ExchangeKeys.DOC_ID_TYPE.NUMBER;
import static org.restheart.exchange.ExchangeKeys.DOC_ID_TYPE.OID;
import static org.restheart.exchange.ExchangeKeys.DOC_ID_TYPE.STRING_OID;
import static org.restheart.exchange.ExchangeKeys.FALSE_KEY_ID;
import static org.restheart.exchange.ExchangeKeys.MAX_KEY_ID;
import static org.restheart.exchange.ExchangeKeys.MIN_KEY_ID;
import static org.restheart.exchange.ExchangeKeys.NULL_KEY_ID;
import static org.restheart.exchange.ExchangeKeys.TRUE_KEY_ID;
import org.restheart.mongodb.MongoServiceConfiguration;
import org.restheart.representation.UnsupportedDocumentIdException;
import org.restheart.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class URLUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(URLUtils.class);

    /**
     *
     * @param id
     * @return
     * @throws UnsupportedDocumentIdException
     */
    public static DOC_ID_TYPE checkId(BsonValue id)
            throws UnsupportedDocumentIdException {
        Objects.requireNonNull(id);

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
        if (MAX_KEY_ID.equalsIgnoreCase(id)) {
            return new BsonMaxKey();
        }

        // MaxKey can be also determined from the _id
        if (MIN_KEY_ID.equalsIgnoreCase(id)) {
            return new BsonMinKey();
        }

        // null can be also determined from the _id
        if (NULL_KEY_ID.equalsIgnoreCase(id)) {
            return new BsonNull();
        }

        // true can be also determined from the _id
        if (TRUE_KEY_ID.equalsIgnoreCase(id)) {
            return new BsonBoolean(true);
        }

        // false can be also determined from the _id
        if (FALSE_KEY_ID.equalsIgnoreCase(id)) {
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
     * returns the request URL taking into account the instance-base-url
     * configuration option. When RESTHeart is exposed via a reverse-proxy or an
     * API gateway it allows mapping the Location header correctly.
     *
     * @param exchange
     * @return
     */
    static public String getRemappedRequestURL(HttpServerExchange exchange) {
        String ibu = MongoServiceConfiguration.get().getInstanceBaseURL();

        if (ibu == null) {
            return exchange.getRequestURL();
        } else {
            return removeTrailingSlashes(ibu)
                    .concat(exchange.getRelativePath());
        }
    }

    /**
     *
     * @param request
     * @param dbName
     * @param collName
     * @param id
     * @return
     * @throws org.restheart.representation.UnsupportedDocumentIdException
     */
    static public String getUriWithDocId(
            BsonRequest request,
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

        return request.mapUri(sb.toString());
    }

    /**
     *
     * @param request
     * @param dbName
     * @param collName
     * @param ids
     * @return
     * @throws org.restheart.representation.UnsupportedDocumentIdException
     */
    static public String getUriWithFilterMany(
            BsonRequest request,
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

        return request.mapUri(sb.toString());
    }

    /**
     *
     * @param request
     * @param dbName
     * @param collName
     * @param referenceField
     * @param id
     * @return
     * @throws org.restheart.representation.UnsupportedDocumentIdException
     */
    static public String getUriWithFilterOne(
            BsonRequest request,
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

        return request.mapUri(sb.toString());
    }

    /**
     *
     * @param request
     * @param dbName
     * @param collName
     * @param referenceField
     * @param id
     * @return
     * @throws org.restheart.representation.UnsupportedDocumentIdException
     */
    static public String getUriWithFilterManyInverse(
            BsonRequest request,
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

        return JsonUtils.minify(request.mapUri(sb.toString()));
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
        if (id.equals(TRUE_KEY_ID)) {
            return new BsonBoolean(true);
        }

        if (id.equals(FALSE_KEY_ID)) {
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
        } else if (id.isObjectId()) {
            return id.asObjectId().getValue().toHexString();
        } else {
            return JsonUtils.minify(
                    JsonUtils.toJson(id));
        }
    }

    /**
     *
     * @param id
     * @return
     * @throws UnsupportedDocumentIdException
     */
    public static String getIdString(BsonValue id)
            throws UnsupportedDocumentIdException {
        if (id == null) {
            return null;
        } else if (id.isString()) {
            return "'" + id.asString().getValue() + "'";
        } else {
            return JsonUtils.toJson(id).replace("\"", "'");
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
