import { defineInterceptor, bson } from "restheart:sdk";

// reference plugin: a mongo interceptor on GET /test-sdk/icoll/{id}, req.mongo and res.content
export default defineInterceptor({
    name: "sdkMongoInterceptor",
    description: "conformance: contentKind mongo interceptor, req.mongo.dbName/collectionName/documentId/isDocument()",
    interceptPoint: "RESPONSE",
    contentKind: "mongo",

    handle(req, res) {
        res.content = {
            ...res.content,
            injected: {
                db: req.mongo.dbName,
                coll: req.mongo.collectionName,
                id: req.mongo.documentId,
                isDocument: req.mongo.isDocument(),
                isCollection: req.mongo.isCollection(),
                page: req.mongo.page,
                pagesize: req.mongo.pagesize
            }
        };
    },

    resolve(req, res) {
        return req.isGet() && req.mongo.isDocument() && req.mongo.dbName === "test-sdk" && req.mongo.collectionName === "icoll";
    }
});
