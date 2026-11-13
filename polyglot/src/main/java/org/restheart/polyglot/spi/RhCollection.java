/*-
 * ========================LICENSE_START=================================
 * restheart-polyglot
 * %%
 * Copyright (C) 2020 - 2026 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.polyglot.spi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.conversions.Bson;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.restheart.polyglot.spi.proxy.BsonProxy;
import org.restheart.polyglot.spi.proxy.MapProxy;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.DeleteManyModel;
import com.mongodb.client.model.DeleteOneModel;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.UpdateManyModel;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.result.UpdateResult;

/**
 * A collection of the {@code mongo} capability: {@code find}, {@code findOne},
 * {@code countDocuments}, {@code distinct}, {@code aggregate}, {@code insertOne/Many},
 * {@code updateOne/Many}, {@code replaceOne}, {@code deleteOne/Many}, {@code bulkWrite}.
 *
 * <p>Filters, updates, documents and pipelines are plain object literals in canonical extended
 * JSON ({@code { _id: { $oid: id } }}); results come back the same way, as native data read
 * lazily from the driver's documents. Blocking, as every call on a virtual thread should be.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RhCollection {
    private final MongoCollection<BsonDocument> coll;

    RhCollection(MongoCollection<BsonDocument> coll) {
        this.coll = coll;
    }

    public String name() {
        return coll.getNamespace().getCollectionName();
    }

    // ---- reads ----

    public RhCursor find() {
        return find(null, null);
    }

    public RhCursor find(Value filter) {
        return find(filter, null);
    }

    /**
     * @param filter the filter, null or undefined for all documents
     * @param options {@code { limit, skip, sort, projection }}, all optional
     * @return a cursor, iterable and with {@code toArray()}
     */
    public RhCursor find(Value filter, Value options) {
        var it = coll.find(document(filter, "filter"));

        if (options != null && options.hasMembers()) {
            if (options.hasMember("limit")) {
                it = it.limit(options.getMember("limit").asInt());
            }
            if (options.hasMember("skip")) {
                it = it.skip(options.getMember("skip").asInt());
            }
            if (options.hasMember("sort")) {
                it = it.sort(document(options.getMember("sort"), "sort"));
            }
            if (options.hasMember("projection")) {
                it = it.projection(document(options.getMember("projection"), "projection"));
            }
            if (options.hasMember("batchSize")) {
                it = it.batchSize(options.getMember("batchSize").asInt());
            }
        }

        return new RhCursor(it);
    }

    public Object findOne() {
        return findOne(null, null);
    }

    public Object findOne(Value filter) {
        return findOne(filter, null);
    }

    public Object findOne(Value filter, Value options) {
        var it = coll.find(document(filter, "filter"));

        if (options != null && options.hasMembers()) {
            if (options.hasMember("sort")) {
                it = it.sort(document(options.getMember("sort"), "sort"));
            }
            if (options.hasMember("projection")) {
                it = it.projection(document(options.getMember("projection"), "projection"));
            }
            if (options.hasMember("skip")) {
                it = it.skip(options.getMember("skip").asInt());
            }
        }

        return BsonProxy.toGuest(it.first());
    }

    public long countDocuments() {
        return coll.countDocuments();
    }

    public long countDocuments(Value filter) {
        return coll.countDocuments(document(filter, "filter"));
    }

    public long estimatedDocumentCount() {
        return coll.estimatedDocumentCount();
    }

    public Object distinct(String field) {
        return distinct(field, null);
    }

    public Object distinct(String field, Value filter) {
        var values = new ArrayList<Object>();
        coll.distinct(field, document(filter, "filter"), BsonValue.class).forEach(v -> values.add(BsonProxy.toGuest(v)));
        return ProxyArray.fromList(values);
    }

    public RhCursor aggregate(Value pipeline) {
        return aggregate(pipeline, null);
    }

    /**
     * @param pipeline the stages, an array of documents
     * @param options {@code { allowDiskUse, batchSize }}, optional
     * @return a cursor
     */
    public RhCursor aggregate(Value pipeline, Value options) {
        var it = coll.aggregate(documents(pipeline, "pipeline"));

        if (options != null && options.hasMembers()) {
            if (options.hasMember("allowDiskUse")) {
                it = it.allowDiskUse(options.getMember("allowDiskUse").asBoolean());
            }
            if (options.hasMember("batchSize")) {
                it = it.batchSize(options.getMember("batchSize").asInt());
            }
        }

        return new RhCursor(it);
    }

    // ---- writes ----

    public Object insertOne(Value document) {
        var result = coll.insertOne(document(document, "document"));
        return result("acknowledged", result.wasAcknowledged(),
                "insertedId", result.getInsertedId() == null ? null : BsonProxy.toGuest(result.getInsertedId()));
    }

    public Object insertMany(Value documents) {
        var result = coll.insertMany(documents(documents, "documents"));
        var ids = new ArrayList<Object>();
        result.getInsertedIds().forEach((i, id) -> ids.add(BsonProxy.toGuest(id)));
        return result("acknowledged", result.wasAcknowledged(),
                "insertedCount", ids.size(),
                "insertedIds", ProxyArray.fromList(ids));
    }

    public Object updateOne(Value filter, Value update) {
        return updateOne(filter, update, null);
    }

    /**
     * @param filter the filter
     * @param update a document of operators, or an array of stages for a pipeline update
     * @param options {@code { upsert, arrayFilters }}, optional
     * @return {@code { matchedCount, modifiedCount, upsertedId }}
     */
    public Object updateOne(Value filter, Value update, Value options) {
        var f = document(filter, "filter");
        var uo = updateOptions(options);
        return updateResult(isPipeline(update)
                ? coll.updateOne(f, documents(update, "update"), uo)
                : coll.updateOne(f, document(update, "update"), uo));
    }

    public Object updateMany(Value filter, Value update) {
        return updateMany(filter, update, null);
    }

    public Object updateMany(Value filter, Value update, Value options) {
        var f = document(filter, "filter");
        var uo = updateOptions(options);
        return updateResult(isPipeline(update)
                ? coll.updateMany(f, documents(update, "update"), uo)
                : coll.updateMany(f, document(update, "update"), uo));
    }

    public Object replaceOne(Value filter, Value replacement) {
        return replaceOne(filter, replacement, null);
    }

    public Object replaceOne(Value filter, Value replacement, Value options) {
        var ro = new ReplaceOptions();
        if (options != null && options.hasMember("upsert")) {
            ro.upsert(options.getMember("upsert").asBoolean());
        }
        return updateResult(coll.replaceOne(document(filter, "filter"), document(replacement, "replacement"), ro));
    }

    public Object deleteOne(Value filter) {
        var result = coll.deleteOne(document(filter, "filter"));
        return result("acknowledged", result.wasAcknowledged(), "deletedCount", result.getDeletedCount());
    }

    public Object deleteMany(Value filter) {
        var result = coll.deleteMany(document(filter, "filter"));
        return result("acknowledged", result.wasAcknowledged(), "deletedCount", result.getDeletedCount());
    }

    /**
     * @param operations an array of {@code { insertOne: { document } }},
     *        {@code { updateOne: { filter, update, upsert? } }}, {@code { updateMany: ... }},
     *        {@code { replaceOne: { filter, replacement, upsert? } }},
     *        {@code { deleteOne: { filter } }}, {@code { deleteMany: { filter } }}
     * @return the counts
     */
    public Object bulkWrite(Value operations) {
        if (operations == null || !operations.hasArrayElements()) {
            throw new IllegalArgumentException("bulkWrite needs an array of operations");
        }

        var models = new ArrayList<WriteModel<BsonDocument>>();

        for (var i = 0L;i < operations.getArraySize();i++) {
            var op = operations.getArrayElement(i);
            if (op.hasMember("insertOne")) {
                models.add(new InsertOneModel<>(document(op.getMember("insertOne").getMember("document"), "document")));
            } else if (op.hasMember("updateOne")) {
                var u = op.getMember("updateOne");
                var f = document(u.getMember("filter"), "filter");
                models.add(isPipeline(u.getMember("update"))
                        ? new UpdateOneModel<>(f, documents(u.getMember("update"), "update"), updateOptions(u))
                        : new UpdateOneModel<>(f, document(u.getMember("update"), "update"), updateOptions(u)));
            } else if (op.hasMember("updateMany")) {
                var u = op.getMember("updateMany");
                var f = document(u.getMember("filter"), "filter");
                models.add(isPipeline(u.getMember("update"))
                        ? new UpdateManyModel<>(f, documents(u.getMember("update"), "update"), updateOptions(u))
                        : new UpdateManyModel<>(f, document(u.getMember("update"), "update"), updateOptions(u)));
            } else if (op.hasMember("replaceOne")) {
                var r = op.getMember("replaceOne");
                var ro = new ReplaceOptions();
                if (r.hasMember("upsert")) {
                    ro.upsert(r.getMember("upsert").asBoolean());
                }
                models.add(new ReplaceOneModel<>(document(r.getMember("filter"), "filter"), document(r.getMember("replacement"), "replacement"), ro));
            } else if (op.hasMember("deleteOne")) {
                models.add(new DeleteOneModel<>(document(op.getMember("deleteOne").getMember("filter"), "filter")));
            } else if (op.hasMember("deleteMany")) {
                models.add(new DeleteManyModel<>(document(op.getMember("deleteMany").getMember("filter"), "filter")));
            } else {
                throw new IllegalArgumentException("unknown bulkWrite operation at index " + i + ": " + op.getMemberKeys());
            }
        }

        var result = coll.bulkWrite(models);

        return result("acknowledged", result.wasAcknowledged(),
                "insertedCount", result.getInsertedCount(),
                "matchedCount", result.getMatchedCount(),
                "modifiedCount", result.getModifiedCount(),
                "deletedCount", result.getDeletedCount(),
                "upsertedCount", result.getUpserts().size());
    }

    // ---- indexes and the collection itself ----

    public String createIndex(Value keys) {
        return createIndex(keys, null);
    }

    /**
     * @param keys the index keys, {@code { field: 1 }}
     * @param options {@code { unique, name, sparse, expireAfterSeconds }}, optional
     * @return the index name
     */
    public String createIndex(Value keys, Value options) {
        var io = new IndexOptions();
        if (options != null && options.hasMembers()) {
            if (options.hasMember("unique")) {
                io.unique(options.getMember("unique").asBoolean());
            }
            if (options.hasMember("name")) {
                io.name(options.getMember("name").asString());
            }
            if (options.hasMember("sparse")) {
                io.sparse(options.getMember("sparse").asBoolean());
            }
            if (options.hasMember("expireAfterSeconds")) {
                io.expireAfter(options.getMember("expireAfterSeconds").asLong(), java.util.concurrent.TimeUnit.SECONDS);
            }
        }
        return coll.createIndex(document(keys, "keys"), io);
    }

    public void drop() {
        coll.drop();
    }

    /**
     * @return the driver's collection of {@link BsonDocument}, the escape hatch
     */
    public MongoCollection<BsonDocument> raw() {
        return coll;
    }

    // ---- helpers ----

    private static BsonDocument document(Value v, String what) {
        if (v == null || v.isNull()) {
            return new BsonDocument();
        }

        var bson = GuestValues.toBson(v);

        if (!bson.isDocument()) {
            throw new IllegalArgumentException(what + " must be a document, got " + v);
        }

        return bson.asDocument();
    }

    private static List<BsonDocument> documents(Value v, String what) {
        if (v == null || !v.hasArrayElements()) {
            throw new IllegalArgumentException(what + " must be an array of documents");
        }

        var docs = new ArrayList<BsonDocument>();

        for (var i = 0L;i < v.getArraySize();i++) {
            docs.add(document(v.getArrayElement(i), what + "[" + i + "]"));
        }

        return docs;
    }

    /**
     * An update is a document of operators or, since MongoDB 4.2, an array of pipeline stages.
     */
    private static boolean isPipeline(Value update) {
        return update != null && update.hasArrayElements();
    }

    private static UpdateOptions updateOptions(Value options) {
        var uo = new UpdateOptions();

        if (options != null && options.hasMembers()) {
            if (options.hasMember("upsert")) {
                uo.upsert(options.getMember("upsert").asBoolean());
            }
            if (options.hasMember("arrayFilters")) {
                uo.arrayFilters(new ArrayList<Bson>(documents(options.getMember("arrayFilters"), "arrayFilters")));
            }
        }

        return uo;
    }

    private static Object updateResult(UpdateResult r) {
        var m = new LinkedHashMap<String, Object>();
        m.put("acknowledged", r.wasAcknowledged());
        m.put("matchedCount", r.getMatchedCount());
        m.put("modifiedCount", r.getModifiedCount());
        m.put("upsertedId", r.getUpsertedId() == null ? null : BsonProxy.toGuest(r.getUpsertedId()));
        return new MapProxy(m);
    }

    /** A result object from key, value pairs; a null value is kept, as a null member. */
    private static Object result(Object... kv) {
        var m = new LinkedHashMap<String, Object>();
        for (var i = 0;i < kv.length;i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return new MapProxy(m);
    }
}
