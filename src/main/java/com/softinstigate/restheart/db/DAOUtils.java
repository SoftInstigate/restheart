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

import com.mongodb.BasicDBList;
import com.mongodb.DBObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 *
 * @author uji
 */
public class DAOUtils
{
    /**
     * @param rows list of DBObject rows as returned by getDataFromCursor()
     * @return 
    */
    public static List<Map<String, Object>> getDataFromRows(List<DBObject> rows)
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
     * @param row a DBObject row
     * @param fieldsToFilter list of field names to filter
     * @return 
    */
    public static TreeMap<String, Object> getDataFromRow(DBObject row, String... fieldsToFilter)
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