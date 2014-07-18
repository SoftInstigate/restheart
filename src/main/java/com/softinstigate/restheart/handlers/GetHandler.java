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
import java.util.Deque;
import java.util.List;
import net.hamnaberg.funclite.Optional;
import net.hamnaberg.json.Collection;
import net.hamnaberg.json.Item;
import net.hamnaberg.json.Link;
import net.hamnaberg.json.Property;
import net.hamnaberg.json.Error;
import net.hamnaberg.json.MediaType;
import net.hamnaberg.json.Value;
import net.hamnaberg.json.extension.Tuple3;
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
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json," + MediaType.COLLECTION_JSON);
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
     * @param data
     * @param page
     * @param pagesize
     * @param size
     * @param sortBy
     * @param filterBy
     * @param filter
     * @return
     */
    protected String generateCollectionContent(String baseUrl, String queryString, List<List<Tuple3<String, String, Object>>> data, int page, int pagesize, long size, Deque<String> sortBy, Deque<String> filterBy, Deque<String> filter)
    {
        Collection.Builder cb = new Collection.Builder();

        // *** href collection property
        try
        {
            if (queryString == null || queryString.isEmpty())
            {
                cb.withHref(new URI(baseUrl));
            }
            else
            {
                cb.withHref(new URI(baseUrl + "?" + queryString));
            }
        }
        catch (URISyntaxException ex)
        {
            logger.error("error creating resource self href", ex);
        }

        // *** arguments check
        long total_pages = (size / pagesize) > 1 ? (size / pagesize) : 1;

        if (pagesize < 1 || pagesize > 1000)
        {
            cb.withError(Error.create("illegal argument", "IA-PAGESIZE", "pagesize must be > 0 and <= 1000"));
            return cb.build().toString();
        }

        if (page < 1)
        {
            cb.withError(Error.create("illegal argument", "IA-PAGE", "page cannot be less than 1"));
            return cb.build().toString();
        }

        if (page > total_pages)
        {
            cb.withError(Error.create("illegal argument", "IA-PAGE", "page is bigger that total pages which is " + total_pages));
            return cb.build().toString();
        }

        // *** data items
        long count = data.stream().filter((item) -> item.stream().anyMatch((property) -> property._1.equals("id") || property._1.equals("_id"))).count();

        data.stream().filter((item) -> item.stream().anyMatch((property) -> property._1.equals("id") || property._1.equals("_id"))).forEach(
                (item) ->
                {
                    ArrayList<Property> props = new ArrayList<>();

                    item.stream().forEach((property) ->
                            {
                                //TODO if value (property._3) is a json object (instanceof DBObject) it gets quoted
                                // need to add nested document somehow - check specification
                                props.add(Property.value(property._1, Optional.fromNullable(property._2), property._3));
                    });

                    Object id = item.stream().filter((property) -> property._1.equals("id") || property._1.equals("_id")).findFirst().get()._3;

                    if (id != null)
                    {
                        cb.addItem(Item.create(JSONHelper.getReference(baseUrl, id.toString()), props));
                    }
                });

        // *** links
        boolean includepagesize = pagesize != 100;

        try
        {
            cb.addLink(Link.create(new URI(baseUrl + (includepagesize ? "?pagesize=" + pagesize : "")), "first"));

            if (page < total_pages)
            {
                cb.addLink(Link.create(new URI(baseUrl + "?page=" + (page + 1) + (includepagesize ? "&pagesize=" + pagesize : "")), "next"));
                cb.addLink(Link.create(new URI(baseUrl + (total_pages != 1 ? "?page=" + total_pages : "") + (total_pages != 1 && includepagesize ? "&" : "?") + (includepagesize ? "pagesize=" + pagesize : "")), "last"));
            }
            else
            {
                cb.addLink(Link.create(new URI(baseUrl + (includepagesize ? "?pagesize=" + pagesize : "")), "last"));
            }

            if (page > 1)
            {
                cb.addLink(Link.create(new URI(baseUrl + (page != 2 ? "?page=" + (page - 1) : "") + (page - 1 != 1 && includepagesize ? "&" : "?") + (includepagesize ? "pagesize=" + pagesize : "")), "previous"));
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

        // ***  metadata - NOTE: this is an extension to collection+json
        ArrayList<Property> props = new ArrayList<>();

        props.add(Property.value("returned", Optional.fromNullable("number of items returned"), count));
        props.add(Property.value("size", Optional.fromNullable("size of the collection"), size));
        props.add(Property.value("current_page", Optional.fromNullable("current page number"), page));
        props.add(Property.value("total_pages", Optional.fromNullable("number of pages"), total_pages));

        if (sortBy != null && !sortBy.isEmpty())
        {
            props.add(Property.value("sort_by", Optional.fromNullable("properties used to sort data items, comma separated"), sortBy));
        }

        if (filterBy != null && !filterBy.isEmpty())
        {
            props.add(Property.value("filter_by", Optional.fromNullable("properties used to filter data items, comma separated"), filterBy));
        }

        if (filter != null && !filter.isEmpty())
        {
            props.add(Property.value("filter", Optional.fromNullable("filters to apply, comma separated regexs"), filter));
        }

        return JSONHelper.addMetadataAndGetJson(cb.build(), props);
    }

    protected String generateDocumentContent(String baseUrl, String queryString, List<Tuple3<String, String, Object>> data)
    {
        Collection.Builder cb = new Collection.Builder();

        // *** href document property
        try
        {
            if (queryString == null || queryString.isEmpty())
            {
                cb.withHref(new URI(baseUrl));
            }
            else
            {
                cb.withHref(new URI(baseUrl + "?" + queryString));
            }
        }
        catch (URISyntaxException ex)
        {
            logger.error("error creating resource self href", ex);
        }

        // *** data item
        ArrayList<Property> props = new ArrayList<>();

        String id = null;

        for (Tuple3<String, String, Object> property : data)
        {
            if (property._1.equals("id") || property._1.equals("_id"))
            {
                id = (String) property._3;
            }

            props.add(Property.value(property._1, Optional.fromNullable(property._2), property._3));
        }

        if (id != null)
        {
            cb.addItem(Item.create(JSONHelper.getReference(baseUrl, id), props));
        }

        // *** templates
        logger.warn("templates not yet implemented");

        return cb.build().toString();
    }
}
