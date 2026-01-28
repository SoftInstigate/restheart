/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2026 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.exchange;

import java.io.IOException;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Scanner;

import org.bson.BsonArray;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.json.JsonParseException;
import org.bson.types.ObjectId;
import org.restheart.utils.BsonUtils;
import static org.restheart.utils.BsonUtils.document;
import org.restheart.utils.ChannelReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;

/**
 * ServiceRequest implementation that converts CSV data to BSON array format.
 * <p>
 * This class handles HTTP requests containing CSV data and converts them into
 * BSON arrays suitable for processing by MongoDB services. The conversion process
 * is controlled by query parameters that specify the CSV format and structure.
 * </p>
 * <p>
 * Two key query parameters control the conversion:
 * <ul>
 *   <li><strong>id</strong>: Specifies the column index (0-based) that should be used as the MongoDB _id field</li>
 *   <li><strong>sep</strong>: Defines the separator character used in the CSV (defaults to comma ",")</li>
 * </ul>
 * </p>
 * <p>
 * The first line of the CSV is treated as a header row containing column names.
 * Each subsequent row becomes a BSON document in the resulting array. The class
 * automatically handles JSON parsing for cell values, falling back to string
 * representation if JSON parsing fails.
 * </p>
 * <p>
 * Example usage with query parameters:
 * {@code POST /collection?id=0&sep=; }
 * This would use semicolon as separator and the first column as the _id field.
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class BsonFromCsvRequest extends ServiceRequest<BsonArray> {
    private static final Logger LOGGER = LoggerFactory.getLogger(BsonFromCsvRequest.class);

    /** The MIME content type for CSV data. */
    public static final String CVS_CONTENT_TYPE = "text/csv";

    /**
     * Constructs a new BsonFromCsvRequest wrapping the given HTTP exchange.
     * <p>
     * This constructor is protected and should only be called by factory methods
     * or subclasses. Use {@link #init(HttpServerExchange)} or {@link #of(HttpServerExchange)}
     * to create instances.
     * </p>
     *
     * @param exchange the HTTP server exchange to wrap
     */
    protected BsonFromCsvRequest(HttpServerExchange exchange) {
        super(exchange);
    }

    /**
     * Factory method to create and initialize a BsonFromCsvRequest.
     * <p>
     * This method creates a new instance and validates that the request has the
     * correct Content-Type header for CSV data. If the content type is invalid,
     * the request is marked as being in error state.
     * </p>
     *
     * @param exchange the HTTP server exchange containing the CSV request
     * @return a new BsonFromCsvRequest instance, potentially in error state if content type is invalid
     */
    public static BsonFromCsvRequest init(HttpServerExchange exchange) {
        var ret = new BsonFromCsvRequest(exchange);

        if (!checkContentType(exchange)) {
            LOGGER.warn("error initializing request, " + "Contenty-Type is not {}", CVS_CONTENT_TYPE);
            ret.setInError(true);
        }

        return ret;
    }

    /**
     * Factory method to create a BsonFromCsvRequest from an existing exchange.
     * <p>
     * This method retrieves an existing BsonFromCsvRequest instance that has been
     * previously attached to the exchange, or creates a new one if none exists.
     * </p>
     *
     * @param exchange the HTTP server exchange
     * @return the BsonFromCsvRequest associated with the exchange
     */
    public static BsonFromCsvRequest of(HttpServerExchange exchange) {
        return of(exchange, BsonFromCsvRequest.class);
    }

    /**
     * Parses the CSV content from the request body and converts it to a BsonArray.
     * <p>
     * This method reads the raw CSV data from the request body and converts it into
     * a BSON array where each CSV row (excluding the header) becomes a BSON document.
     * The conversion process uses the query parameters to determine the separator
     * character and which column should be used as the MongoDB _id field.
     * </p>
     * <p>
     * The parsing process:
     * <ol>
     *   <li>Extracts CSV parameters from query string (separator and id column index)</li>
     *   <li>Reads the raw CSV content from the request body</li>
     *   <li>Processes the CSV line by line, treating the first line as headers</li>
     *   <li>Converts each data row into a BSON document with appropriate field names</li>
     *   <li>Attempts to parse cell values as JSON, falling back to strings</li>
     * </ol>
     * </p>
     *
     * @return a BsonArray containing the converted CSV data as BSON documents
     * @throws IOException if there is an error reading the request body
     * @throws BadRequestException if the CSV data is malformed or cannot be processed
     */
    @Override
    public BsonArray parseContent() throws IOException, BadRequestException {
        final var params = new CsvRequestParams(wrapped);
        final var csv = ChannelReader.readString(wrapped);

        return parseCsv(params, csv);
    }

    /**
     * Validates that the request has the correct Content-Type header for CSV data.
     * <p>
     * This method checks if the Content-Type header is exactly "text/csv" or starts
     * with "text/csv;" (allowing for additional parameters like charset).
     * </p>
     *
     * @param exchange the HTTP server exchange to check
     * @return true if the Content-Type is valid for CSV data, false otherwise
     */
    private static boolean checkContentType(HttpServerExchange exchange) {
        HeaderValues contentType = exchange.getRequestHeaders().get(Headers.CONTENT_TYPE);

        return contentType != null && contentType.stream()
                .anyMatch(ct -> ct.equals(CVS_CONTENT_TYPE) || ct.startsWith(CVS_CONTENT_TYPE.concat(";")));
    }

    /**
     * Parses CSV string content into a BsonArray using the specified parameters.
     * <p>
     * This method processes the CSV content line by line, using the first line as
     * column headers and converting subsequent lines into BSON documents. Each
     * document gets an auto-generated _etag field and attempts to parse cell values
     * as JSON before falling back to string representation.
     * </p>
     *
     * @param params the CSV parsing parameters (separator and id column index)
     * @param csv the raw CSV content as a string
     * @return a BsonArray containing the parsed CSV data as BSON documents
     * @throws IOException if there is an error during parsing
     */
    private BsonArray parseCsv(CsvRequestParams params, String csv) throws IOException {
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
                    var doc = document().put("_etag", new ObjectId()).get();

                    int unnamedProps = 0;

                    for (int idx = 0; idx < vals.size(); idx++) {
                        if (idx == params.idIdx) {
                            var _v = vals.get(params.idIdx);

                            if (_v != null) {
                                // quote empty string
                                if ("".equals(_v.strip())) {
                                    _v = "\"".concat(_v).concat("\"");
                                }

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

                            // quote empty string
                            if ("".equals(_v.strip())) {
                                _v = "\"".concat(_v).concat("\"");
                            }

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

    /**
     * Converts a raw string value to an appropriate BsonValue.
     * <p>
     * This method first attempts to parse the string as JSON using BsonUtils.
     * If JSON parsing fails, it falls back to creating a BsonString with the
     * raw value. This allows CSV cells to contain JSON objects, arrays, numbers,
     * booleans, or plain text.
     * </p>
     *
     * @param raw the raw string value from the CSV cell
     * @return a BsonValue representing the parsed content, either as structured JSON or as a string
     */
    private BsonValue getBsonValue(String raw) {
        try {
            return BsonUtils.parse(raw);
        } catch (JsonParseException jpe) {
            return new BsonString(raw);
        }
    }

    /**
     * Internal class for holding CSV parsing parameters extracted from query parameters.
     * <p>
     * This class encapsulates the configuration options that control how CSV data
     * is parsed and converted to BSON format. The parameters are extracted from
     * the HTTP request's query string.
     * </p>
     */
    private static class CsvRequestParams {
        /** Query parameter name for specifying the ID column index. */
        private static final String ID_IDX_QPARAM_NAME = "id";
        
        /** Query parameter name for specifying the CSV separator character. */
        private static final String SEPARATOR_QPARAM_NAME = "sep";

        /** The 0-based index of the column to use as the MongoDB _id field, or -1 if none. */
        public final int idIdx;
        
        /** The character used to separate CSV fields (defaults to comma). */
        public final String sep;

        /**
         * Constructs CSV parameters by extracting values from the HTTP exchange.
         * <p>
         * Extracts the 'id' and 'sep' query parameters, applying defaults if they
         * are not specified. The 'id' parameter defaults to -1 (no id column) and
         * 'sep' defaults to comma.
         * </p>
         *
         * @param exchange the HTTP server exchange containing query parameters
         * @throws IllegalArgumentException if the 'id' parameter cannot be parsed as an integer
         */
        CsvRequestParams(HttpServerExchange exchange) {
            Deque<String> _sep = exchange.getQueryParameters().get(SEPARATOR_QPARAM_NAME);
            Deque<String> _id = exchange.getQueryParameters().get(ID_IDX_QPARAM_NAME);

            sep = _sep != null ? !_sep.isEmpty() ? _sep.getFirst() : "" : ",";
            String _idIdx = _id != null ? !_id.isEmpty() ? _id.getFirst() : "-1" : "-1";

            try {
                idIdx = Integer.parseInt(_idIdx);
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException(nfe);
            }
        }
    }
}
