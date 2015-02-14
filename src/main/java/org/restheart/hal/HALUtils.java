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
package org.restheart.hal;

import org.restheart.handlers.IllegalQueryParamenterException;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.URLUtils;
import io.undertow.server.HttpServerExchange;
import java.util.TreeMap;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class HALUtils {
    /**
     *
     * @param exchange
     * @param context
     * @param size
     * @return
     * @throws IllegalQueryParamenterException
     */
    public static TreeMap<String, String> getPaginationLinks(HttpServerExchange exchange, RequestContext context, long size) throws IllegalQueryParamenterException {
        String requestPath = URLUtils.removeTrailingSlashes(exchange.getRequestPath());
        String queryString = URLUtils.decodeQueryString(exchange.getQueryString());

        int page = context.getPage();
        int pagesize = context.getPagesize();
        long totalPages = 0;

        if (size >= 0) {
            float _size = size + 0f;
            float _pagesize = pagesize + 0f;

            totalPages = Math.max(1, Math.round(Math.ceil(_size / _pagesize)));
        }

        TreeMap<String, String> links = new TreeMap<>();

        if (queryString == null || queryString.isEmpty()) {
            // i.e. the url contains the count paramenter and there is a next page
            if (totalPages > 0 && page < totalPages) {
                links.put("next", requestPath + "?page=" + (page + 1) + "&pagesize=" + pagesize);
            }
        } else {
            String queryStringNoPagingProps = URLUtils.decodeQueryString(URLUtils.getQueryStringRemovingParams(exchange, "page", "pagesize"));

            if (queryStringNoPagingProps == null || queryStringNoPagingProps.isEmpty()) {
                links.put("first", requestPath + "?pagesize=" + pagesize);
                links.put("next", requestPath + "?page=" + (page + 1) + "&pagesize=" + pagesize);

                // i.e. the url contains the count paramenter
                if (totalPages > 0) {
                    if (page < totalPages) {
                        links.put("last", requestPath + (totalPages != 1 ? "?page=" + totalPages : "") + "&pagesize=" + pagesize);
                        links.put("next", requestPath + "?page=" + (page + 1) + "&pagesize=" + pagesize + "&" + queryStringNoPagingProps);
                    } else {
                        links.put("last", requestPath + (totalPages != 1 ? "?page=" + totalPages : "") + "&pagesize=" + pagesize);
                    }
                }

                if (page > 1) {
                    links.put("previous", requestPath + (page >= 2 ? "?page=" + (page - 1) : "") + (page >= 2 ? "&pagesize=" + pagesize : "?pagesize=" + pagesize));
                }
            } else {
                links.put("first", requestPath + "?pagesize=" + pagesize + "&" + queryStringNoPagingProps);

                if (totalPages <= 0) {
                    links.put("next", requestPath + "?page=" + (page + 1) + "&pagesize=" + pagesize + "&" + queryStringNoPagingProps);
                }

                // i.e. the url contains the count paramenter
                if (totalPages > 0) {
                    if (page < totalPages) {
                        links.put("last", requestPath + (totalPages != 1 ? "?page=" + totalPages : "") + "&pagesize=" + pagesize + "&" + queryStringNoPagingProps);
                        links.put("next", requestPath + "?page=" + (page + 1) + "&pagesize=" + pagesize + "&" + queryStringNoPagingProps);
                    } else {
                        links.put("last", requestPath + (totalPages != 1 ? "?page=" + totalPages : "") + "&pagesize=" + pagesize + "&" + queryStringNoPagingProps);
                    }
                }

                if (page > 1) {
                    links.put("previous", requestPath + (page >= 2 ? "?page=" + (page - 1) : "") + (page >= 2 ? "&pagesize=" + pagesize : "?pagesize=" + pagesize) + "&" + queryStringNoPagingProps);
                }
            }
        }

        return links;
    }
}
