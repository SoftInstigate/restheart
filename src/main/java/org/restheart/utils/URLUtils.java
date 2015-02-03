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

import org.restheart.handlers.RequestContext;
import io.undertow.server.HttpServerExchange;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Deque;
import org.bson.types.ObjectId;
import org.restheart.handlers.RequestContext.DOC_ID_TYPE;
import static org.restheart.handlers.RequestContext.DOC_ID_TYPE.DOUBLE;
import static org.restheart.handlers.RequestContext.DOC_ID_TYPE.FLOAT;
import static org.restheart.handlers.RequestContext.DOC_ID_TYPE.INT;
import static org.restheart.handlers.RequestContext.DOC_ID_TYPE.LONG;
import static org.restheart.handlers.RequestContext.DOC_ID_TYPE.OBJECTID;
import static org.restheart.handlers.RequestContext.DOC_ID_TYPE.STRING;
import static org.restheart.handlers.RequestContext.DOC_ID_TYPE.STRING_OBJECTID;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class URLUtils {

    public static void checkId(Object id) throws UnsupportedDocumentIdException {
        if (id == null) {
            return;
        }

        String clazz = id.getClass().getName();

        if (clazz.equals("java.lang.String")
                || clazz.equals("org.bson.types.ObjectId")
                || clazz.equals("java.lang.Integer")
                || clazz.equals("java.lang.Long")
                || clazz.equals("java.lang.Float")
                || clazz.equals("java.lang.Double")) {
        } else {
            throw new UnsupportedDocumentIdException("unknown _id type: " + id.getClass().getSimpleName());
        }
    }

    public static Object getId(Object id, DOC_ID_TYPE type) throws UnsupportedDocumentIdException {
        if (id == null) {
            return null;
        }

        if (type == null) {
            type = DOC_ID_TYPE.STRING_OBJECTID;
        }

        String _id = id.toString();

        try {
            switch (type) {
                case STRING_OBJECTID:
                    return getIdAsStringOrObjectId(_id);
                case OBJECTID:
                    return getIdAsObjectId(_id);
                case STRING:
                    return id;
                case INT:
                    return getIdAsInt(_id);
                case LONG:
                    return getIdAsLong(_id);
                case FLOAT:
                    return getIdAsFloat(_id);
                case DOUBLE:
                    return getIdAsDouble(_id);
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
     * @param refFieldType
     * @return
     */
    static public String getUriWithDocId(RequestContext context, String dbName, String collName, Object id, DOC_ID_TYPE refFieldType) {
        StringBuilder sb = new StringBuilder();

        sb.append("/").append(dbName).append("/").append(collName).append("/").append(id);

        if (refFieldType != null && refFieldType != DOC_ID_TYPE.OBJECTID && refFieldType != DOC_ID_TYPE.STRING_OBJECTID) {
            sb.append("?doc_id_type=").append(refFieldType.name());
        }

        return context.mapUri(sb.toString().replaceAll(" ", ""));
    }

    /**
     *
     * @param context
     * @param dbName
     * @param collName
     * @param ids
     * @param detectOids if false adds the detect_oids=false query parameter
     * @return
     * @throws org.restheart.utils.UnsupportedDocumentIdException
     */
    static public String getUriWithFilterMany(RequestContext context, String dbName, String collName, Object[] ids, boolean detectOids) throws UnsupportedDocumentIdException {
        StringBuilder sb = new StringBuilder();

        ///db/coll/?filter={"ref":{"$in":{"a","b","c"}}
        sb.append("/").append(dbName).append("/").append(collName).append("?")
                .append("filter={").append("'").append("_id").append("'").append(":")
                .append("{'$in'").append(":").append(getIdsString(ids)).append("}}");

        if (!detectOids) {
            sb.append("&detect_oids=false");
        }

        return context.mapUri(sb.toString().replaceAll(" ", ""));
    }

    /**
     *
     * @param context
     * @param dbName
     * @param collName
     * @param referenceField
     * @param id
     * @param detectOids if false adds the detect_oids=false query parameter
     * @return
     */
    static public String getUriWithFilterOne(RequestContext context, String dbName, String collName, String referenceField, Object id, boolean detectOids) throws UnsupportedDocumentIdException {
        StringBuilder sb = new StringBuilder();

        ///db/coll/?filter={"ref":{"$in":{"a","b","c"}}
        sb.append("/").append(dbName).append("/").append(collName).append("?")
                .append("filter={").append("'").append(referenceField).append("'")
                .append(":").append(getIdString(id)).append("}");

        if (!detectOids) {
            sb.append("&detect_oids=false");
        }

        return context.mapUri(sb.toString().replaceAll(" ", ""));
    }

    /**
     *
     * @param context
     * @param dbName
     * @param collName
     * @param referenceField
     * @param id
     * @param detectOids if false adds the detect_oids=false query parameter
     * @return
     * @throws org.restheart.utils.UnsupportedDocumentIdException
     */
    static public String getUriWithFilterManyInverse(RequestContext context, String dbName, String collName, String referenceField, Object id, boolean detectOids) throws UnsupportedDocumentIdException {
        StringBuilder sb = new StringBuilder();

        ///db/coll/?filter={'referenceField':{"$elemMatch":{'ids'}}}
        sb.append("/").append(dbName).append("/").append(collName).append("?")
                .append("filter={'").append(referenceField)
                .append("':{").append("'$elemMatch':{'$eq':").append(getIdString(id)).append("}}}");

        if (!detectOids) {
            sb.append("&detect_oids=false");
        }

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

    private static int getIdAsInt(String id) throws IllegalArgumentException {
        try {
            return Integer.parseInt(id, 10);
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("The id is not a valid int " + id, nfe);
        }
    }

    private static long getIdAsLong(String id) throws IllegalArgumentException {
        try {
            return Long.parseLong(id, 10);
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("The id is not a valid long " + id, nfe);
        }
    }

    private static float getIdAsFloat(String id) throws IllegalArgumentException {
        try {
            return Float.parseFloat(id);
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("The id is not a valid float " + id, nfe);
        }
    }

    private static double getIdAsDouble(String id) throws IllegalArgumentException {
        try {
            return Double.parseDouble(id);
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("The id is not a valid double " + id, nfe);
        }
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
        }

        switch (id.getClass().getName()) {
            case "java.lang.String":
                return "'" + ((String) id) + "'";
            case "org.bson.types.ObjectId":
                return "'" + ((ObjectId) id).toString() + "'";
            case "java.lang.Integer":
                return "" + id;
            case "java.lang.Long":
                return "" + id;
            case "java.lang.Float":
                return "" + id;
            case "java.lang.Double":
                return "" + id;
            default:
                throw new UnsupportedDocumentIdException("unknown _id type: " + id.getClass().getSimpleName());
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
