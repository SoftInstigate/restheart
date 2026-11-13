import { defineInterceptor } from "restheart:sdk";

// a mongo interceptor: runs on GET /coll/<docid> handled by the MongoDB service and adds a field
// to the response document. res.content is the document as native data, in extended JSON.
export default defineInterceptor({
    name: "mongoGetDocInterceptor",
    description: "modifies the response of GET /coll/<docid>",
    interceptPoint: "RESPONSE",
    contentKind: "mongo",

    handle(req, res) {
        res.content = { ...(res.content as object), injectedDoc: { n: 1, s: "foo" } };
    },

    resolve(req) {
        return req.isGet() && req.mongo.isDocument() && req.mongo.collectionName === "coll";
    },
});
