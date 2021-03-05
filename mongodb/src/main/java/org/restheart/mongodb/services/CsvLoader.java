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
package org.restheart.mongodb.services;

import com.mongodb.client.model.FindOneAndUpdateOptions;
import io.undertow.server.HttpServerExchange;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.json.JsonParseException;
import org.restheart.exchange.BsonFromCsvRequest;
import org.restheart.exchange.BsonResponse;
import org.restheart.mongodb.db.MongoClientSingleton;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.Service;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.BsonUtils;

/**
 * service to upload a csv file in a collection
 *
 * query parameters:<br>
 * - db=&lt;db_name&gt; *required<br>
 * - coll=&lt;collection_name&gt; *required<br>
 * - id=&lt;id_column_index&gt; optional (default: no _id column, each row will
 * get an new ObjectId)<br>
 * - sep=&lt;column_separator&gt; optional (default: ,)<br>
 * - props=&lt;props&gt; optional (default: no props) additional props to add to
 * each row<br>
 * - values=&lt;values&gt; optional (default: no values) values of additional
 * props to add to each row<br>
 * defined in conf file) of a tranformer to apply to imported data - update
 * optional (default: no).use data to update matching documents");
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@SuppressWarnings("unchecked")
@RegisterPlugin(name = "csvLoader",
        description = "Uploads a csv file in a collection",
        defaultURI = "/csv")
public class CsvLoader implements Service<BsonFromCsvRequest, BsonResponse> {
    /**
     *
     */
    public static final String FILTER_PROPERTY = "_filter";

    private static final String ERROR_QPARAM = "query parameters: " + "db=<db_name> *required, "
            + "coll=<collection_name> *required, "
            + "id=<id_column_index> optional (default: no _id column, each row will get an new ObjectId), "
            + "sep=<column_separator> optional (default: ,), "
            + "props=<props> optional (default: no props) additional props to add to each row, "
            + "values=<values> optional (default: no values) values of additional props to add to each row, "
            + "transformer=<tname> optional (default: no transformer). name of an interceptor to transform data, "
            + "update=<value> optional (default: false). if true, update matching documents (requires id to be set), "
            + "upsert=<value> optional (default: true). when update=true, create new documents when not matching existing ones.";

    private static final String ERROR_NO_ID = "id must be set when update=true";

    // private static final String ERROR_CONTENT_TYPE = "Content-Type request header must be 'text/csv'";

    private static final String ERROR_WRONG_METHOD = "Only POST method is supported";

    // private static final String ERROR_PARSING_DATA = "Error parsing CSV, see logs for more information";

    private final static FindOneAndUpdateOptions FAU_NO_UPSERT_OPS = new FindOneAndUpdateOptions().upsert(false);

    private final static FindOneAndUpdateOptions FAU_WITH_UPSERT_OPS = new FindOneAndUpdateOptions().upsert(true);

    /**
     *
     * @throws Exception
     */
    @Override
    public void handle(BsonFromCsvRequest request, BsonResponse response) throws Exception {
        var exchange = request.getExchange();

        if (request.isOptions()) {
            handleOptions(request);
        } else {
            response.setContentTypeAsJson();
            if (doesApply(request)) {
                try {
                    CsvRequestParams params = new CsvRequestParams(exchange);

                    if (params.db == null) {
                        response.setInError(HttpStatus.SC_BAD_REQUEST, "db qparam is mandatory");
                        return;
                    }

                    if (params.coll == null) {
                        response.setInError(HttpStatus.SC_BAD_REQUEST, "coll qparam is mandatory");
                        return;
                    }

                    if (params.update && params.idIdx < 0) {
                        response.setInError(HttpStatus.SC_BAD_REQUEST, ERROR_NO_ID);
                    } else {
                        BsonArray documents = request.getContent();

                        if (documents != null && documents.size() > 0) {
                            var mcoll = MongoClientSingleton.getInstance().getClient()
                                    .getDatabase(params.db).getCollection(params.coll, BsonDocument.class);

                            if (params.update && !params.upsert) {
                                documents.stream()
                                        .map(doc -> doc.asDocument())
                                        // add props specified via keys and values qparams
                                        .map(doc -> addProps(params, doc))
                                        .forEach(doc -> {
                                            BsonDocument updateQuery = new BsonDocument("_id", doc.remove("_id"));

                                            // for upate import, take _filter property into account
                                            // for instance, a filter allows to use $ positional array operator
                                            BsonValue _filter = doc.remove(FILTER_PROPERTY);

                                            if (_filter != null && _filter.isDocument()) {
                                                updateQuery.putAll(_filter.asDocument());
                                            }
                                            if (params.upsert) {
                                                mcoll.findOneAndUpdate(updateQuery, new BsonDocument("$set", doc),
                                                        FAU_WITH_UPSERT_OPS);
                                            } else {

                                                mcoll.findOneAndUpdate(updateQuery, new BsonDocument("$set", doc),
                                                        FAU_NO_UPSERT_OPS);
                                            }
                                        });
                            } else if (params.update && params.upsert) {
                                documents.stream()
                                        .map(doc -> doc.asDocument())
                                        // add props specified via keys and values qparams
                                        .map(doc -> addProps(params, doc))
                                        .forEach(doc -> {
                                            var updateQuery = new BsonDocument("_id", doc.remove("_id"));

                                            mcoll.findOneAndUpdate(updateQuery, new BsonDocument("$set", doc),
                                                    FAU_WITH_UPSERT_OPS);
                                        });
                            } else {
                                var docList = documents.stream()
                                        .map(doc -> doc.asDocument())
                                        // add props specified via keys and values qparams
                                        .map(doc -> addProps(params, doc))
                                        .collect(Collectors.toList());

                                mcoll.insertMany(docList);
                            }
                            response.setStatusCode(HttpStatus.SC_OK);
                        } else {
                            response.setStatusCode(HttpStatus.SC_NOT_MODIFIED);
                        }
                    }
                } catch (IllegalArgumentException iae) {
                    response.setInError(HttpStatus.SC_BAD_REQUEST,
                            ERROR_QPARAM);
                }
            } else {
                response.setInError(HttpStatus.SC_NOT_IMPLEMENTED,
                        ERROR_WRONG_METHOD);
            }
        }
    }

    private boolean doesApply(BsonFromCsvRequest request) {
        return request.isPost();
    }

    private BsonDocument addProps(CsvRequestParams params, BsonDocument doc) {
        if (params.props != null && params.values != null) {
            @SuppressWarnings("rawtypes")
            Deque<String> _props = new ArrayDeque(params.props);

            @SuppressWarnings("rawtypes")
            Deque<String> _values = new ArrayDeque(params.values);

            while (!_props.isEmpty() && !_values.isEmpty()) {
                doc.append(_props.pop(), getBsonValue(_values.poll()));
            }
        }

        return doc;
    }

    private BsonValue getBsonValue(String raw) {
        try {
            return BsonUtils.parse(raw);
        } catch (JsonParseException jpe) {
            return new BsonString(raw);
        }
    }

    @Override
    public Consumer<HttpServerExchange> requestInitializer() {
        return e -> BsonFromCsvRequest.init(e);
    }

    @Override
    public Consumer<HttpServerExchange> responseInitializer() {
        return e -> BsonResponse.init(e);
    }

    @Override
    public Function<HttpServerExchange, BsonFromCsvRequest> request() {
        return e -> BsonFromCsvRequest.of(e);
    }

    @Override
    public Function<HttpServerExchange, BsonResponse> response() {
        return e -> BsonResponse.of(e);
    }
}

