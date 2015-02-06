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
package org.restheart.db;

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
 * @author Andrea Di Cesare <andrea@softinstigate.com>
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
        } else if (element instanceof BasicDBObject) {
            TreeMap<String, Object> ret = new TreeMap<>();

            BasicDBObject el = (BasicDBObject) element;

            el.keySet().stream().forEach((key) -> {
                ret.put(key, el.get(key));
            });

            return ret;
        } else {
            return element;
        }
    }
}
