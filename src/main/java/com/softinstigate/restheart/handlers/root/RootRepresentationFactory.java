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
package com.softinstigate.restheart.handlers.root;

import com.softinstigate.restheart.hal.*;
import com.mongodb.DBObject;
import static com.softinstigate.restheart.hal.Representation.HAL_JSON_MEDIA_TYPE;
import com.softinstigate.restheart.handlers.IllegalQueryParamenterException;
import com.softinstigate.restheart.handlers.RequestContext;
import com.softinstigate.restheart.utils.ResponseHelper;
import com.softinstigate.restheart.utils.URLUtilis;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import java.net.URISyntaxException;
import java.util.List;
import java.util.TreeMap;
import org.bson.types.ObjectId;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 *
 * @author uji
 */
public class RootRepresentationFactory
{
    private static final Logger logger = LoggerFactory.getLogger(RootRepresentationFactory.class);

    public static void sendHal(HttpServerExchange exchange, RequestContext context, List<DBObject> embeddedData, long size)
            throws IllegalQueryParamenterException, URISyntaxException
    {
        Representation rep = getDbs(exchange, context, embeddedData, size);

        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, HAL_JSON_MEDIA_TYPE);
        exchange.getResponseSender().send(rep.toString());
    }
    
    static private Representation getDbs(HttpServerExchange exchange, RequestContext context, List<DBObject> embeddedData, long size)
            throws IllegalQueryParamenterException
    {
        String requestPath = URLUtilis.removeTrailingSlashes(URLUtilis.getRequestPath(exchange));
        String queryString = (exchange.getQueryString() == null || exchange.getQueryString().isEmpty()) ? "" : "?" + exchange.getQueryString();
        
        boolean trailingSlash = requestPath.substring(requestPath.length()-1).equals("/");
        
        Representation rep = new Representation(requestPath + queryString);

        if (size > 0)
        {
            float _size = size + 0f;
            float _pagesize = context.getPagesize() + 0f;

            rep.addProperty("_size", size);
            rep.addProperty("_total_pages", Math.max(1, Math.round(Math.nextUp(_size / _pagesize))));
        }

        if (embeddedData != null)
        {
            long count = embeddedData.stream().filter((props) -> props.keySet().stream().anyMatch((k) -> k.equals("id") || k.equals("_id"))).count();

            rep.addProperty("_returned", "" + count);

            if (!embeddedData.isEmpty()) // embedded documents
            {
                embeddedData.stream().forEach((d) ->
                {
                    Object _id = d.get("_id");

                    if (_id != null && (_id instanceof String || _id instanceof ObjectId))
                    {
                        Representation nrep;
                        
                        if (trailingSlash)
                            nrep = new Representation(requestPath + _id.toString());
                        else
                            nrep = new Representation(requestPath + "/" + _id.toString());
                        
                        nrep.addProperties(d);
                        
                        rep.addRepresentation("rh:db", nrep);
                    }
                    else
                    {
                        logger.error("db missing string _id field", d);
                    }
                });
            }
        }
        
        // collection links
        TreeMap<String, String> links;

        links = HALUtils.getPaginationLinks(exchange, context, size);

        if (links != null)
        {
            links.keySet().stream().forEach((k) ->
            {
                rep.addLink(new Link(k, links.get(k)));
            });
        }
        
        //curies
        rep.addLink(new Link("rh:paging", requestPath + "{?page}{&pagesize}", true));
        rep.addLink(new Link("rh", "curies", "/_docs/{rel}.html", true), true);

        ResponseHelper.injectWarnings(rep, exchange, context);
        
        return rep;
    }
}