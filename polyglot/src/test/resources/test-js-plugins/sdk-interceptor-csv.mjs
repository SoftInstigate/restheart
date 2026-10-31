import { defineInterceptor } from "restheart:sdk";

// reference plugin: a csv interceptor on the request of the csv loader, req.content is an array of documents
export default defineInterceptor({
    name: "sdkCsvInterceptor",
    description: "conformance: contentKind csv interceptor, req.content as an array of documents",
    interceptPoint: "REQUEST_AFTER_AUTH",
    contentKind: "csv",

    handle(req, res) {
        req.content = [...req.content].map(d => ({ ...d, imported: true }));
    },

    resolve(req, res) {
        return req.isPost() && req.query.db === "test-sdk" && req.query.coll === "csv";
    }
});
