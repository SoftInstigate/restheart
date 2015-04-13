/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
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

import com.mongodb.util.JSONSerializers;
import com.mongodb.util.ObjectSerializer;
import org.restheart.hal.UnsupportedDocumentIdException;
import org.restheart.handlers.RequestContext;
import io.undertow.server.HttpServerExchange;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Date;
import java.util.Deque;
import org.bson.types.MaxKey;
import org.bson.types.MinKey;
import org.bson.types.ObjectId;
import org.restheart.handlers.RequestContext.DOC_ID_TYPE;
import static org.restheart.handlers.RequestContext.DOC_ID_TYPE.STRING;
import static org.restheart.handlers.RequestContext.DOC_ID_TYPE_KEY;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class URLUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(URLUtils.class);
    
    private static final ObjectSerializer serializer = JSONSerializers.getStrict();

    public static String getReferenceLink(RequestContext context, String parentUrl, Object docId) {
        if (context == null || parentUrl == null || docId == null) {
            LOGGER.error("error creating URI, null arguments: context = {}, parentUrl = {}, docId = {}", context, parentUrl, docId);
            return "";
        }

        String uri = "#";

        if (docId instanceof String && ObjectId.isValid((String) docId)) {
            uri = URLUtils.removeTrailingSlashes(parentUrl).concat("/").concat(docId.toString()).concat("?").concat(DOC_ID_TYPE_KEY).concat("=").concat(DOC_ID_TYPE.STRING.name());
        } else if (docId instanceof String || docId instanceof ObjectId) {
            uri = URLUtils.removeTrailingSlashes(parentUrl).concat("/").concat(docId.toString());
        } else if (docId instanceof Integer) {
            uri = URLUtils.removeTrailingSlashes(parentUrl).concat("/").concat(docId.toString()).concat("?").concat(DOC_ID_TYPE_KEY).concat("=").concat(DOC_ID_TYPE.NUMBER.name());
        } else if (docId instanceof Long) {
            uri = URLUtils.removeTrailingSlashes(parentUrl).concat("/").concat(docId.toString()).concat("?").concat(DOC_ID_TYPE_KEY).concat("=").concat(DOC_ID_TYPE.NUMBER.name());
        } else if (docId instanceof Float) {
            uri = URLUtils.removeTrailingSlashes(parentUrl).concat("/").concat(docId.toString()).concat("?").concat(DOC_ID_TYPE_KEY).concat("=").concat(DOC_ID_TYPE.NUMBER.name());
        } else if (docId instanceof Double) {
            uri = URLUtils.removeTrailingSlashes(parentUrl).concat("/").concat(docId.toString()).concat("?").concat(DOC_ID_TYPE_KEY).concat("=").concat(DOC_ID_TYPE.NUMBER.name());
        } else if (docId instanceof MinKey) {
            uri = URLUtils.removeTrailingSlashes(parentUrl).concat("/").concat("_MinKey");
        } else if (docId instanceof MaxKey) {
            uri = URLUtils.removeTrailingSlashes(parentUrl).concat("/").concat("_MaxKey");
        } else if (docId instanceof Date) {
            uri = URLUtils.removeTrailingSlashes(parentUrl).concat("/").concat(((Date) docId).getTime() + "").concat("?").concat(DOC_ID_TYPE_KEY).concat("=").concat(DOC_ID_TYPE.DATE.name());
        } else {
            context.addWarning("this resource does not have an URI since the _id is of type " + docId.getClass().getSimpleName());
        }

        return uri;
    }

    public static DOC_ID_TYPE checkId(Object id) throws UnsupportedDocumentIdException {
        if (id == null) {
            return null;
        }

        String clazz = id.getClass().getName();

        switch (clazz) {
            case "java.lang.String":
                return DOC_ID_TYPE.STRING_OID;
            case "org.bson.types.ObjectId":
                return DOC_ID_TYPE.OID;
            case "java.lang.Integer":
                return DOC_ID_TYPE.NUMBER;
            case "java.lang.Long":
                return DOC_ID_TYPE.NUMBER;
            case "java.lang.Float":
                return DOC_ID_TYPE.NUMBER;
            case "java.lang.Double":
                return DOC_ID_TYPE.NUMBER;
            case "java.util.Date":
                return DOC_ID_TYPE.DATE;
            case "org.bson.types.MaxKey":
                return DOC_ID_TYPE.MAXKEY;
            case "org.bson.types.MinKey":
                return DOC_ID_TYPE.MINKEY;
            default:
                throw new UnsupportedDocumentIdException("unknown _id type: " + id.getClass().getSimpleName());
        }
    }

    public static Object getId(String id, DOC_ID_TYPE type) throws UnsupportedDocumentIdException {
        if (id == null) {
            return null;
        }

        if (type == null) {
            type = DOC_ID_TYPE.STRING_OID;
        }

        // MaxKey can be also determined from the _id
        if (RequestContext.MAX_KEY_ID.equalsIgnoreCase(id)) {
            return new MaxKey();
        }

        // MaxKey can be also determined from the _id
        if (RequestContext.MIN_KEY_ID.equalsIgnoreCase(id)) {
            return new MinKey();
        }

        try {
            switch (type) {
                case STRING_OID:
                    return getIdAsStringOrObjectId(id);
                case OID:
                    return getIdAsObjectId(id);
                case STRING:
                    return id;
                case NUMBER:
                    return getIdAsNumber(id);
                case MINKEY:
                    return new MinKey();
                case MAXKEY:
                    return new MaxKey();
                case DATE:
                    return getIdAsDate(id);
            }
        } catch (IllegalArgumentException iar) {
            throw new UnsupportedDocumentIdException(iar);
        }

        return id;
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
            return URLDecoder.decode(qs.replace("+", "%2B"), "UTF-8").replace("%2B", "+");
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
        if (path == null || path.equals("") || path.equals("/")) {
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
        return exchange.getRequestURL().replaceAll(exchange.getRelativePath(), "");
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
    static public String getUriWithDocId(RequestContext context, String dbName, String collName, Object id) throws UnsupportedDocumentIdException {
        DOC_ID_TYPE docIdType = URLUtils.checkId(id);

        StringBuilder sb = new StringBuilder();
        
        sb.append("/").append(dbName).append("/").append(collName).append("/").append(id);

        if (docIdType == DOC_ID_TYPE.STRING_OID && ObjectId.isValid((String)id)) {
            sb.append("?id_type=STRING");
        } else if (docIdType != DOC_ID_TYPE.STRING_OID) {
            sb.append("?id_type=").append(docIdType.name());
        }
        
        return context.mapUri(sb.toString().replaceAll(" ", ""));
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
    static public String getUriWithFilterMany(RequestContext context, String dbName, String collName, Object[] ids) throws UnsupportedDocumentIdException {
        StringBuilder sb = new StringBuilder();

        ///db/coll/?filter={"ref":{"$in":{"a","b","c"}}
        sb.append("/").append(dbName).append("/").append(collName).append("?")
                .append("filter={").append("'").append("_id").append("'").append(":")
                .append("{'$in'").append(":").append(getIdsString(ids)).append("}}");

        return context.mapUri(sb.toString().replaceAll(" ", ""));
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
    static public String getUriWithFilterOne(RequestContext context, String dbName, String collName, String referenceField, Object id) throws UnsupportedDocumentIdException {
        StringBuilder sb = new StringBuilder();

        ///db/coll/?filter={"ref":{"$in":{"a","b","c"}}
        sb.append("/").append(dbName).append("/").append(collName).append("?")
                .append("filter={").append("'").append(referenceField).append("'")
                .append(":").append(getIdString(id)).append("}");

        return context.mapUri(sb.toString().replaceAll(" ", ""));
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
    static public String getUriWithFilterManyInverse(RequestContext context, String dbName, String collName, String referenceField, Object id) throws UnsupportedDocumentIdException {
        StringBuilder sb = new StringBuilder();

        ///db/coll/?filter={'referenceField':{"$elemMatch":{'ids'}}}
        sb.append("/").append(dbName).append("/").append(collName).append("?")
                .append("filter={'").append(referenceField)
                .append("':{").append("'$elemMatch':{'$eq':").append(getIdString(id)).append("}}}");

        return context.mapUri(sb.toString().replaceAll(" ", ""));
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

    private static Number getIdAsNumber(String id) throws IllegalArgumentException {
        try {
            return Integer.parseInt(id, 10);
        } catch (NumberFormatException nfe) {
            try {
                return Long.parseLong(id, 10);
            } catch (NumberFormatException nfe2) {
                try {
                    return Float.parseFloat(id);
                } catch (NumberFormatException nfe3) {
                    try {
                        return Double.parseDouble(id);
                    } catch (NumberFormatException nfe4) {
                        try {
                            return Float.parseFloat(id);
                        } catch (NumberFormatException nfe5) {
                            throw new IllegalArgumentException("The id is not a valid number " + id, nfe);
                        }
                    }
                }
            }
        }
    }

    private static Date getIdAsDate(String id) throws IllegalArgumentException {
        Date ret;

        try {
            Long n = Long.parseLong(id, 10);

            ret = new Date(n);
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("The id is not a valid date (number of milliseconds since the epoch) " + id, nfe);
        }
        
        return ret;
    }

    private static ObjectId getIdAsObjectId(String id) throws IllegalArgumentException {
        if (!ObjectId.isValid(id)) {
            throw new IllegalArgumentException("The id is not a valid ObjectId " + id);
        }

        return new ObjectId(id);
    }

    private static Object getIdAsStringOrObjectId(String id) {
        if (ObjectId.isValid(id)) {
            return getIdAsObjectId(id);
        }

        return id;
    }

    private static String getIdString(Object id) throws UnsupportedDocumentIdException {
        if (id == null) {
            return null;
        } else {
            return serializer.serialize(id).replace("\"", "'");
        }
    }

    private static String getIdsString(Object[] ids) throws UnsupportedDocumentIdException {
        if (ids == null) {
            return null;
        }

        int cont = 0;
        String[] _ids = new String[ids.length];

        for (Object id : ids) {
            _ids[cont] = getIdString(id);
            cont++;
        }

        return Arrays.toString(_ids);
    }
}
