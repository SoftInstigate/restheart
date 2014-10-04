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
package com.softinstigate.restheart.handlers;

import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.utils.JSONHelper;
import com.softinstigate.restheart.utils.RequestContext;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author uji
 */
public abstract class GetHandler extends PipedHttpHandler
{
    protected static final Logger logger = LoggerFactory.getLogger(GetHandler.class);

    final Charset charset = Charset.forName("utf-8");

    /**
     * Creates a new instance of GetHandler
     *
     * @param next
     */
    public GetHandler(PipedHttpHandler next)
    {
        super(next);
    }

    /**
     * @param context
     * @returns the list of db references.
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception
    {
        int page = 1;
        int pagesize = 100;

        Deque<String> _page = exchange.getQueryParameters().get("page");
        Deque<String> _pagesize = exchange.getQueryParameters().get("pagesize");

        Deque<String> sortBy = exchange.getQueryParameters().get("sort_by");
        Deque<String> filterBy = exchange.getQueryParameters().get("filter_by");
        Deque<String> filter = exchange.getQueryParameters().get("filter");

        if (_page != null && !_page.isEmpty())
        {
            try
            {
                page = Integer.parseInt(_page.getFirst());
            }
            catch (NumberFormatException nfwe)
            {
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_BAD_REQUEST, "the page query paramenter is not a number: " + _page, null);
                return;
            }
        }

        if (_pagesize != null && !_pagesize.isEmpty())
        {
            try
            {
                pagesize = Integer.parseInt(_pagesize.getFirst());
            }
            catch (NumberFormatException nfwe)
            {
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_BAD_REQUEST, "the pagesize query paramenter is not a number: " + _pagesize, null);
                return;
            }
        }

        String content = generateContent(exchange, context, page, pagesize, sortBy, filterBy, filter);

        if (content == null) // null if doc not exists. exchange already closed by generateContent
        {
            return;
        }

        /**
         * TODO according to http specifications, Content-Type accepts one
         * single value however we specify two to allow some browsers (i.e.
         * Safari) to display data rather than downloading it
         */
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json," + JSONHelper.HAL_JSON_MEDIA_TYPE);
        exchange.setResponseContentLength(content.length());
        exchange.setResponseCode(200);

        exchange.getResponseSender().send(content);

