/*
 * RESTHeart - the Web API for MongoDB
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
import com.mongodb.client.MongoCollection;
import static com.mongodb.client.model.Filters.eq;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.UpdateOptions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class DAOUtils {

    private final static Logger LOGGER = LoggerFactory.getLogger(DAOUtils.class);

    private final static FindOneAndUpdateOptions FAU_UPSERT_OPS
            = new FindOneAndUpdateOptions()
            .upsert(true);

    private final static UpdateOptions U_UPSERT_OPS
            = new UpdateOptions()
            .upsert(true);

    /**
     * @param rows list of DBObject rows as returned by getDataFromCursor()
     * @return
     */
    public static List<Map<String, Object>> getDataFromRows(final List<DBObject> rows) {
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
    public static TreeMap<String, Object> getDataFromRow(final DBObject row, String... fieldsToFilter) {
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
    private static Object getElement(final Object element) {
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

    /**
     *
     * @param newContent the value of newContent
     * @return a not null DBObject
     */
    protected static DBObject validContent(final DBObject newContent) {
        return (newContent == null) ? new BasicDBObject() : newContent;
    }

    private static final String _UPDATE_OPERATORS[] = {
        "$inc", "$mul", "$rename", "$setOnInsert", "$set", "$unset", // Field Update Operators
        "$min", "$max", "$currentDate",
        "$", "$addToSet", "$pop", "$pullAll", "$pull", "$pushAll", "$push", // Array Update Operators
        "$bit", // Bitwise Update Operator
        "$isolated" // Isolation Update Operator
    };

    private static final List<String> UPDATE_OPERATORS
            = Arrays.asList(_UPDATE_OPERATORS);

    public static Document updateDocument(MongoCollection<Document> coll, Object documentId, Document data, boolean replace) {
        Objects.requireNonNull(coll);
        Objects.requireNonNull(data);

        List<String> keys;

        keys = data.keySet().stream().filter((String key)
                -> !UPDATE_OPERATORS.contains(key))
                .collect(Collectors.toList());

        if (keys != null && !keys.isEmpty()) {

            Document set = new Document();

            keys.stream().forEach((String key)
                    -> {
                Object o = data.remove(key);

                set.append(key, o);
            });

            if (data.get("$set") == null) {
                data.put("$set", set);
            } else if (data.get("$set") instanceof Document) {
                ((Document) data.get("$set"))
                        .putAll(set);
            } else if (data.get("$set") instanceof DBObject) { //TODO remove this after migration to mongodb driver 3.2 completes
                ((DBObject) data.get("$set"))
                        .putAll(set);
            } else {
                LOGGER.warn("count not add properties to $set since request data contains $set property which is not an object: {}", data.get("$set"));
            }
        }

        if (replace) {
            // here we cannot use the atomic findOneAndReplace because it does
            // not support update operators.
            
            Document oldDocument = coll.findOneAndDelete(eq("_id", documentId));

            coll.updateOne(eq("_id", documentId), data, U_UPSERT_OPS);

            return oldDocument;
        } else {
            return coll.findOneAndUpdate(eq("_id", documentId), data, FAU_UPSERT_OPS);
        }
    }
}
