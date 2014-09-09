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

package com.softinstigate.restheart.db;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.utils.ResponseHelper;
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
public class DBDAO
{
    private static final MongoClient client = MongoDBClientSingleton.getInstance().getClient();
    
    private static final Logger logger = LoggerFactory.getLogger(DBDAO.class);
    
    public static final BasicDBObject METADATA_QUERY = new BasicDBObject("_id", "@metadata");
    
    public static boolean doesDbExist(HttpServerExchange exchange, String dbName)
    {
        if (!client.getDatabaseNames().contains(dbName))
        {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_FOUND);
            return false;
        }
        
        return true;
    }
    
    public static DB getDB(String dbName)
    {
        return client.getDB(dbName);
    }
    
    public static List<String> getDbCollections(DB db)
    {
        List<String> colls = new ArrayList(db.getCollectionNames());
        
        Collections.sort(colls); // sort by id
        
        return colls;
    }
    
    /**
     * @param colls the collections list got from getDbCollections()
     * @return the number of collections in this db
    **/
    public static long getDBSize(List<String> colls)
    {
        return colls.size();
    }
    
    /**
     * @param dbName
     * @param colls the collections list as got from getDbCollections()
     * @return the db metadata
    **/
    public static Map<String, Object> getDbMetaData(String dbName, List<String> colls)
    {
        Map<String, Object> metadata = null;
        
        // get metadata collection if exists
        if (colls.contains("@metadata"))
        {
            DBCollection metadatacoll = CollectionDAO.getCollection(dbName, "@metadata");

            // filter out metadata document
            DBObject metadatarow = metadatacoll.findOne(METADATA_QUERY);
            
            metadata = DAOUtils.getDataFromRow(metadatarow, "_id");
        }
        
        
        return metadata;
    }
    
    /**
     * @param colls the collections list as got from getDbCollections()
     * @param page
     * @param pagesize
     * @param sortBy
     * @param filterBy
     * @param filter
     * @return the db data
    **/
    public static List<Map<String, Object>> getData(List<String> colls, int page, int pagesize, Deque<String> sortBy, Deque<String> filterBy, Deque<String> filter)
    {
        // filter out collection starting with @, e.g. @metadata collection
        List<String> _colls = colls.stream().filter(coll -> !coll.startsWith("@")).collect(Collectors.toList());
        
        // apply page and pagesize
        _colls = _colls.subList((page - 1) * pagesize, (page - 1) * pagesize + pagesize > colls.size() ? colls.size() : (page - 1) * pagesize + pagesize);

        // apply sort_by
        logger.debug("sort_by not yet implemented");

        // apply filter_by and filter
        logger.debug("filter not yet implemented");

        List<Map<String, Object>> data = new ArrayList<>();

        _colls.stream().map(
                (coll) ->
                {
                    TreeMap<String, Object> properties = new TreeMap<>();

                    properties.put("_id", coll);
                    return properties;
                }
        ).forEach((item) ->
        {
            data.add(item);
        });
        
        return data;
    }
}