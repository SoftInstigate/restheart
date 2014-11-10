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
import com.mongodb.BasicDBObject;
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
public class DAOUtils {
    /**
     * @param rows list of DBObject rows as returned by getDataFromCursor()
     * @return
     */
    public static List<Map<String, Object>> getDataFromRows(List<DBObject> rows) {
        if (rows == null) {
            return null;
        }

        List<Map<String, Object>> data = new ArrayList<>();

        rows.stream().map((row) -> {
            TreeMap<String, Object> properties = getDataFromRow(row);

            return properties;
        }).forEach((item) -> {
            data.add(item);
        });

        return data;
    }

    /**
     * @param row a DBObject row
     * @param fieldsToFilter list of field names to filter
     * @return
     */
    public static TreeMap<String, Object> getDataFromRow(DBObject row, String... fieldsToFilter) {
        if (row == null) {
            return null;
        }

        if (row instanceof BasicDBList) {
            throw new IllegalArgumentException("cannot convert an array to a map");
        }

        List<String> _fieldsToFilter = Arrays.asList(fieldsToFilter);

        TreeMap<String, Object> properties = new TreeMap<>();

        row.keySet().stream().forEach((key) -> {
            if (!_fieldsToFilter.contains(key)) {
                properties.put(key, getElement(row.get(key)));
            }
        });

        return properties;
    }

    /**
     * @param row a DBObject row
     * @param fieldsToFilter list of field names to filter
     * @return
     */
    private static Object getElement(Object element) {
        if (element == null) {
            return null;
        }

        if (element instanceof BasicDBList) {
            ArrayList<Object> ret = new ArrayList<>();

            BasicDBList dblist = (BasicDBList) element;

            dblist.stream().forEach((subel) -> {
                ret.add(getElement(subel));
            });

            return ret;
        }
        else if (element instanceof BasicDBObject) {
            TreeMap<String, Object> ret = new TreeMap<>();

            BasicDBObject el = (BasicDBObject) element;

            el.keySet().stream().forEach((key) -> {
                ret.put(key, el.get(key));
            });

            return ret;
        }
        else {
            return element;
        }
    }
}
