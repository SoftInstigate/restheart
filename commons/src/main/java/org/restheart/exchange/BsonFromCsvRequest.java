/*
 * Copyright SoftInstigate srl. All Rights Reserved.
 *
 *
 * The copyright to the computer program(s) herein is the property of
 * SoftInstigate srl, Italy. The program(s) may be used and/or copied only
 * with the written permission of SoftInstigate srl or in accordance with the
 * terms and conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied. This copyright notice must not be removed.
 */
package org.restheart.exchange;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import java.io.IOException;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonNull;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.json.JsonParseException;
import static org.restheart.exchange.Exchange.LOGGER;
import static org.restheart.exchange.ServiceRequest.of;
import org.restheart.utils.ChannelReader;
import org.restheart.utils.JsonUtils;

/**
 * ServiceRequest implementation backed by BsonValue and initialized from csv
 * data. Two query parameters controls the conversion: 'id', the the index of
 * the _id property and 'sep', the separator char
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class BsonFromCsvRequest extends ServiceRequest<BsonArray> {
    /**
     *
     */
    public static final String CVS_CONTENT_TYPE = "text/csv";

    private BsonFromCsvRequest(HttpServerExchange exchange) {
        super(exchange);
    }

    public static BsonFromCsvRequest init(HttpServerExchange exchange) {
        var ret = new BsonFromCsvRequest(exchange);

        if (checkContentType(exchange)) {
            try {
                ret.injectContent(exchange);
            } catch (IOException ex) {
                LOGGER.warn("error parsing CSV", ex);
                ret.setInError(true);
            } catch (Throwable ieo) {
                LOGGER.warn("error initializing request", ieo);
                ret.setInError(true);
            }
        } else {
            LOGGER.warn("error initializing request, "
                    + "Contenty-Type is not {}", CVS_CONTENT_TYPE);
            ret.setInError(true);
        }

        return ret;
    }

    public static BsonFromCsvRequest of(HttpServerExchange exchange) {
        return of(exchange, BsonFromCsvRequest.class);
    }

    public void injectContent(HttpServerExchange exchange) throws IOException {
        final var params = new CsvRequestParams(exchange);
        final var csv = ChannelReader.read(exchange.getRequestChannel());

        setContent(parseCsv(params, csv));
    }

    private static boolean checkContentType(HttpServerExchange exchange) {
        HeaderValues contentType = exchange.getRequestHeaders()
                .get(Headers.CONTENT_TYPE);

        return contentType != null
                && contentType.stream()
                        .anyMatch(ct
                                -> ct.equals(CVS_CONTENT_TYPE)
                        || ct.startsWith(CVS_CONTENT_TYPE.concat(";")));
    }

    private BsonArray parseCsv(CsvRequestParams params, String csv)
            throws IOException {
        var bson = new BsonArray();

        boolean isHeader = true;

        List<String> cols = null;

        try (Scanner scanner = new Scanner(csv)) {
            while (scanner.hasNext()) {
                String line = scanner.nextLine();

                // split on the separator only if that comma has zero,
                // or an even number of quotes ahead of it.
                List<String> vals = Arrays.asList(line.split(params.sep + "(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1));

                if (isHeader) {
                    cols = vals;
                } else {
                    var doc = new BsonDocument("_etag", new BsonObjectId());

                    int unnamedProps = 0;

                    for (int idx = 0; idx < vals.size(); idx++) {
                        if (idx == params.idIdx) {
                            var _v = vals.get(params.idIdx);
                            
                            if (_v != null) {
                                doc.append("_id", getBsonValue(_v));
                            }
                        } else {
                            String propname;

                            if (cols == null || cols.size() <= idx) {
                                propname = "unnamed_" + unnamedProps;
                                unnamedProps++;
                            } else {
                                propname = cols.get(idx);
                            }
                            
                            var _v = vals.get(idx);
                            
                            if (_v != null) {
                                 doc.append(propname, getBsonValue(_v));
                            }
                        }
                    }

                    bson.add(doc);
                }

                isHeader = false;
            }
        }

        return bson;
    }

    private BsonValue getBsonValue(String raw) {
        try {
            return JsonUtils.parse(raw);
        } catch (JsonParseException jpe) {
            return new BsonString(raw);
        }
    }

    private static class CsvRequestParams {
        private static final String ID_IDX_QPARAM_NAME = "id";
        private static final String SEPARATOR_QPARAM_NAME = "sep";

        public final int idIdx;
        public final String sep;

        CsvRequestParams(HttpServerExchange exchange) {
            Deque<String> _sep = exchange.getQueryParameters().get(SEPARATOR_QPARAM_NAME);
            Deque<String> _id = exchange.getQueryParameters().get(ID_IDX_QPARAM_NAME);

            sep = _sep != null ? _sep.size() > 0 ? _sep.getFirst() : "" : ",";
            String _idIdx = _id != null ? _id.size() > 0 ? _id.getFirst() : "-1" : "-1";

            try {
                idIdx = Integer.parseInt(_idIdx);
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException(nfe);
            }
        }
    }
}
