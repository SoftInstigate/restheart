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
package org.restheart.handlers.applicationlogic;

import com.mongodb.client.MongoCollection;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.json.JsonParseException;
import org.restheart.db.MongoDBClientSingleton;
import org.restheart.hal.Representation;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import static org.restheart.security.handlers.IAuthToken.AUTH_TOKEN_HEADER;
import static org.restheart.security.handlers.IAuthToken.AUTH_TOKEN_LOCATION_HEADER;
import static org.restheart.security.handlers.IAuthToken.AUTH_TOKEN_VALID_HEADER;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class CsvLoaderHandler extends ApplicationLogicHandler {
    public static final String CVS_CONTENT_TYPE = "text/csv";

    private static final Logger LOGGER
            = LoggerFactory.getLogger(CsvLoaderHandler.class);

    private static final String ID_IDX_QPARAM_NAME = "id";
    private static final String SEPARATOR_QPARAM_NAME = "sep";
    private static final String DB_QPARAM_NAME = "db";
    private static final String COLL_QPARAM_NAME = "coll";
    private static final String PROP_KEYS_NAME = "props";
    private static final String PROP_VALUES_NAME = "values";

    private int idIdx = -1;
    private String db;
    private String coll;
    private String sep = ",";

    private Deque<String> props = null;
    private Deque<String> values = null;

    private static final BsonString ERROR_QPARAM = new BsonString(
            "query parameters: "
            + "db=<db_name> *required, "
            + "coll=<collection_name> *required, "
            + "id=<id_column_index> optional (default: no _id column, each row will get an new ObjectId), "
            + "sep=<column_separator> optional (default: ,), "
            + "props=<props> optional (default: no props) additional props to add to each row, "
            + "values=<values> optional (default: no values) values of additional props to add to each row");

    private static final BsonString ERROR_CONTENT_TYPE = new BsonString(
            "Content-Type request header must be 'text/csv'");

    private static final BsonString ERROR_WRONG_METHOD = new BsonString(
            "Only POST method is supported");

    private static final BsonString ERROR_PARSING_DATA = new BsonString(
            "Error parsing CSV, see logs for more information");

    /**
     * Creates a new instance of CsvLoaderHandler
     *
     * @param next
     * @param args
     * @throws Exception
     */
    public CsvLoaderHandler(PipedHttpHandler next, Map<String, Object> args) {
        super(next, args);
    }

    @Override
    public void handleRequest(
            HttpServerExchange exchange, RequestContext context)
            throws Exception {
        if (context.isOptions()) {
            exchange.getResponseHeaders()
                    .put(HttpString
                            .tryFromString("Access-Control-Allow-Methods"),
                            "POST");
            exchange.getResponseHeaders()
                    .put(HttpString
                            .tryFromString("Access-Control-Allow-Headers"),
                            "Accept, Accept-Encoding, Authorization, "
                            + "Content-Length, Content-Type, Host, Origin, "
                            + "X-Requested-With, User-Agent, "
                            + "No-Auth-Challenge, "
                            + AUTH_TOKEN_HEADER + ", "
                            + AUTH_TOKEN_VALID_HEADER + ", "
                            + AUTH_TOKEN_LOCATION_HEADER);
            exchange.setStatusCode(HttpStatus.SC_OK);
            exchange.endExchange();
        } else {
            exchange.getResponseHeaders().put(
                    Headers.CONTENT_TYPE, Representation.JSON_MEDIA_TYPE);
            if (doesApply(context)) {
                if (checkContentType(exchange)) {
                    if (checkQueryParameters(exchange)) {
                        try {
                            List<BsonDocument> documents = parseCsv(context.getRawContent());

                            if (documents != null && documents.size() > 0) {
                                MongoCollection<BsonDocument> mcoll = MongoDBClientSingleton
                                        .getInstance()
                                        .getClient()
                                        .getDatabase(db)
                                        .getCollection(coll, BsonDocument.class);

                                mcoll.insertMany(documents);
                                exchange.setStatusCode(HttpStatus.SC_OK);
                            } else {
                                exchange.setStatusCode(HttpStatus.SC_NOT_MODIFIED);
                            }
                        } catch (IOException ex) {
                            LOGGER.error("error parsing CSV data", ex);
                            exchange.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                            exchange.getResponseSender()
                                    .send(getError(
                                            HttpStatus.SC_INTERNAL_SERVER_ERROR,
                                            ERROR_PARSING_DATA));
                        }
                    } else {
                        exchange.setStatusCode(HttpStatus.SC_BAD_REQUEST);
                        exchange.getResponseSender()
                                .send(getError(
                                        HttpStatus.SC_BAD_REQUEST,
                                        ERROR_QPARAM));
                    }
                } else {
                    exchange.setStatusCode(HttpStatus.SC_BAD_REQUEST);
                    exchange.getResponseSender()
                            .send(getError(
                                    HttpStatus.SC_BAD_REQUEST,
                                    ERROR_CONTENT_TYPE));
                }

            } else {
                exchange.getResponseSender()
                        .send(getError(
                                HttpStatus.SC_NOT_IMPLEMENTED,
                                ERROR_WRONG_METHOD));

                exchange.setStatusCode(HttpStatus.SC_NOT_IMPLEMENTED);
            }

            exchange.endExchange();
        }
    }

    private String getError(int code, BsonString message) {
        BsonDocument error = new BsonDocument();
        error.put("http status code",
                new BsonInt32(code));
        error.put("http status description",
                new BsonString(HttpStatus.getStatusText(code)));

        if (message != null) {
            error.put(
                    "message",
                    message);
        }

        return JsonUtils.toJson(error);
    }

    private List<BsonDocument> parseCsv(String rawContent)
            throws IOException {
        List<BsonDocument> ret = new ArrayList<BsonDocument>();

        Scanner scanner = new Scanner(rawContent);

        boolean isHeader = true;

        List<String> cols = null;

        while (scanner.hasNext()) {
            String line = scanner.nextLine();

            // split on the separator only if that comma has zero, 
            // or an even number of quotes ahead of it.
            List<String> vals = Arrays.asList(line.
                    split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1));

            if (isHeader) {
                cols = vals;
            } else {
                BsonDocument doc = new BsonDocument();

                int unnamedProps = 0;

                for (int idx = 0; idx < vals.size(); idx++) {
                    if (idx == idIdx) {
                        doc.append("_id", getBsonValue(vals.get(idIdx)));
                    } else {
                        String propname;

                        if (cols.size() <= idx) {
                            propname = "unnamed_" + unnamedProps;
                            unnamedProps++;
                        } else {
                            propname = cols.get(idx);
                        }

                        doc.append(propname, getBsonValue(vals.get(idx)));
                    }
                }

                // add props specified via keys and values qparams
                addProps(doc);

                ret.add(doc);
            }

            isHeader = false;
        }

        return ret;
    }

    private void addProps(BsonDocument doc) {
        if (props != null && values != null) {
            Deque<String> _props = new ArrayDeque(props);
            Deque<String> _values = new ArrayDeque(values);

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

    private boolean checkQueryParameters(HttpServerExchange exchange) {
        Deque<String> _db = exchange.getQueryParameters().get(DB_QPARAM_NAME);
        Deque<String> _coll = exchange.getQueryParameters().get(COLL_QPARAM_NAME);
        Deque<String> _sep = exchange.getQueryParameters().get(SEPARATOR_QPARAM_NAME);
        Deque<String> _id = exchange.getQueryParameters().get(ID_IDX_QPARAM_NAME);

        this.props = exchange.getQueryParameters().get(PROP_KEYS_NAME);
        this.values = exchange.getQueryParameters().get(PROP_VALUES_NAME);

        db = _db != null ? _db.size() > 0 ? _db.getFirst() : null : null;
        coll = _coll != null ? _coll.size() > 0 ? _coll.getFirst() : null : null;
        sep = _sep != null ? _sep.size() > 0 ? _sep.getFirst() : "," : ",";
        String _idIdx = _id != null ? _id.size() > 0 ? _id.getFirst() : "-1" : "-1";

        try {
            idIdx = Integer.parseInt(_idIdx);
        } catch (NumberFormatException nfe) {
            return false;
        }

        return db != null && coll != null;
    }

    private boolean doesApply(RequestContext context) {
        return context.isPost();
    }

    private boolean checkContentType(HttpServerExchange exchange) {
        HeaderValues contentType = exchange
                .getRequestHeaders()
                .get(Headers.CONTENT_TYPE);

        return contentType != null && contentType.contains(CVS_CONTENT_TYPE);
    }
}
