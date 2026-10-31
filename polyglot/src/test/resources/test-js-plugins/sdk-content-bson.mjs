import { defineService, bson } from "restheart:sdk";

// reference plugin: contentKind bson, extended JSON on read and on write, bson.oid()
export default defineService({
    name: "sdkContentBson",
    description: "conformance: contentKind bson",
    uri: "/sdk/content/bson",
    secured: false,
    contentKind: "bson",

    handle(req, res) {
        const c = req.content;
        res.json({
            oid: bson.oid(c.id),
            isOid: bson.isOid(bson.oid(c.id)),
            when: c.when,
            whenIsObject: typeof c.when === "object" && "$date" in c.when,
            n: c.n,
            nType: typeof c.n,
            big: { $numberLong: "4000000000" },
            dbl: 4000000000,
            date: new Date(0),
            nested: c.nested?.deep?.value ?? null,
            fresh: typeof bson.newOid().$oid
        });
    }
});
