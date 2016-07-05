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
package org.restheart.handlers.injectors;

import com.mongodb.util.JSON;
import io.undertow.server.HttpServerExchange;
import java.util.Arrays;
import java.util.Deque;
import java.util.Optional;
import org.bson.BSONObject;
import org.bson.BsonDocument;
import org.bson.json.JsonParseException;
import org.restheart.db.CursorPool.EAGER_CURSOR_ALLOCATION_POLICY;
import org.restheart.hal.UnsupportedDocumentIdException;
import org.restheart.hal.metadata.AggregationPipeline;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.RequestContext.DOC_ID_TYPE;
import static org.restheart.handlers.RequestContext.EAGER_CURSOR_ALLOCATION_POLICY_QPARAM_KEY;
import static org.restheart.handlers.RequestContext.FILTER_QPARAM_KEY;
import static org.restheart.handlers.RequestContext.KEYS_QPARAM_KEY;
import static org.restheart.handlers.RequestContext.PAGESIZE_QPARAM_KEY;
import static org.restheart.handlers.RequestContext.PAGE_QPARAM_KEY;
import static org.restheart.handlers.RequestContext.SORT_BY_QPARAM_KEY;
import static org.restheart.handlers.RequestContext.HAL_QPARAM_KEY;
import static org.restheart.handlers.RequestContext.HAL_MODE;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;
import org.restheart.utils.URLUtils;
import static org.restheart.handlers.RequestContext.DOC_ID_TYPE_QPARAM_KEY;
import org.restheart.handlers.RequestContext.METHOD;
import org.restheart.handlers.RequestContext.TYPE;
import static org.restheart.handlers.RequestContext.SHARDKEY_QPARAM_KEY;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RequestContextInjectorHandler extends PipedHttpHandler {

    private final String whereUri;
    private final String whatUri;

    /**
     *
     * @param whereUri
     * @param whatUri
     * @param next
     */
    public RequestContextInjectorHandler(String whereUri, String whatUri, PipedHttpHandler next) {
        super(next);

        if (whereUri == null) {
            throw new IllegalArgumentException("whereUri cannot be null. check your mongo-mounts.");
        }

        if (!whereUri.startsWith("/")) {
            throw new IllegalArgumentException("whereUri must start with \"/\". check your mongo-mounts");
        }

        if (!whatUri.startsWith("/") && !whatUri.equals("*")) {
            throw new IllegalArgumentException("whatUri must start with \"/\". check your mongo-mounts");
        }

        this.whereUri = URLUtils.removeTrailingSlashes(whereUri);
        this.whatUri = whatUri;
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        RequestContext rcontext = new RequestContext(exchange, whereUri, whatUri);

        // skip parameters injection if method is OPTIONS
        // this makes sure OPTIONS works even on wrong paramenter
        // e.g. OPTIONS 127.0.0.1:8080?page=a
        if (rcontext.getMethod() == METHOD.OPTIONS) {
            if (getNext() != null) {
                getNext()
                        .handleRequest(exchange, rcontext);
            }

            return;
        }

        // check database name to be a valid mongodb name
        if (rcontext.getDBName() != null
                && rcontext.isDbNameInvalid()) {
            ResponseHelper.endExchangeWithMessage(
                    exchange, 
                    context,
                    HttpStatus.SC_BAD_REQUEST,
                    "illegal database name, see https://docs.mongodb.org/v3.2/reference/limits/#naming-restrictions");
            return;
        }

        // check collection name to be a valid mongodb name
        if (rcontext.getCollectionName() != null
                && rcontext.isCollectionNameInvalid()) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_BAD_REQUEST,
                    "illegal collection name, "
                    + "see https://docs.mongodb.org/v3.2/reference/limits/#naming-restrictions");
            return;
        }

        // check collection name to be a valid mongodb name
        if (rcontext.isReservedResource()) {
            ResponseHelper.endExchangeWithMessage(
                    exchange, context,
                    HttpStatus.SC_FORBIDDEN,
                    "reserved resource");
            return;
        }

        Deque<String> __pagesize = exchange.getQueryParameters().get(PAGESIZE_QPARAM_KEY);

        int page = 1; // default page
        int pagesize = 100; // default pagesize

        if (__pagesize != null && !(__pagesize.isEmpty())) {
            try {
                pagesize = Integer.parseInt(__pagesize.getFirst());
            } catch (NumberFormatException ex) {
                ResponseHelper.endExchangeWithMessage(
                        exchange,
                        context,
                        HttpStatus.SC_BAD_REQUEST,
                        "illegal pagesize paramenter, it is not a number", ex);
                return;
            }
        }

        if (pagesize < 0 || pagesize > 1_000) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_BAD_REQUEST,
                    "illegal page parameter, pagesize must be >= 0 and <= 1000");
            return;
        } else {
            rcontext.setPagesize(pagesize);
        }

        Deque<String> __page = exchange.getQueryParameters().get(PAGE_QPARAM_KEY);

        if (__page != null && !(__page.isEmpty())) {
            try {
                page = Integer.parseInt(__page.getFirst());
            } catch (NumberFormatException ex) {
                ResponseHelper.endExchangeWithMessage(
                        exchange,
                        context,
                        HttpStatus.SC_BAD_REQUEST,
                        "illegal page paramenter, it is not a number", ex);
                return;
            }
        }

        if (page < 1) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_BAD_REQUEST,
                    "illegal page paramenter, it is < 1");
            return;
        } else {
            rcontext.setPage(page);
        }

        Deque<String> __count = exchange.getQueryParameters().get("count");

        if (__count != null) {
            rcontext.setCount(true);
        }
        // get and check sort_by parameter
        Deque<String> sort_by = exchange.getQueryParameters().get("sort_by");

        if (sort_by != null) {
            if (sort_by.stream().anyMatch(s -> s == null || s.isEmpty())) {
                ResponseHelper.endExchangeWithMessage(
                        exchange,
                        context,
                        HttpStatus.SC_BAD_REQUEST,
                        "illegal sort_by paramenter");
                return;
            }

            if (sort_by.stream().anyMatch(s -> s.trim().equals("_last_updated_on") || s.trim().equals("+_last_updated_on") || s.trim().equals("-_last_updated_on"))) {
                rcontext.addWarning("unexepecting sorting; the _last_updated_on timestamp is generated from the _etag property if present");
            }

            if (sort_by.stream().anyMatch(s -> s.trim().equals("_created_on") || s.trim().equals("_created_on") || s.trim().equals("_created_on"))) {
                rcontext.addWarning("unexepecting sorting; the _created_on timestamp is generated from the _id property if it is an ObjectId");
            }

            rcontext.setSortBy(exchange.getQueryParameters().get(SORT_BY_QPARAM_KEY));
        }

        Deque<String> keys = exchange.getQueryParameters().get(KEYS_QPARAM_KEY);
        if (keys != null) {
            if (keys.stream().anyMatch(f -> {
                if (f == null || f.isEmpty()) {
                    ResponseHelper.endExchangeWithMessage(
                            exchange,
                            context,
                            HttpStatus.SC_BAD_REQUEST,
                            "illegal keys paramenter (empty)");
                    return true;
                }

                try {
                    Object _keys = JSON.parse(f);

                    if (!(_keys instanceof BSONObject)) {
                        ResponseHelper.endExchangeWithMessage(
                                exchange,
                                context,
                                HttpStatus.SC_BAD_REQUEST,
                                "illegal keys paramenter, it is not a json object: "
                                + f
                                + " => "
                                + f.getClass().getSimpleName());
                        return true;
                    }
                } catch (Throwable t) {
                    ResponseHelper.endExchangeWithMessage(
                            exchange,
                            context,
                            HttpStatus.SC_BAD_REQUEST,
                            "illegal keys paramenter: " + f, t);
                    return true;
                }

                return false;
            })) {
                return; // an error occurred
            }
            rcontext.setKeys(exchange.getQueryParameters().get(KEYS_QPARAM_KEY));
        }

        // get and check filter parameter
        Deque<String> filters = exchange.getQueryParameters().get(FILTER_QPARAM_KEY);

        if (filters != null) {
            if (filters.stream().anyMatch(f -> {
                if (f == null || f.isEmpty()) {
                    ResponseHelper.endExchangeWithMessage(
                            exchange,
                            context,
                            HttpStatus.SC_BAD_REQUEST,
                            "illegal filter paramenter (empty)");
                    return true;
                }

                try {
                    Object _filter = JSON.parse(f);

                    if (!(_filter instanceof BSONObject)) {
                        ResponseHelper.endExchangeWithMessage(
                                exchange,
                                context,
                                HttpStatus.SC_BAD_REQUEST,
                                "illegal filter paramenter, it is not a json object: "
                                + f
                                + " => "
                                + f.getClass().getSimpleName());
                        return true;
                    } else if (((BSONObject) _filter).keySet().isEmpty()) {
                        ResponseHelper.endExchangeWithMessage(
                                exchange,
                                context,
                                HttpStatus.SC_BAD_REQUEST,
                                "illegal filter paramenter (empty json object)");
                        return true;
                    }

                } catch (Throwable t) {
                    ResponseHelper.endExchangeWithMessage(
                            exchange,
                            context,
                            HttpStatus.SC_BAD_REQUEST,
                            "illegal filter paramenter: " + f, t);
                    return true;
                }

                return false;
            })) {
                return; // an error occurred
            }

            rcontext.setFilter(exchange.getQueryParameters().get(FILTER_QPARAM_KEY));
        }

        // filter qparam is mandatory for bulk DELETE and PATCH 
        if (rcontext.getType() == TYPE.BULK_DOCUMENTS
                && (rcontext.getMethod() == METHOD.DELETE
                || rcontext.getMethod() == METHOD.PATCH)
                && (filters == null || filters.isEmpty())) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_BAD_REQUEST,
                    "filter paramenter is mandatory for bulk write requests");
            return;
        }

        // get and check qarvs parameter
        Deque<String> dqvars = exchange.getQueryParameters().get(RequestContext.AGGREGATION_VARIABLES_QPARAM_KEY);

        if (dqvars != null) {
            Optional<String> _qvars = dqvars.stream().findFirst();

            if (!_qvars.isPresent()) {
                ResponseHelper.endExchangeWithMessage(
                        exchange,
                        context,
                        HttpStatus.SC_BAD_REQUEST,
                        "illegal avars paramenter (empty)");
                return;
            }

            try {
                BsonDocument qvars;

                try {
                    qvars = BsonDocument.parse(_qvars.get());
                } catch (JsonParseException jpe) {
                    ResponseHelper.endExchangeWithMessage(
                            exchange,
                            context,
                            HttpStatus.SC_BAD_REQUEST,
                            "illegal avars paramenter, it is not a json object: "
                            + _qvars.get());
                    return;
                }

                // throws SecurityException if aVars contains operators
                AggregationPipeline.checkAggregationVariables(qvars);

                rcontext.setAggregationVars(qvars);
            } catch (Throwable t) {
                ResponseHelper.endExchangeWithMessage(
                        exchange,
                        context,
                        HttpStatus.SC_BAD_REQUEST,
                        "illegal avars paramenter: "
                        + _qvars.get(),
                        t);
                return;
            }
        }

        // get and check eager parameter
        Deque<String> __eager = exchange.getQueryParameters().get(EAGER_CURSOR_ALLOCATION_POLICY_QPARAM_KEY);

        // default value
        EAGER_CURSOR_ALLOCATION_POLICY eager = EAGER_CURSOR_ALLOCATION_POLICY.LINEAR;

        if (__eager != null && !__eager.isEmpty()) {
            String _eager = __eager.getFirst();

            if (_eager != null && !_eager.isEmpty()) {
                try {
                    eager = EAGER_CURSOR_ALLOCATION_POLICY.valueOf(_eager.trim().toUpperCase());
                } catch (IllegalArgumentException iae) {
                    ResponseHelper.endExchangeWithMessage(
                            exchange,
                            context,
                            HttpStatus.SC_BAD_REQUEST,
                            "illegal eager paramenter (must be LINEAR, RANDOM or NONE)");
                    return;
                }
            }
        }

        rcontext.setCursorAllocationPolicy(eager);

        // get and check the doc id type parameter
        Deque<String> __docIdType = exchange.getQueryParameters().get(DOC_ID_TYPE_QPARAM_KEY);

        // default value
        DOC_ID_TYPE docIdType = DOC_ID_TYPE.STRING_OID;

        if (__docIdType != null && !__docIdType.isEmpty()) {
            String _docIdType = __docIdType.getFirst();

            if (_docIdType != null && !_docIdType.isEmpty()) {
                try {
                    docIdType = DOC_ID_TYPE.valueOf(_docIdType.trim().toUpperCase());
                } catch (IllegalArgumentException iae) {
                    ResponseHelper.endExchangeWithMessage(
                            exchange,
                            context,
                            HttpStatus.SC_BAD_REQUEST,
                            "illegal "
                            + DOC_ID_TYPE_QPARAM_KEY
                            + " paramenter; must be "
                            + Arrays.toString(DOC_ID_TYPE.values()));
                    return;
                }
            }
        }

        rcontext.setDocIdType(docIdType);

        // for POST the doc _id is set by BodyjectorHandler
        if (rcontext.getMethod() != METHOD.POST) {
            // get and check the document id
            String _docId = rcontext.getDocumentIdRaw();

            try {
                rcontext.setDocumentId(URLUtils.getDocumentIdFromURI(_docId, docIdType));
            } catch (UnsupportedDocumentIdException idide) {
                ResponseHelper.endExchangeWithMessage(
                        exchange,
                        context,
                        HttpStatus.SC_BAD_REQUEST,
                        "wrong document id format: not a valid "
                        + docIdType.name(),
                        idide);
                return;
            }
        }

        // get the HAL query parameter
        Deque<String> __halMode = exchange.getQueryParameters().get(HAL_QPARAM_KEY);

        if (__halMode == null || __halMode.isEmpty()) {
            // default is compact mode
            rcontext.setHalMode(HAL_MODE.COMPACT);
        } else {
            String _halMode = __halMode.getFirst();

            try {
                rcontext.setHalMode(HAL_MODE.valueOf(_halMode.trim().toUpperCase()));
            } catch (IllegalArgumentException iae) {
                ResponseHelper.endExchangeWithMessage(
                        exchange,
                        context,
                        HttpStatus.SC_BAD_REQUEST,
                        "illegal "
                        + HAL_QPARAM_KEY
                        + " paramenter; valid values are "
                        + Arrays.toString(HAL_MODE.values()));
            }
        }

        // get the shardkey query parameter
        // get and check shardKeys parameter
        Deque<String> shardKeys = exchange.getQueryParameters().get(SHARDKEY_QPARAM_KEY);

        if (shardKeys != null) {
            if (shardKeys.stream().anyMatch(f -> {
                if (f == null || f.isEmpty()) {
                    ResponseHelper.endExchangeWithMessage(
                            exchange,
                            context,
                            HttpStatus.SC_BAD_REQUEST,
                            "illegal shardkey paramenter (empty)");
                    return true;
                }

                try {
                    BsonDocument _shardKeys = BsonDocument.parse(f);

                    if (_shardKeys.keySet().isEmpty()) {
                        ResponseHelper.endExchangeWithMessage(
                                exchange,
                                context,
                                HttpStatus.SC_BAD_REQUEST,
                                "illegal shardkey paramenter (empty json object)");
                        return true;
                    }

                    rcontext.setShardKey(_shardKeys);

                } catch (Throwable t) {
                    ResponseHelper.endExchangeWithMessage(
                            exchange,
                            context,
                            HttpStatus.SC_BAD_REQUEST,
                            "illegal shardkey paramenter: "
                            + f,
                            t);
                    return true;
                }

                return false;
            })) {
                return; // an error occurred
            }

            rcontext.setFilter(exchange.getQueryParameters().get(FILTER_QPARAM_KEY));
        }

        getNext().handleRequest(exchange, rcontext);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        handleRequest(exchange, new RequestContext(exchange, whereUri, whatUri));
    }
}
