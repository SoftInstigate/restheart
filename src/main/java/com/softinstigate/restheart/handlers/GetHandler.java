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

import com.mongodb.BasicDBList;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.softinstigate.restheart.db.MongoDBClientSingleton;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.utils.JSONHelper;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author uji
 */
public abstract class GetHandler implements HttpHandler
{
    protected static final MongoClient client = MongoDBClientSingleton.getInstance().getClient();

    protected static final Logger logger = LoggerFactory.getLogger(GetHandler.class);

    final Charset charset = Charset.forName("utf-8");

    /**
     * Creates a new instance of EntityResource
     */
    public GetHandler()
    {
    }

    /**
     * @returns the list of db references.
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception
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
                ResponseHelper.endExchangeWithError(exchange, HttpStatus.SC_BAD_REQUEST, nfwe);
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
                ResponseHelper.endExchangeWithError(exchange, HttpStatus.SC_BAD_REQUEST, nfwe);
                return;
            }
        }

        String content = generateContent(exchange, client, page, pagesize, sortBy, filterBy, filter);

        /**
         * TODO according to http specifications, Content-Type accepts one
         * single value however we specify two, to allow some browsers (i.e.
         * Safari) to display data rather than downloading it
         */
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json," + JSONHelper.HAL_JSON_MEDIA_TYPE);
        exchange.setResponseContentLength(content.length());
        exchange.setResponseCode(200);

        exchange.getResponseSender().send(content);

        exchange.endExchange();
    }

    protected abstract String generateContent(HttpServerExchange exchange, MongoClient client, int page, int pagesize, Deque<String> sortBy, Deque<String> filterBy, Deque<String> filter);

    /**
     * generic helper method for generating response body
     *
     * @param baseUrl
     * @param queryString
     * @param metadata
     * @param data
     * @param page
     * @param pagesize
     * @param size
     * @param sortBy
     * @param filterBy
     * @param filter
     * @return
     */
    protected String generateCollectionContent(String baseUrl, String queryString, Map<String, Object> metadata, List<Map<String, Object>> data, int page, int pagesize, long size, Deque<String> sortBy, Deque<String> filterBy, Deque<String> filter)
    {
        // *** arguments check

        float _size = size + 0f;
        float _pagesieze = pagesize + 0f;
        
        long total_pages = Math.max(1, Math.round(Math.nextUp(_size / _pagesieze)));
        
        if (pagesize < 1 || pagesize > 1000)
        {
            throw new IllegalArgumentException("illegal argument, pagesize must be > 0 and <= 1000");
        }

        if (page < 1)
        {
            throw new IllegalArgumentException("illegal argument, page must be > 0");
        }

        if (page > total_pages)
        {
            throw new IllegalArgumentException("illegal argument, page is bigger that total pages which is " + total_pages);
        }

        // *** data items
        
        List<Map<String, Object>> embedded = data.stream().filter((props) -> props.keySet().stream().anyMatch((k) -> k.equals("id") || k.equals("_id"))).collect(Collectors.toList());
        
        // *** links

        boolean includepagesize = pagesize != 100;

        TreeMap<String, URI> links = new TreeMap<>();
        
        try
        {
            if (queryString == null || queryString.isEmpty())
                links.put("self" , new URI(baseUrl));
            else
                links.put("self" , new URI(baseUrl + "?" + queryString));
            
            links.put("first" , new URI(baseUrl + (includepagesize ? "?pagesize=" + pagesize : "")));

            if (page < total_pages)
            {
                links.put("next", new URI(baseUrl + "?page=" + (page + 1) + (includepagesize ? "&pagesize=" + pagesize : "")));
                links.put("last", new URI(baseUrl + (total_pages != 1 ? "?page=" + total_pages : "") + (total_pages != 1 && includepagesize ? "&" : "?") + (includepagesize ? "pagesize=" + pagesize : "")));
            }
            else
            {
                links.put("last", new URI(baseUrl + (includepagesize ? "?pagesize=" + pagesize : "")));
            }

            if (page > 1)
            {
                links.put("previous", new URI(baseUrl + (page != 2 ? "?page=" + (page - 1) : "") + (page - 1 != 1 && includepagesize ? "&" : "?") + (includepagesize ? "pagesize=" + pagesize : "")));
            }
        }
        catch (URISyntaxException ex)
        {
            logger.error("error creating resource links", ex);
        }

        // *** queries
        logger.warn("queries not yet implemented");

        // *** templates
        logger.warn("templates not yet implemented");

        Map<String, Object> properties = new TreeMap<>();

        long count = data.stream().filter((props) -> props.keySet().stream().anyMatch((k) -> k.equals("id") || k.equals("_id"))).count();
        
        properties.put("returned", "" + count);
        properties.put("size", "" + size);
        properties.put("current_page", "" + page);
        properties.put("total_pages", "" + total_pages);
        
        if (metadata != null)
            properties.putAll(metadata);

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

        return JSONHelper.getCollectionHal(baseUrl, properties, links, embedded).toString();
    }

    protected String generateDocumentContent(String baseUrl, String queryString, Map<String, Object> data)
    {
        // *** links

        TreeMap<String, URI> links = new TreeMap<>();
        
        try
        {
            if (queryString == null || queryString.isEmpty())
                links.put("self" , new URI(baseUrl));
            else
                links.put("self" , new URI(baseUrl + "?" + queryString));
        }
        catch (URISyntaxException ex)
        {
            logger.error("error creating resource links", ex);
        }

        // *** queries
        logger.warn("queries not yet implemented");

        // *** templates
        logger.warn("templates not yet implemented");

        return JSONHelper.getDocumentHal(baseUrl, data, links).toString();
    }
    
    protected List<Map<String, Object>> getDataFromRows(ArrayList<DBObject> rows)
    {
        if (rows == null)
            return null;
        
        List<Map<String, Object>> data = new ArrayList<>();
        
        rows.stream().map((row) ->
        {
            TreeMap<String, Object> properties = getDataFromRow(row);

            return properties;
        }).forEach((item) ->
        {
            data.add(item);
        });
        
        return data;
    }
    
    /**
     * @param row
     * @param fieldsToFilter list of field names to filter
     * @return 
    */
    protected TreeMap<String, Object> getDataFromRow(DBObject row, String... fieldsToFilter)
    {
        if (row == null)
            return null;
        
        List<String> _fieldsToFilter = Arrays.asList(fieldsToFilter);
        
        
        TreeMap<String, Object> properties = new TreeMap<>();

        row.keySet().stream().forEach((key) ->
        {
            // data value is either a String or a Map. the former case applies with nested json objects

            if (!_fieldsToFilter.contains(key))
            {
                Object obj = row.get(key);

                if (obj instanceof BasicDBList)
                {
                    BasicDBList dblist = (BasicDBList) obj;

                    obj = dblist.toMap();
                }

                properties.put(key, obj);
            }
        });
        
        return properties;    
    }
}