class CsvRequestParams {
    private static final String ID_IDX_QPARAM_NAME = "id";
    private static final String SEPARATOR_QPARAM_NAME = "sep";
    private static final String DB_QPARAM_NAME = "db";
    private static final String COLL_QPARAM_NAME = "coll";
    private static final String PROP_KEYS_NAME = "props";
    private static final String PROP_VALUES_NAME = "values";
    private static final String UPDATE_QPARAM_NAME = "update";
    private static final String UPSERT_QPARAM_NAME = "upsert";

    public final int idIdx;
    public final String db;
    public final String coll;
    public final String sep;
    public final boolean update;
    public final boolean upsert;

    public final Deque<String> props;
    public final Deque<String> values;

    CsvRequestParams(HttpServerExchange exchange) {
        Deque<String> _db = exchange.getQueryParameters().get(DB_QPARAM_NAME);
        Deque<String> _coll = exchange.getQueryParameters().get(COLL_QPARAM_NAME);
        Deque<String> _sep = exchange.getQueryParameters().get(SEPARATOR_QPARAM_NAME);
        Deque<String> _id = exchange.getQueryParameters().get(ID_IDX_QPARAM_NAME);
        Deque<String> _update = exchange.getQueryParameters().get(UPDATE_QPARAM_NAME);
        Deque<String> _upsert = exchange.getQueryParameters().get(UPSERT_QPARAM_NAME);

        this.props = exchange.getQueryParameters().get(PROP_KEYS_NAME);
        this.values = exchange.getQueryParameters().get(PROP_VALUES_NAME);

        db = _db != null ? _db.size() > 0 ? _db.getFirst() : null : null;
        coll = _coll != null ? _coll.size() > 0 ? _coll.getFirst() : null : null;

        sep = _sep != null ? _sep.size() > 0 ? _sep.getFirst() : "" : ",";
        String _idIdx = _id != null ? _id.size() > 0 ? _id.getFirst() : "-1" : "-1";

        try {
            idIdx = Integer.parseInt(_idIdx);
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException(nfe);
        }

        update = _update != null && (_update.isEmpty() || "true".equalsIgnoreCase(_update.getFirst()));

        upsert = _upsert == null
                || _update == null
                || _update.isEmpty()
                || "true".equalsIgnoreCase(_upsert.getFirst());
    }
}
