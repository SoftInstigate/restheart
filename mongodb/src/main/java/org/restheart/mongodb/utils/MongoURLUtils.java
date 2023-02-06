/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
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
import java.util.Arrays;
import java.util.Objects;
import org.bson.BsonBoolean;
import org.bson.BsonDateTime;
import org.bson.BsonMaxKey;
import org.bson.BsonMinKey;
import org.bson.BsonNull;
import org.bson.BsonNumber;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.types.ObjectId;
import org.restheart.exchange.ExchangeKeys.DOC_ID_TYPE;
import static org.restheart.exchange.ExchangeKeys.FALSE_KEY_ID;
import static org.restheart.exchange.ExchangeKeys.MAX_KEY_ID;
import static org.restheart.exchange.ExchangeKeys.MIN_KEY_ID;
import static org.restheart.exchange.ExchangeKeys.NULL_KEY_ID;
import static org.restheart.exchange.ExchangeKeys.TRUE_KEY_ID;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.UnsupportedDocumentIdException;
import org.restheart.mongodb.MongoServiceConfiguration;
import org.restheart.utils.BsonUtils;
import org.restheart.utils.URLUtils;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class MongoURLUtils extends URLUtils {

    /**
     *
     * @param id
     * @return
     * @throws UnsupportedDocumentIdException
     */
    public static DOC_ID_TYPE checkId(BsonValue id) throws UnsupportedDocumentIdException {
        Objects.requireNonNull(id);

        var type = id.getBsonType();

        return switch (type) {
            case STRING -> DOC_ID_TYPE.STRING;
            case OBJECT_ID -> DOC_ID_TYPE.OID;
            case BOOLEAN -> DOC_ID_TYPE.BOOLEAN;
            case NULL -> DOC_ID_TYPE.NULL;
            case INT32 -> DOC_ID_TYPE.NUMBER;
            case INT64 -> DOC_ID_TYPE.NUMBER;
            case DOUBLE -> DOC_ID_TYPE.NUMBER;
            case MAX_KEY -> DOC_ID_TYPE.MAXKEY;
            case MIN_KEY -> DOC_ID_TYPE.MINKEY;
            case DATE_TIME -> DOC_ID_TYPE.DATE;
            case TIMESTAMP -> DOC_ID_TYPE.DATE;
            default -> throw new UnsupportedDocumentIdException("unknown _id type: " + id.getClass().getSimpleName());
        };
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
    public static BsonValue getDocumentIdFromURI(String id, DOC_ID_TYPE type) throws UnsupportedDocumentIdException {
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
            return switch (type) {
                case STRING_OID -> getIdAsStringOrObjectId(id);
                case OID -> getIdAsObjectId(id);
                case STRING -> new BsonString(id);
                case NUMBER -> getIdAsNumber(id);
                case MINKEY -> new BsonMinKey();
                case MAXKEY -> new BsonMaxKey();
                case DATE -> getIdAsDate(id);
                case BOOLEAN -> getIdAsBoolean(id);
                case NULL -> new BsonNull();
                default -> new BsonString(id);
            };
        } catch (IllegalArgumentException iar) {
            throw new UnsupportedDocumentIdException(iar);
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
        var ibu = MongoServiceConfiguration.get().getInstanceBaseURL();

        if (ibu == null) {
            return exchange.getRequestURL();
        } else {
            return removeTrailingSlashes(ibu).concat(exchange.getRelativePath());
        }
    }

    /**
     *
     * @param request
     * @param dbName
     * @param collName
     * @param id
     * @return
     * @throws org.restheart.exchange.UnsupportedDocumentIdException
     */
    static public String getUriWithDocId(
        MongoRequest request,
        String dbName,
        String collName,
        BsonValue id) throws UnsupportedDocumentIdException {
        var docIdType = MongoURLUtils.checkId(id);

        var sb = new StringBuilder();

        sb.append("/")
            .append(dbName)
            .append("/")
            .append(collName)
            .append("/")
            .append(getIdAsStringNoBrachets(id));

        if (docIdType == DOC_ID_TYPE.STRING && ObjectId.isValid(id.asString().getValue())) {
            sb.append("?id_type=STRING");
        } else if (docIdType != DOC_ID_TYPE.STRING && docIdType != DOC_ID_TYPE.OID) {
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
     * @throws org.restheart.exchange.UnsupportedDocumentIdException
     */
    static public String getUriWithFilterMany(
        MongoRequest request,
        String dbName,
        String collName,
        BsonValue[] ids) throws UnsupportedDocumentIdException {
        var sb = new StringBuilder();

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
     * @throws org.restheart.exchange.UnsupportedDocumentIdException
     */
    static public String getUriWithFilterOne(
        MongoRequest request,
        String dbName,
        String collName,
        String referenceField,
        BsonValue id) throws UnsupportedDocumentIdException {
        var sb = new StringBuilder();

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
     * @throws org.restheart.exchange.UnsupportedDocumentIdException
     */
    static public String getUriWithFilterManyInverse(
        MongoRequest request,
        String dbName,
        String collName,
        String referenceField,
        BsonValue id) throws UnsupportedDocumentIdException {
        var sb = new StringBuilder();

        ///db/coll/?filter={'referenceField':{"$elemMatch":{'ids'}}}
        sb.append("/").append(dbName).append("/").append(collName).append("?")
            .append("filter={'").append(referenceField)
            .append("':{").append("'$elemMatch':{'$eq':")
            .append(getIdString(id)).append("}}}");

        return BsonUtils.minify(request.mapUri(sb.toString()));
    }

    private static BsonNumber getIdAsNumber(String id) throws IllegalArgumentException {
        var ret = BsonUtils.parse(id);

        if (ret.isNumber()) {
            return ret.asNumber();
        } else {
            throw new IllegalArgumentException("The id is not a valid number " + id);
        }
    }

    private static BsonDateTime getIdAsDate(String id) throws IllegalArgumentException {
        var ret = BsonUtils.parse(id);

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

    private static BsonObjectId getIdAsObjectId(String id) throws IllegalArgumentException {
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

    private static String getIdAsStringNoBrachets(BsonValue id) throws UnsupportedDocumentIdException {
        if (id == null) {
            return null;
        } else if (id.isString()) {
            return id.asString().getValue();
        } else if (id.isObjectId()) {
            return id.asObjectId().getValue().toHexString();
        } else {
            return BsonUtils.minify(BsonUtils.toJson(id));
        }
    }

    private static String getIdsString(BsonValue[] ids) throws UnsupportedDocumentIdException {
        if (ids == null) {
            return null;
        }

        var cont = 0;
        var _ids = new String[ids.length];

        for (BsonValue id : ids) {
            _ids[cont] = getIdString(id);
            cont++;
        }

        return BsonUtils.minify(Arrays.toString(_ids));
    }

    private MongoURLUtils() {
    }
}
