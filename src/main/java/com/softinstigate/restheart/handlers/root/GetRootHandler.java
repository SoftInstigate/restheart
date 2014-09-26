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

import com.mongodb.MongoClient;
import com.softinstigate.restheart.db.MongoDBClientSingleton;
import com.softinstigate.restheart.handlers.GetHandler;
import com.softinstigate.restheart.utils.RequestContext;
import io.undertow.server.HttpServerExchange;
import java.util.ArrayList;
import java.util.Collections;
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
public class GetRootHandler extends GetHandler
{
    private static final MongoClient client = MongoDBClientSingleton.getInstance().getClient();
    
    private static final Logger logger = LoggerFactory.getLogger(GetRootHandler.class);

    /**
     * Creates a new instance of EntityResource
     */
    public GetRootHandler()
    {
        super(null);
    }

    @Override
    protected String generateContent(HttpServerExchange exchange, RequestContext context, int page, int pagesize, Deque<String> sortBy, Deque<String> filterBy, Deque<String> filter)
    {
        List<String> _dbs = client.getDatabaseNames();

        // filter out reserved resourced
        List<String> dbs = _dbs.stream().filter(db -> ! RequestContext.isReservedResourceDb(db)).collect(Collectors.toList());
        
        if (dbs == null)
        {
            dbs = new ArrayList<>();
        }

        int size = dbs.size();

        Collections.sort(dbs); // sort by id

        // apply page and pagesize
        dbs = dbs.subList((page - 1) * pagesize, (page - 1) * pagesize + pagesize > dbs.size() ? dbs.size() : (page - 1) * pagesize + pagesize);

        // apply sort_by
        logger.debug("sort_by not yet implemented");

        // apply filter_by and filter
        logger.debug("filter not yet implemented");
        
        List<Map<String, Object>> data = new ArrayList<>();

        dbs.stream().map(
                (db) ->
                {
                    TreeMap<String, Object> properties = new TreeMap<>();

                    properties.put("_id", db);
                    return properties;
                }
        ).forEach((item) -> { data.add(item); });

        return generateCollectionContent(exchange, null, data, page, pagesize, size, sortBy, filterBy, filter);
    }
}