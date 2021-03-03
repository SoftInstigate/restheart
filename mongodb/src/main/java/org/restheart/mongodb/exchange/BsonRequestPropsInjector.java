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
package org.restheart.mongodb.exchange;

import io.undertow.server.HttpServerExchange;
import java.util.Arrays;
import java.util.Deque;
import java.util.Optional;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.json.JsonParseException;
import static org.restheart.exchange.ExchangeKeys.AGGREGATION_VARIABLES_QPARAM_KEY;
import org.restheart.exchange.ExchangeKeys.DOC_ID_TYPE;
import static org.restheart.exchange.ExchangeKeys.DOC_ID_TYPE_QPARAM_KEY;
import org.restheart.exchange.ExchangeKeys.EAGER_CURSOR_ALLOCATION_POLICY;
import static org.restheart.exchange.ExchangeKeys.EAGER_CURSOR_ALLOCATION_POLICY_QPARAM_KEY;
import static org.restheart.exchange.ExchangeKeys.FILTER_QPARAM_KEY;
import org.restheart.exchange.ExchangeKeys.HAL_MODE;
import static org.restheart.exchange.ExchangeKeys.HAL_QPARAM_KEY;
import static org.restheart.exchange.ExchangeKeys.HINT_QPARAM_KEY;
import static org.restheart.exchange.ExchangeKeys.KEYS_QPARAM_KEY;
import static org.restheart.exchange.ExchangeKeys.PAGESIZE_QPARAM_KEY;
import static org.restheart.exchange.ExchangeKeys.PAGE_QPARAM_KEY;
import org.restheart.exchange.ExchangeKeys.REPRESENTATION_FORMAT;
import static org.restheart.exchange.ExchangeKeys.REPRESENTATION_FORMAT_KEY;
import static org.restheart.exchange.ExchangeKeys.SHARDKEY_QPARAM_KEY;
import static org.restheart.exchange.ExchangeKeys.SORT_BY_QPARAM_KEY;
import static org.restheart.exchange.ExchangeKeys.SORT_QPARAM_KEY;
import org.restheart.exchange.ExchangeKeys.TYPE;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.exchange.UnsupportedDocumentIdException;
import org.restheart.mongodb.MongoServiceConfiguration;
import org.restheart.mongodb.handlers.aggregation.AggregationPipeline;
import org.restheart.mongodb.utils.URLUtils;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.BsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * initialize the MongoRequest

 Assumes that the MongoRequest and MongoRequest are already initialized
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class BsonRequestPropsInjector {
    static final Logger LOGGER
            = LoggerFactory.getLogger(BsonRequestPropsInjector.class);

    private static final int DEFAULT_PAGESIZE = MongoServiceConfiguration
            .get()
            .getDefaultPagesize();

    private static final int MAX_PAGESIZE = MongoServiceConfiguration
            .get()
            .getMaxPagesize();

    /**
     *
     * @param exchange
     */
    public static void inject(HttpServerExchange exchange) {
        var request = MongoRequest.of(exchange);
        var response = MongoResponse.of(exchange);

        // skip parameters injection if method is OPTIONS
        // this makes sure OPTIONS works even on wrong paramenter
        // e.g. OPTIONS 127.0.0.1:8080?page=a
        if (request.isOptions()) {
            return;
        }

        // get and check rep parameter (representation format)
        Deque<String> __rep = exchange
                .getQueryParameters()
                .get(REPRESENTATION_FORMAT_KEY);

        // default value
        REPRESENTATION_FORMAT rep = MongoServiceConfiguration
                .get()
                .getDefaultRepresentationFormat();

        if (__rep != null && !__rep.isEmpty()) {
            String _rep = __rep.getFirst();

            if (_rep != null && !_rep.isEmpty()) {
                try {
                    rep = REPRESENTATION_FORMAT.valueOf(_rep.trim().toUpperCase());
                } catch (IllegalArgumentException iae) {
                    response.addWarning("illegal rep parameter "
                            + _rep
                            + " (must be STANDARD, NESTED or HAL;"
                            + " S is an alias for STANDARD;"
                            + " PLAIN_JSON, PJ are aliases for NESTED)");
                }
            }
        }

        request.setRepresentationFormat(rep);

        // check database name to be a valid mongodb name
        if (request.getDBName() != null
                && request.isDbNameInvalid()) {
            response.setInError(
                    HttpStatus.SC_BAD_REQUEST,
                    "illegal database name, see https://docs.mongodb.org/v3.2/reference/limits/#naming-restrictions");
            return;
        }

        // check collection name to be a valid mongodb name
        if (request.getCollectionName() != null
                && request.isCollectionNameInvalid()) {
            response.setInError(
                    HttpStatus.SC_BAD_REQUEST,
                    "illegal collection name, "
                    + "see https://docs.mongodb.org/v3.2/reference/limits/#naming-restrictions");
            return;
        }

        // check txnId to be a valid long
        if (request.isTxn()) {
            try {
                request.getTxnId();
            } catch (Throwable t) {
                response.setInError(
                        HttpStatus.SC_BAD_REQUEST,
                        "illegal txnId: it must be a number");
                return;
            }
        }

        // check collection name to be a valid mongodb name
        if (request.isReservedResource()) {
            response.setInError(
                    HttpStatus.SC_FORBIDDEN,
                    "reserved resource");
            return;
        }

        Deque<String> __pagesize = exchange.getQueryParameters()
                .get(PAGESIZE_QPARAM_KEY);

        int page = 1; // default page

        int pagesize = DEFAULT_PAGESIZE;

        if (__pagesize != null && !(__pagesize.isEmpty())) {
            try {
                pagesize = Integer.parseInt(__pagesize.getFirst());
            } catch (NumberFormatException ex) {
                response.setInError(
                        HttpStatus.SC_BAD_REQUEST,
                        "illegal pagesize paramenter, it is not a number", ex);
                return;
            }
        }

        if (pagesize < 0 || pagesize > MAX_PAGESIZE) {
            response.setInError(
                    HttpStatus.SC_BAD_REQUEST,
                    "illegal pagesize parameter, pagesize must be >= 0 and <= "
                    + MAX_PAGESIZE);
            return;
        } else {
            request.setPagesize(pagesize);
        }

        Deque<String> __page = exchange.getQueryParameters()
                .get(PAGE_QPARAM_KEY);

        if (__page != null && !(__page.isEmpty())) {
            try {
                page = Integer.parseInt(__page.getFirst());
            } catch (NumberFormatException ex) {
                response.setInError(
                        HttpStatus.SC_BAD_REQUEST,
                        "illegal page paramenter, it is not a number", ex);
                return;
            }
        }

        if (page < 1) {
            response.setInError(
                    HttpStatus.SC_BAD_REQUEST,
                    "illegal page paramenter, it is < 1");
            return;
        } else {
            request.setPage(page);
        }

        Deque<String> __count = exchange.getQueryParameters().get("count");

        if (__count != null) {
            request.setCount(true);
        }

        // get and check sort_by parameter
        Deque<String> sort_by = null;

        if (exchange.getQueryParameters().containsKey(SORT_BY_QPARAM_KEY)) {
            sort_by = exchange.getQueryParameters().get(SORT_BY_QPARAM_KEY);

        } else if (exchange.getQueryParameters().containsKey(SORT_QPARAM_KEY)) {
            sort_by = exchange.getQueryParameters().get(SORT_QPARAM_KEY);
        }

        if (sort_by != null) {
            if (sort_by.stream().anyMatch(s -> s == null || s.isEmpty())) {
                response.setInError(
                        HttpStatus.SC_BAD_REQUEST,
                        "illegal sort_by paramenter");
                return;
            }

            if (sort_by.stream()
                    .anyMatch(s -> s.trim().equals("_last_updated_on")
                    || s.trim().equals("+_last_updated_on")
                    || s.trim().equals("-_last_updated_on"))) {
                response.addWarning("unexepecting sorting; "
                        + "the _last_updated_on timestamp is generated "
                        + "from the _etag property if present");
            }

            if (sort_by.stream()
                    .anyMatch(s -> s.trim().equals("_created_on")
                    || s.trim().equals("_created_on")
                    || s.trim().equals("_created_on"))) {
                response.addWarning("unexepecting sorting; "
                        + "the _created_on timestamp is generated "
                        + "from the _id property if it is an ObjectId");
            }

            request.setSortBy(sort_by);
        }

        // get and check hint parameter
        Deque<String> hint = null;

        if (exchange.getQueryParameters().containsKey(HINT_QPARAM_KEY)) {
            hint = exchange.getQueryParameters().get(HINT_QPARAM_KEY);

        } else if (exchange.getQueryParameters().containsKey(HINT_QPARAM_KEY)) {
            hint = exchange.getQueryParameters().get(HINT_QPARAM_KEY);
        }

        if (hint != null) {
            if (hint.stream().anyMatch(s -> s == null || s.isEmpty())) {
                response.setInError(
                        HttpStatus.SC_BAD_REQUEST,
                        "illegal hint paramenter");
                return;
            }

            request.setHint(hint);
        }

        Deque<String> keys = exchange.getQueryParameters().get(KEYS_QPARAM_KEY);
        if (keys != null) {
            if (keys.stream().anyMatch(f -> {
                if (f == null || f.isEmpty()) {
                    response.setInError(
                            HttpStatus.SC_BAD_REQUEST,
                            "illegal keys paramenter (empty)");
                    return true;
                }

                try {
                    BsonValue _keys = BsonUtils.parse(f);

                    if (!_keys.isDocument()) {
                        response.setInError(
                                HttpStatus.SC_BAD_REQUEST,
                                "illegal keys paramenter, it is not a json object: "
                                + f
                                + " => "
                                + f.getClass().getSimpleName());
                        return true;
                    }
                } catch (Throwable t) {
                    response.setInError(
                            HttpStatus.SC_BAD_REQUEST,
                            "illegal keys paramenter: " + f, t);
                    return true;
                }

                return false;
            })) {
                return; // an error occurred
            }
            request.setKeys(exchange.getQueryParameters().get(KEYS_QPARAM_KEY));
        }

        // get and check filter parameter
        Deque<String> filters = exchange.getQueryParameters().get(FILTER_QPARAM_KEY);

        if (filters != null) {
            if (filters.stream().anyMatch(f -> {
                if (f == null || f.isEmpty()) {
                    response.setInError(
                            HttpStatus.SC_BAD_REQUEST,
                            "illegal filter paramenter (empty)");
                    return true;
                }

                try {
                    BsonValue _filter = BsonUtils.parse(f);

                    if (!_filter.isDocument()) {
                        response.setInError(
                                HttpStatus.SC_BAD_REQUEST,
                                "illegal filter paramenter, it is not a json object: "
                                + f
                                + " => "
                                + f.getClass().getSimpleName());
                        return true;
                    } else if (_filter.asDocument().keySet().isEmpty()) {
                        response.setInError(
                                HttpStatus.SC_BAD_REQUEST,
                                "illegal filter paramenter (empty json object)");
                        return true;
                    }

                } catch (Throwable t) {
                    response.setInError(
                            HttpStatus.SC_BAD_REQUEST,
                            "illegal filter paramenter: " + f, t);
                    return true;
                }

                return false;
            })) {
                return; // an error occurred
            }

            request.setFilter(exchange.getQueryParameters().get(FILTER_QPARAM_KEY));
        }

        // filter qparam is mandatory for bulk DELETE and PATCH 
        if (request.getType() == TYPE.BULK_DOCUMENTS
                && (request.isDelete() || request.isPatch())
                && (filters == null || filters.isEmpty())) {
            response.setInError(
                    HttpStatus.SC_BAD_REQUEST,
                    "filter paramenter is mandatory for bulk write requests");

            return;
        }

        // get and check avars parameter
        Deque<String> avars = exchange.getQueryParameters().get(AGGREGATION_VARIABLES_QPARAM_KEY);

        if (avars != null) {
            Optional<String> _qvars = avars.stream().findFirst();

            if (!_qvars.isPresent()) {
                response.setInError(
                        HttpStatus.SC_BAD_REQUEST,
                        "Illegal avars paramenter (empty)");

                return;
            }

            try {
                BsonDocument qvars;

                try {
                    qvars = BsonDocument.parse(_qvars.get());
                } catch (JsonParseException jpe) {
                    response.setInError(
                            HttpStatus.SC_BAD_REQUEST,
                            "illegal avars paramenter, it is not a json object: "
                            + _qvars.get());

                    return;
                }

                // throws SecurityException if aVars contains operators
                if (MongoServiceConfiguration.get()
                        .getAggregationCheckOperators()) {
                    AggregationPipeline.checkAggregationVariables(qvars);
                }

                request.setAggregationVars(qvars);
            } catch (SecurityException t) {
                response.setInError(
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
        EAGER_CURSOR_ALLOCATION_POLICY eager = EAGER_CURSOR_ALLOCATION_POLICY.NONE;

        if (__eager != null && !__eager.isEmpty()) {
            String _eager = __eager.getFirst();

            if (_eager != null && !_eager.isEmpty()) {
                try {
                    eager = EAGER_CURSOR_ALLOCATION_POLICY.valueOf(_eager.trim().toUpperCase());
                } catch (IllegalArgumentException iae) {
                    response.setInError(
                            HttpStatus.SC_BAD_REQUEST,
                            "illegal eager paramenter (must be LINEAR, RANDOM or NONE)");
                    return;
                }
            }
        }

        request.setCursorAllocationPolicy(eager);

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
                    response.setInError(
                            HttpStatus.SC_BAD_REQUEST,
                            "illegal "
                            + DOC_ID_TYPE_QPARAM_KEY
                            + " paramenter; must be "
                            + Arrays.toString(DOC_ID_TYPE.values()));
                    return;
                }
            }
        }

        request.setDocIdType(docIdType);

        // for POST the doc _id is set by BodyjectorHandler
        if (!request.isPost()) {
            // get and check the document id
            String _docId = request.getDocumentIdRaw();

            try {
                request.setDocumentId(URLUtils.getDocumentIdFromURI(_docId, docIdType));
            } catch (UnsupportedDocumentIdException idide) {
                response.setInError(
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
            request.setHalMode(HAL_MODE.COMPACT);
        } else {
            String _halMode = __halMode.getFirst();

            try {
                request.setHalMode(HAL_MODE.valueOf(_halMode.trim().toUpperCase()));
            } catch (IllegalArgumentException iae) {
                response.setInError(
                        HttpStatus.SC_BAD_REQUEST,
                        "illegal "
                        + HAL_QPARAM_KEY
                        + " paramenter; valid values are "
                        + Arrays.toString(HAL_MODE.values()));

                return;
            }

            // if representation has not been set explicitly, set to default
            if (exchange
                    .getQueryParameters()
                    .get(REPRESENTATION_FORMAT_KEY) == null) {

                request.setRepresentationFormat(MongoServiceConfiguration.get()
                        .getDefaultRepresentationFormat());
            }
        }

        // get the shardkey query parameter
        // get and check shardKeys parameter
        Deque<String> shardKeys = exchange.getQueryParameters().get(SHARDKEY_QPARAM_KEY);

        if (shardKeys != null) {
            if (shardKeys.stream().anyMatch(f -> {
                if (f == null || f.isEmpty()) {
                    response.setInError(
                            HttpStatus.SC_BAD_REQUEST,
                            "illegal shardkey paramenter (empty)");

                    return true;
                }

                try {
                    BsonDocument _shardKeys = BsonDocument.parse(f);

                    if (_shardKeys.keySet().isEmpty()) {
                        response.setInError(
                                HttpStatus.SC_BAD_REQUEST,
                                "illegal shardkey paramenter (empty json object)");

                        return true;
                    }

                    request.setShardKey(_shardKeys);

                } catch (Throwable t) {
                    response.setInError(
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

            request.setFilter(exchange.getQueryParameters().get(FILTER_QPARAM_KEY));
        }
    }
}
