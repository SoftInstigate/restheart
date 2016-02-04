/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) SoftInstigate Srl
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
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.MongoCollection;
import static com.mongodb.client.model.Filters.eq;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.result.UpdateResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.restheart.utils.HttpStatus;
import static org.restheart.utils.RequestHelper.UPDATE_OPERATORS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.mongodb.client.model.Filters.and;
import java.util.Optional;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class DAOUtils {

    public final static Logger LOGGER = LoggerFactory.getLogger(DAOUtils.class);

    public final static FindOneAndUpdateOptions FAU_UPSERT_OPS
            = new FindOneAndUpdateOptions()
            .upsert(true);

    public final static FindOneAndUpdateOptions FAU_AFTER_UPSERT_OPS
            = new FindOneAndUpdateOptions()
            .upsert(true).returnDocument(ReturnDocument.AFTER);

    public final static UpdateOptions U_UPSERT_OPS
            = new UpdateOptions()
            .upsert(true);

    public final static UpdateOptions U_NOT_UPSERT_OPS
            = new UpdateOptions()
            .upsert(false);

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

    public static OperationResult updateDocument(
            MongoCollection<Document> coll,
            Object documentId,
            Document data,
            boolean replace) {
        return updateDocument(coll, documentId, data, replace, false);
    }

    private static final Bson IMPOSSIBLE_CONDITION =  eq("_etag", new ObjectId());

    /**
     *
     * @param coll
     * @param documentId use Optional.empty() to specify no documentId (null is
     * _id: null)
     * @param data
     * @param replace
     * @param returnNew
     * @return
     */
    public static OperationResult updateDocument(
            MongoCollection<Document> coll,
            Object documentId,
            Document data,
            boolean replace,
            boolean returnNew) {
        Objects.requireNonNull(coll);
        Objects.requireNonNull(data);

        Document document = getUpdateDocument(data);

        Bson query;
        boolean idPresent = true;

        if (documentId instanceof Optional
                && !((Optional) documentId).isPresent()) {
            query = IMPOSSIBLE_CONDITION;
            idPresent = false;
        } else {
            query = eq("_id", documentId);
        }

        if (replace) {
            // here we cannot use the atomic findOneAndReplace because it does
            // not support update operators.

            Document oldDocument;

            if (idPresent) {
                oldDocument = coll.findOneAndDelete(query);
            } else {
                oldDocument = null;
            }

            Document newDocument = coll.findOneAndUpdate(query, document, FAU_AFTER_UPSERT_OPS);

            return new OperationResult(-1, oldDocument, newDocument);
        } else if (returnNew) {
            Document newDocument = coll.findOneAndUpdate(query, document, FAU_AFTER_UPSERT_OPS);

            return new OperationResult(-1, null, newDocument);
        } else {
            Document oldDocument = coll.findOneAndUpdate(query, document, FAU_UPSERT_OPS);

            return new OperationResult(-1, oldDocument, null);
        }
    }

    public static boolean restoreDocument(
            MongoCollection<Document> coll,
            Object documentId,
            Document data,
            Object etag) {
        Objects.requireNonNull(coll);
        Objects.requireNonNull(documentId);
        Objects.requireNonNull(data);

        Bson filter;

        if (etag == null) {
            filter = eq("_id", documentId);
        } else {
            filter = and(eq("_id", documentId), eq("_etag", etag));
        }

        UpdateResult result = coll.replaceOne(filter, data, U_NOT_UPSERT_OPS);

        if (result.isModifiedCountAvailable()) {
            return result.getModifiedCount() == 1;
        } else {
            return true;
        }
    }

    public static BulkOperationResult bulkUpsertDocuments(
            MongoCollection<Document> coll,
            final List<Document> documents) {
        Objects.requireNonNull(coll);
        Objects.requireNonNull(documents);

        ObjectId newEtag = new ObjectId();

        List<WriteModel<Document>> wm = getBulkWriteModel(coll, documents, newEtag);

        BulkWriteResult result = coll.bulkWrite(wm);

        return new BulkOperationResult(HttpStatus.SC_OK, newEtag, result);
    }

    private static List<WriteModel<Document>> getBulkWriteModel(
            final MongoCollection<Document> mcoll,
            final List<Document> documents,
            final ObjectId etag) {
        Objects.requireNonNull(mcoll);
        Objects.requireNonNull(documents);

        List<WriteModel<Document>> updates = new ArrayList<>();

        documents.stream().forEach((document) -> {
            // generate new id if missing, will be an insert
            if (!document.containsKey("_id")) {
                document.put("_id", new ObjectId());
            }

            // add the _etag
            document.put("_etag", etag);

            updates.add(new UpdateOneModel<>(
                    new Document("_id", document.get("_id")),
                    getUpdateDocument(document),
                    new UpdateOptions().upsert(true)
            ));
        });

        return updates;
    }

    /**
     *
     * @param document
     * @return the document for update operation, with proper update operators
     */
    public static Document getUpdateDocument(Document document) {
        Document ret = new Document();

        // add properties to $set update operator
        List<String> setKeys;

        setKeys = document.keySet().stream().filter((String key)
                -> !UPDATE_OPERATORS.contains(key))
                .collect(Collectors.toList());

        if (setKeys != null && !setKeys.isEmpty()) {

            Document set = new Document();

            setKeys.stream().forEach((String key)
                    -> {
                set.append(key, document.get(key));
            });

            if (document.get("$set") == null) {
                ret.put("$set", set);
            } else if (document.get("$set") instanceof Document) {
                ((Document) ret.get("$set"))
                        .putAll(set);
            } else if (document.get("$set") instanceof DBObject) { //TODO remove this after migration to mongodb driver 3.2 completes
                ((DBObject) ret.get("$set"))
                        .putAll(set);
            } else {
                LOGGER.warn("count not add properties to $set since request data contains $set property which is not an object: {}", document.get("$set"));
            }
        }

        // add other update operators
        document.keySet().stream().filter((String key)
                -> UPDATE_OPERATORS.contains(key))
                .forEach(key -> {
                    ret.put(key, document.get(key));
                });

        return ret;
    }
}
