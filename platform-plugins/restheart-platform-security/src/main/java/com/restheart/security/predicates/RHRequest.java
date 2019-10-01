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
package com.restheart.security.predicates;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.undertow.server.HttpServerExchange;
import java.util.Deque;
import org.restheart.security.handlers.exchange.JsonRequest;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class RHRequest extends JsonRequest {

    public static final String RH_FILTER_QPARAM_KEY = "filter";

    private RHRequest(HttpServerExchange exchange) {
        super(exchange);
    }
    
    static public RHRequest wrap(HttpServerExchange exchange) {
        return new RHRequest(exchange);
    }

    /**
     *
     * @return the $and composed filter qparam values
     */
    public JsonObject getFiltersAsJson() throws BadRequestException {
        Deque<String> filter = getFilter();
        final JsonObject filterQuery;

        if (filter != null) {
            if (filter.size() > 1) {
                filterQuery = new JsonObject();
                JsonArray _filters = new JsonArray();

                filter.stream().forEach((String f) -> {
                    _filters.add(PARSER.parse(f));
                });

                filterQuery.add("$and", _filters);
            } else if (filter.size() == 1) {
                filterQuery = PARSER.parse(filter.getFirst()).getAsJsonObject();
            } else {
                filterQuery = new JsonObject();
            }
        } else {
            filterQuery = new JsonObject();
        }

        return filterQuery;
    }

    private Deque<String> getFilter() throws BadRequestException {
        // get filter parameter
        Deque<String> filters = getWrapped().getQueryParameters()
                .get(RH_FILTER_QPARAM_KEY);

        // check filter parameter
        if (filters != null) {
            StringBuffer error = new StringBuffer();

            if (!filters.stream().anyMatch(f -> {
                if (f == null || f.isEmpty()) {
                    error.append("illegal filter paramenter: ")
                            .append("empty");
                    return false;
                }

                try {
                    JsonElement _filter = PARSER.parse(f);

                    if (!_filter.isJsonObject()) {
                        error.append("illegal filter paramenter: ")
                                .append("it is not a json object: ")
                                .append(f)
                                .append(" => ")
                                .append(f.getClass().getSimpleName());
                        return false;
                    } else if (_filter.getAsJsonObject().keySet().isEmpty()) {
                        error.append("illegal filter paramenter: ")
                                .append("(empty json object)");
                        return false;
                    }

                } catch (Throwable t) {
                    error.append("illegal filter paramenter: ")
                            .append(f)
                            .append("; ")
                            .append(t.getLocalizedMessage());
                    return false;
                }

                return true;
            })) {
                throw new BadRequestException(error.toString());
            }
        }

        return filters;
    }
}
