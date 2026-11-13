import { defineService, mongo, bson } from "restheart:sdk";

// reference plugin: the mongo capability, reads, writes, cursors and command()
export default defineService({
    name: "sdkMongo",
    description: "conformance: mongo.db().collection() find, findOne, count, aggregate, writes, command",
    uri: "/sdk/mongo",
    secured: false,
    contentKind: "json",

    handle(req, res) {
        const db = mongo.db("test-sdk");
        const coll = db.collection("coll");
        coll.deleteMany({});

        const inserted = coll.insertMany([
            { name: "a", n: 1, when: new Date(0), tags: ["x"] },
            { name: "b", n: 2, when: { $date: "2026-01-01T00:00:00Z" } },
            { name: "c", n: 3, big: { $numberLong: "9007199254740993" }, dec: { $numberDecimal: "1.50" } }
        ]);

        const one = coll.insertOne({ name: "d", n: 4 });
        const byId = coll.findOne({ _id: one.insertedId });
        const byOidLiteral = coll.findOne({ _id: { $oid: bson.oid(one.insertedId) } });

        const sorted = coll.find({ n: { $gt: 1 } }, { sort: { n: -1 }, limit: 2, projection: { _id: 0, name: 1 } }).toArray();
        let iterated = 0;
        for (const d of coll.find()) iterated += d.n;
        const spread = [...coll.find({}, { sort: { n: 1 } })].map(d => d.name);

        const updated = coll.updateOne({ name: "a" }, { $set: { n: 10 }, $push: { tags: "y" } });
        const upserted = coll.updateOne({ name: "zzz" }, { $set: { n: 99 } }, { upsert: true });
        const replaced = coll.replaceOne({ name: "b" }, { name: "b", n: 20 });
        const agg = coll.aggregate([{ $match: { n: { $gte: 10 } } }, { $group: { _id: null, total: { $sum: "$n" } } }]).first();
        const bulk = coll.bulkWrite([
            { insertOne: { document: { name: "e", n: 5 } } },
            { updateOne: { filter: { name: "e" }, update: { $set: { n: 50 } } } },
            { deleteOne: { filter: { name: "zzz" } } }
        ]);
        const distinct = coll.distinct("name", { n: { $gte: 10 } });
        const count = coll.countDocuments({ n: { $gte: 10 } });
        const c = coll.findOne({ name: "c" });
        const a = coll.findOne({ name: "a" });
        const ping = db.command({ ping: 1 });
        const index = coll.createIndex({ name: 1 }, { unique: true, name: "by_name" });
        const deleted = coll.deleteMany({ n: { $lt: 10 } });

        res.json({
            insertedCount: inserted.insertedCount,
            insertedIdIsOid: typeof one.insertedId.$oid === "string",
            byId: byId.name,
            byOidLiteral: byOidLiteral.name,
            sorted: sorted.map(d => d.name),
            iterated,
            spread,
            updated: { matched: updated.matchedCount, modified: updated.modifiedCount },
            upsertedIdIsOid: typeof upserted.upsertedId?.$oid === "string",
            replaced: replaced.modifiedCount,
            aggTotal: agg.total,
            bulk: { inserted: bulk.insertedCount, modified: bulk.modifiedCount, deleted: bulk.deletedCount },
            distinct: [...distinct].sort(),
            count,
            cBig: c.big,
            cDec: c.dec,
            aWhen: a.when,
            aTags: [...a.tags],
            ping: ping.ok,
            index,
            deleted: deleted.deletedCount,
            rawIsClient: typeof mongo.raw().getDatabase === "function"
        });
    }
});
