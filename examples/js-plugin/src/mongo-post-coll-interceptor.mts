import { defineInterceptor } from "restheart:sdk";

// a mongo interceptor on the request: adds a timestamp to the document of POST /coll.
// A native Date is written as a BSON date.
export default defineInterceptor({
    name: "mongoPostCollInterceptor",
    description: "modifies the content of POST requests adding a timestamp",
    interceptPoint: "REQUEST_AFTER_AUTH",
    contentKind: "mongo",

    handle(req, res) {
        req.content = { ...(req.content as object), timestamp: new Date() };
    },

    resolve(req) {
        return req.isPost() && req.mongo.collectionName === "coll";
    },
});
