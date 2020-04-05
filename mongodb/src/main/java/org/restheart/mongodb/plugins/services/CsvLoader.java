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
package org.restheart.mongodb.plugins.services;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Scanner;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.json.JsonParseException;
import org.restheart.handlers.exchange.ByteArrayRequest;
import org.restheart.handlers.exchange.ByteArrayResponse;
import org.restheart.handlers.exchange.RequestContext;
import org.restheart.mongodb.db.MongoClientSingleton;
import org.restheart.mongodb.representation.Resource;
import org.restheart.mongodb.utils.ChannelReader;
import org.restheart.plugins.InjectPluginsRegistry;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.Service;
import org.restheart.plugins.mongodb.Transformer;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * service to upload a csv file in a collection<br>
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
 * - transformer=&lt;tname&gt; optional (default: no transformer). name (as
 * defined in conf file) of a tranformer to apply to imported data - update
 * optional (default: no). use data to update matching documents");
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@SuppressWarnings("unchecked")
@RegisterPlugin(name = "csvLoader",
        description = "Uploads a csv file in a collection",
        defaultURI = "/csv")
public class CsvLoader implements Service {

    private PluginsRegistry pluginsRegistry;

    
    @InjectPluginsRegistry
    public void init(PluginsRegistry pluginsRegistry) {
        this.pluginsRegistry = pluginsRegistry;
    }

    /**
     *
     */
    public static final String CVS_CONTENT_TYPE = "text/csv";

    /**
     *
     */
    public static final String FILTER_PROPERTY = "_filter";

    private static final Logger LOGGER = LoggerFactory.getLogger(CsvLoader.class);

    private static final String ERROR_QPARAM = "query parameters: " + "db=<db_name> *required, "
            + "coll=<collection_name> *required, "
            + "id=<id_column_index> optional (default: no _id column, each row will get an new ObjectId), "
            + "sep=<column_separator> optional (default: ,), "
            + "props=<props> optional (default: no props) additional props to add to each row, "
            + "values=<values> optional (default: no values) values of additional props to add to each row, "
            + "transformer=<tname> optional (default: no transformer). name (as defined in conf file) of a tranformer to apply to imported data, "
            + "update=<value> optional (default: false). if true, update matching documents (requires id to be set), "
            + "upsert=<value> optional (default: true). when update=true, create new documents when not matching existing ones.";

    private static final String ERROR_NO_ID = "id must be set when update=true";

    private static final String ERROR_CONTENT_TYPE = "Content-Type request header must be 'text/csv'";

    private static final String ERROR_WRONG_METHOD = "Only POST method is supported";

    private static final String ERROR_PARSING_DATA = "Error parsing CSV, see logs for more information";

    private final static FindOneAndUpdateOptions FAU_NO_UPSERT_OPS = new FindOneAndUpdateOptions().upsert(false);