        exchange.endExchange();
    }

    protected abstract String generateContent(HttpServerExchange exchange, RequestContext context, int page, int pagesize, Deque<String> sortBy, Deque<String> filterBy, Deque<String> filter);

    /**
     * generic helper method for generating response body
     *
     * @param exchange
     * @param metadata
     * @param data
     * @param page
     * @param pagesize
     * @param size if < 0, don't include size in metadata @param sortBy @param f
     * ilterBy @param filter @return @param sortBy @param filterBy @param filter
     * @param sortBy
     * @param filterBy
     * @param filter
     * @return
     * @throws com.softinstigate.restheart.handlers.IllegalQueryParamenterException
     */
    protected String generateCollectionContent(HttpServerExchange exchange, Map<String, Object> metadata, List<Map<String, Object>> data, int page, int pagesize, long size, Deque<String> sortBy, Deque<String> filterBy, Deque<String> filter)
            throws IllegalQueryParamenterException
    {
        String baseUrl = exchange.getRequestURL();
        String queryString = exchange.getQueryString();

        // *** arguments check
        long total_pages = -1;

        if (size > 0)
        {
            float _size = size + 0f;
            float _pagesize = pagesize + 0f;

            total_pages = Math.max(1, Math.round(Math.nextUp(_size / _pagesize)));

            if (page > total_pages)
            {
                throw new IllegalQueryParamenterException("illegal query paramenter, page is bigger that total pages which is " + total_pages);
            }
        }

        if (pagesize < 1 || pagesize > 1000)
        {
            throw new IllegalQueryParamenterException("illegal argument, pagesize must be >= 0 and <= 1000");
        }

        if (page < 1)
        {
            throw new IllegalQueryParamenterException("illegal argument, page must be > 0");
        }

        // *** data items
        List<Map<String, Object>> embedded = data.stream().filter((props) -> props.keySet().stream().anyMatch((k) -> k.equals("id") || k.equals("_id"))).collect(Collectors.toList());

        // *** links
        
        TreeMap<String, URI> links = getLinks(baseUrl, queryString, page, pagesize, total_pages);
        
        // *** queries
        logger.debug("queries not yet implemented");

        // *** templates
        logger.debug("templates not yet implemented");

        Map<String, Object> properties = new TreeMap<>();

        long count = data.stream().filter((props) -> props.keySet().stream().anyMatch((k) -> k.equals("id") || k.equals("_id"))).count();

        properties.put("@returned", "" + count);
        
        if (size >= 0)
        {
            properties.put("@size", "" + size);
        }
        
        if (size > 0)
        {
            properties.put("@total_pages", "" + total_pages);
        }

        properties.put("@current_page", "" + page);

        if (metadata != null)
        {
            properties.putAll(metadata);
        }

        if (sortBy != null && !sortBy.isEmpty())
        {
            properties.put("sort_by", "" + sortBy);
        }

        if (filterBy != null && !filterBy.isEmpty())
        {
            properties.put("filter_by", "" + filterBy);
        }

        if (filter != null && !filter.isEmpty())
        {
            properties.put("filter", "" + filter);
        }

        // inject etag header from metadata
        ResponseHelper.injectEtagHeader(exchange, metadata);

        return JSONHelper.getCollectionHal(baseUrl, properties, links, embedded).toString();
    }

    private String removePagingParamsFromQueryString(String queryString)
    {
        if (queryString == null)
        {
            return null;
        }

        String ret = queryString;
        ret = ret.replaceAll("page=.?$", "");
        ret = ret.replaceAll("pagesize=.?$", "");

        ret = ret.replaceAll("page=.?&", "");
        ret = ret.replaceAll("pagesize=.?&", "");

        return ret;
    }

    protected String generateDocumentContent(HttpServerExchange exchange, Map<String, Object> data)
    {
        String baseUrl = exchange.getRequestURL();
        String queryString = exchange.getQueryString();

        // *** links
        TreeMap<String, URI> links = new TreeMap<>();

        try
        {
            if (queryString == null || queryString.isEmpty())
            {
                links.put("self", new URI(baseUrl));
            }
            else
            {
                links.put("self", new URI(baseUrl + "?" + queryString));
            }
        }
        catch (URISyntaxException ex)
        {
            logger.error("error creating resource links", ex);
        }

        // *** queries
        logger.debug("queries not yet implemented");

        // *** templates
        logger.debug("templates not yet implemented");

        // inject etag header from metadata
        ResponseHelper.injectEtagHeader(exchange, data);

        return JSONHelper.getDocumentHal(baseUrl, data, links).toString();
    }

    private TreeMap<String, URI> getLinks(String baseUrl, String queryString, int page, int pagesize, long total_pages)
    {
        TreeMap<String, URI> links = new TreeMap<>();

        try
        {
            if (queryString == null || queryString.isEmpty())
            {
                links.put("self", new URI(baseUrl));
                links.put("next", new URI(baseUrl + "?page=" + (page + 1) + "&pagesize=" + pagesize));
            }
            else
            {
                links.put("self", new URI(baseUrl + "?" + queryString));

                String queryString2 = removePagingParamsFromQueryString(queryString);

                if (queryString2 == null || queryString2.isEmpty())
                {
                    links.put("first", new URI(baseUrl + "?pagesize=" + pagesize));
                    links.put("next", new URI(baseUrl + "?page=" + (page + 1) + "&pagesize=" + pagesize));

                    if (total_pages > 0) // i.e. the url contains the count paramenter
                    {
                        if (page < total_pages)
                        {
                            links.put("last", new URI(baseUrl + (total_pages != 1 ? "?page=" + total_pages : "") + "&pagesize=" + pagesize));
                            links.put("next", new URI(baseUrl + "?page=" + (page + 1) + "&pagesize=" + pagesize + "&" + queryString2));
                        }
                        else
                        {
                            links.put("last", new URI(baseUrl + (total_pages != 1 ? "?page=" + total_pages : "") + "&pagesize=" + pagesize));
                        }
                    }

                    if (page > 1)
                    {
                        links.put("previous", new URI(baseUrl + (page >= 2 ? "?page=" + (page - 1) : "") + (page > 2 ? "&pagesize=" + pagesize : "?pagesize=" + pagesize)));
                    }
                }
                else
                {
                    links.put("first", new URI(baseUrl + "?pagesize=" + pagesize + "&" + queryString2));

                    if (total_pages <= 0)
                    {
                        links.put("next", new URI(baseUrl + "?page=" + (page + 1) + "&pagesize=" + pagesize + "&" + queryString2));
                    }

                    if (total_pages > 0) // i.e. the url contains the count paramenter
                    {
                        if (page < total_pages)
                        {
                            links.put("last", new URI(baseUrl + (total_pages != 1 ? "?page=" + total_pages : "") + "&pagesize=" + pagesize + "&" + queryString2));
                            links.put("next", new URI(baseUrl + "?page=" + (page + 1) + "&pagesize=" + pagesize + "&" + queryString2));
                        }
                        else
                        {
                            links.put("last", new URI(baseUrl + (total_pages != 1 ? "?page=" + total_pages : "") + "&pagesize=" + pagesize + "&" + queryString2));
                        }
                    }

                    if (page > 1)
                    {
                        links.put("previous", new URI(baseUrl + (page >= 2 ? "?page=" + (page - 1) : "") + (page > 2 ? "&pagesize=" + pagesize : "?pagesize=" + pagesize) + "&" + queryString2));
                    }
                }
            }
        }
        catch (URISyntaxException ex)
        {
            logger.error("error creating resource links", ex);
        }
        
        return links;
    }
}
