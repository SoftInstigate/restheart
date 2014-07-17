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
package com.softinstigate.restheart.handlers.account;

import com.mongodb.MongoClient;
import com.softinstigate.restheart.db.MongoDBClientSingleton;
import com.softinstigate.restheart.utils.JSONHelper;
import com.softinstigate.restheart.utils.RequestContext;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
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
import net.hamnaberg.json.ValueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author uji
 */
public class GetAccountHandler implements HttpHandler
{
    private static final MongoClient client = MongoDBClientSingleton.getInstance().getClient();
    
    private static final Logger logger = LoggerFactory.getLogger(GetAccountHandler.class);
    
    final Charset charset = Charset.forName("utf-8");  

    /**
     * Creates a new instance of EntityResource
     */
    public GetAccountHandler()
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
        RequestContext c = new RequestContext(exchange);

        int page = 1;
        int pagesize = 100;

        Deque<String> _page = exchange.getQueryParameters().get("page");
        Deque<String> _pagesize = exchange.getQueryParameters().get("pagesize");

        if (_page != null && !_page.isEmpty())
        {
            try
            {
                page = Integer.parseInt(_page.getFirst());
            }
            catch (NumberFormatException nfwe)
            {
                page = 1;
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
                pagesize = 100;
            }
        }

        String content = generateContent(exchange.getRequestURL(), exchange.getQueryString(), page, pagesize);

        /** TODO
         * according to http specifications, Content-Type accepts one single value
         * however we specify two, to allow some browsers (i.e. Safari) to display data rather than downloading it
         */
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json," + MediaType.COLLECTION_JSON);
        exchange.setResponseContentLength(content.length());
        exchange.setResponseCode(200);

        exchange.getResponseSender().send(content);
        
        exchange.endExchange();
    }

    /**
     * method for getting documents of collection coll
     *
     * @param page
     * @param pagesize default is 100
     * @return
     */
    private String generateContent(String baseUrl, String queryString, int page, int pagesize)
    {
        List<String> dbs = client.getDatabaseNames();
        
        Collections.sort(dbs);
        
        if (dbs == null)
            dbs = new ArrayList<>();

        int size = dbs.size();
        
        Collection.Builder cb = new Collection.Builder();
        
        // *** href collection property
        
        try
        {
            cb.withHref(new URI(baseUrl + queryString));
        }
        catch (URISyntaxException ex)
        {
            logger.error("error creating resource self href", ex);
        }
        
        // *** arguments check

        int total_pages = (size / pagesize) > 1 ? (size / pagesize) : 1;
        
        if (pagesize < 1)
        {
            cb.withError(Error.create("illegal argument", "IA-PAGESIZE", "pagesize must be > 0"));
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
        
        // apply page and pagesie
        dbs = dbs.subList((page-1)*pagesize, (page-1)*pagesize + pagesize > dbs.size() ? dbs.size() : (page-1)*pagesize + pagesize );
        
        // *** data items
        
        int count = 0;
        
        for (String db: dbs)
        {
            ArrayList<Property> props = new ArrayList<>();
            props.add(Property.value("name", Optional.fromNullable("db name"), db));

            cb.addItem(Item.create(JSONHelper.getReference(baseUrl, db), props));
            count++;
        }
        
        // *** links
        
        try
        {
            cb.addLink(Link.create(new URI(baseUrl + "?page=1&pagesize=" + pagesize), "first"));
            
            if (page < total_pages)
            {
                cb.addLink(Link.create(new URI(baseUrl + "?page=" + (page + 1) + "&pagesize=" + pagesize), "next"));
                cb.addLink(Link.create(new URI(baseUrl + "?page=" + total_pages + "&pagesize=" + pagesize), "last"));
            }
            else
                cb.addLink(Link.create(new URI(baseUrl + queryString), "last"));
            
            if (page > 1)
            {
                cb.addLink(Link.create(new URI(baseUrl + "?page=" + (page - 1) + "&pagesize=" + pagesize), "previous"));
            }
        }
        catch (URISyntaxException ex)
        {
            logger.error("error creating resource links", ex);
        }
        
        // *** queries
        
        
        // *** template
        

        // ***  metadata - NOTE: this is an extension to collection+json
        
        ArrayList<Property> props = new ArrayList<>();
 
        props.add(Property.value("returned", Optional.fromNullable("number of items returned"), count));
        props.add(Property.value("size", Optional.fromNullable("size of the collection"), size));
        props.add(Property.value("current_page", Optional.fromNullable("current page number"), page));
        props.add(Property.value("total_pages", Optional.fromNullable("number of pages"), total_pages));
        props.add(Property.value("sorted_by", Optional.fromNullable("property used to sort data items"), "name"));
 
        List<Value> users = new ArrayList<>();
        
        users.add(ValueFactory.createValue("uji"));
        users.add(ValueFactory.createValue("berni"));
        users.add(ValueFactory.createValue("manu"));
        
        props.add(Property.array("allowed_users", Optional.fromNullable("users allowed to access this resource"), users));
        
        return JSONHelper.addMetadataAndGetJson(cb.build(), props);
    }
}