    private final static FindOneAndUpdateOptions FAU_WITH_UPSERT_OPS = new FindOneAndUpdateOptions().upsert(true);

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handle(HttpServerExchange exchange) throws Exception {
        var request = ByteArrayRequest.wrap(exchange);
        var response = ByteArrayResponse.wrap(exchange);

        if (request.isOptions()) {
            handleOptions(exchange);
        } else {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, Resource.JSON_MEDIA_TYPE);
            if (doesApply(request)) {
                if (checkContentType(exchange)) {
                    try {
                        CsvRequestParams params = new CsvRequestParams(exchange,
                                pluginsRegistry);

                        if (params.update && params.idIdx < 0) {
                            response.endExchangeWithMessage(HttpStatus.SC_BAD_REQUEST,
                                    ERROR_NO_ID);
                        } else {
                            try {
                                final String content = ChannelReader.read(exchange.getRequestChannel());

                                List<BsonDocument> documents = parseCsv(exchange, params, content);

                                if (documents != null && documents.size() > 0) {
                                    MongoCollection<BsonDocument> mcoll = MongoClientSingleton.getInstance().getClient()
                                            .getDatabase(params.db).getCollection(params.coll, BsonDocument.class);

                                    if (params.update && !params.upsert) {
                                        documents.stream().forEach(document -> {
                                            BsonDocument updateQuery = new BsonDocument("_id", document.remove("_id"));

                                            // for upate import, take _filter property into account
                                            // for instance, a filter allows to use $ positional array operator
                                            BsonValue _filter = document.remove(FILTER_PROPERTY);

                                            if (_filter != null && _filter.isDocument()) {
                                                updateQuery.putAll(_filter.asDocument());
                                            }
                                            if (params.upsert) {
                                                mcoll.findOneAndUpdate(updateQuery, new BsonDocument("$set", document),
                                                        FAU_WITH_UPSERT_OPS);
                                            } else {

                                                mcoll.findOneAndUpdate(updateQuery, new BsonDocument("$set", document),
                                                        FAU_NO_UPSERT_OPS);
                                            }
                                        });
                                    } else if (params.update && params.upsert) {
                                        documents.stream().forEach(document -> {
                                            BsonDocument updateQuery = new BsonDocument("_id", document.remove("_id"));

                                            mcoll.findOneAndUpdate(updateQuery, new BsonDocument("$set", document),
                                                    FAU_WITH_UPSERT_OPS);
                                        });
                                    } else {
                                        mcoll.insertMany(documents);
                                    }
                                    response.setStatusCode(HttpStatus.SC_OK);
                                } else {
                                    response.setStatusCode(HttpStatus.SC_NOT_MODIFIED);
                                }
                            } catch (IOException ex) {
                                LOGGER.debug("error parsing CSV data", ex);
                                response.endExchangeWithMessage(HttpStatus.SC_BAD_REQUEST,
                                        ERROR_PARSING_DATA);

                            }
                        }
                    } catch (IllegalArgumentException iae) {
                        response.endExchangeWithMessage(HttpStatus.SC_BAD_REQUEST,
                                ERROR_QPARAM);
                    }
                } else {
                    response.endExchangeWithMessage(HttpStatus.SC_BAD_REQUEST,
                            ERROR_CONTENT_TYPE);
                }

            } else {
                response.endExchangeWithMessage(HttpStatus.SC_NOT_IMPLEMENTED,
                        ERROR_WRONG_METHOD);
            }
        }
    }

    private List<BsonDocument> parseCsv(HttpServerExchange exchange,
            CsvRequestParams params,
            String rawContent) throws IOException {
        List<BsonDocument> listOfBsonDocuments = new ArrayList<>();

        boolean isHeader = true;

        List<String> cols = null;

        try (Scanner scanner = new Scanner(rawContent)) {
            while (scanner.hasNext()) {
                String line = scanner.nextLine();

                // split on the separator only if that comma has zero,
                // or an even number of quotes ahead of it.
                List<String> vals = Arrays.asList(line.split(params.sep + "(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1));

                if (isHeader) {
                    cols = vals;
                } else {
                    BsonDocument doc = new BsonDocument("_etag", new BsonObjectId());

                    int unnamedProps = 0;

                    for (int idx = 0; idx < vals.size(); idx++) {
                        if (idx == params.idIdx) {
                            doc.append("_id", getBsonValue(vals.get(params.idIdx)));
                        } else {
                            String propname;

                            if (cols == null || cols.size() <= idx) {
                                propname = "unnamed_" + unnamedProps;
                                unnamedProps++;
                            } else {
                                propname = cols.get(idx);
                            }

                            doc.append(propname, getBsonValue(vals.get(idx)));
                        }
                    }

                    // add props specified via keys and values qparams
                    addProps(params, doc);

                    // apply transformer if defined
                    if (params.transformer != null) {
                        params.transformer.transform(exchange,
                                RequestContext.wrap(exchange),
                                doc,
                                null);
                    }

                    listOfBsonDocuments.add(doc);
                }

                isHeader = false;
            }
        }

        return listOfBsonDocuments;
    }

    private void addProps(CsvRequestParams params, BsonDocument doc) {
        if (params.props != null && params.values != null) {
            Deque<String> _props = new ArrayDeque(params.props);
            Deque<String> _values = new ArrayDeque(params.values);

            while (!_props.isEmpty() && !_values.isEmpty()) {
                doc.append(_props.pop(), getBsonValue(_values.poll()));
            }
        }
    }

    private BsonValue getBsonValue(String raw) {
        try {
            return JsonUtils.parse(raw);
        } catch (JsonParseException jpe) {
            return new BsonString(raw);
        }
    }

    private boolean doesApply(ByteArrayRequest request) {
        return request.isPost();
    }

    private boolean checkContentType(HttpServerExchange exchange) {
        HeaderValues contentType = exchange.getRequestHeaders().get(Headers.CONTENT_TYPE);

        return contentType != null
                && contentType.stream()
                        .anyMatch(ct
                                -> ct.equals(CVS_CONTENT_TYPE)
                        || ct.startsWith(CVS_CONTENT_TYPE.concat(";")));
    }
}

class CsvRequestParams {

    private static final String ID_IDX_QPARAM_NAME = "id";
    private static final String SEPARATOR_QPARAM_NAME = "sep";
    private static final String DB_QPARAM_NAME = "db";
    private static final String COLL_QPARAM_NAME = "coll";
    private static final String TRANFORMER_QPARAM_NAME = "transformer";
    private static final String PROP_KEYS_NAME = "props";
    private static final String PROP_VALUES_NAME = "values";
    private static final String UPDATE_QPARAM_NAME = "update";
    private static final String UPSERT_QPARAM_NAME = "upsert";

    public final int idIdx;
    public final String db;
    public final String coll;
    public final String sep;
    public final Transformer transformer;
    public final boolean update;
    public final boolean upsert;

    public final Deque<String> props;
    public final Deque<String> values;

    CsvRequestParams(HttpServerExchange exchange, PluginsRegistry pluginsRegistry) {
        Deque<String> _db = exchange.getQueryParameters().get(DB_QPARAM_NAME);
        Deque<String> _coll = exchange.getQueryParameters().get(COLL_QPARAM_NAME);
        Deque<String> _sep = exchange.getQueryParameters().get(SEPARATOR_QPARAM_NAME);
        Deque<String> _id = exchange.getQueryParameters().get(ID_IDX_QPARAM_NAME);
        Deque<String> _tranformer = exchange.getQueryParameters().get(TRANFORMER_QPARAM_NAME);
        Deque<String> _update = exchange.getQueryParameters().get(UPDATE_QPARAM_NAME);
        Deque<String> _upsert = exchange.getQueryParameters().get(UPSERT_QPARAM_NAME);

        this.props = exchange.getQueryParameters().get(PROP_KEYS_NAME);
        this.values = exchange.getQueryParameters().get(PROP_VALUES_NAME);

        db = _db != null ? _db.size() > 0 ? _db.getFirst() : null : null;
        coll = _coll != null ? _coll.size() > 0 ? _coll.getFirst() : null : null;

        if (db == null) {
            throw new IllegalArgumentException("db qparam is mandatory");
        }

        if (coll == null) {
            throw new IllegalArgumentException("db qparam is mandatory");
        }

        sep = _sep != null ? _sep.size() > 0 ? _sep.getFirst() : "" : ",";
        String _idIdx = _id != null ? _id.size() > 0 ? _id.getFirst() : "-1" : "-1";

        try {
            idIdx = Integer.parseInt(_idIdx);
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException(nfe);
        }

        String transformerName = _tranformer != null ? _tranformer.size() > 0 ? _tranformer.getFirst() : null : null;

        if (transformerName != null) {
            var _transformer = pluginsRegistry
                    .getTransformers()
                    .stream()
                    .filter(t -> transformerName.equals(t.getName()))
                    .findFirst();

            if (_transformer.isPresent()) {
                transformer = _transformer.get().getInstance();
            } else {
                transformer = null;
            }

        } else {
            transformer = null;
        }

        update = _update != null && (_update.isEmpty() || "true".equalsIgnoreCase(_update.getFirst()));

        upsert = _upsert == null
                || _update == null
                || _update.isEmpty()
                || "true".equalsIgnoreCase(_upsert.getFirst());
    }
}
