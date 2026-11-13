import { defineService } from "restheart:sdk";

// reference plugin: the request as the SDK shows it, path, method, query, headers, account
export default defineService({
    name: "sdkRequest",
    description: "conformance: req.path, req.method, req.query, req.queryAll, req.headers, req.account",
    uri: "/sdk/request",
    secured: true,
    contentKind: "json",

    handle(req, res) {
        res.json({
            path: req.path,
            method: req.method,
            isGet: req.isGet(),
            isPost: req.isPost(),
            query: {
                limit: req.query.limit,
                missing: req.query.missing === undefined ? "undefined" : req.query.missing,
                hasLimit: "limit" in req.query,
                keys: Object.keys(req.query)
            },
            queryAll: {
                tag: req.queryAll.tag ?? []
            },
            headers: {
                lower: req.headers["x-test"],
                upper: req.headers["X-TEST"],
                missing: req.headers["x-missing"] === undefined ? "undefined" : req.headers["x-missing"]
            },
            account: req.account,
            authenticated: req.isAuthenticated(),
            pathParams: req.pathParams("/sdk/{what}"),
            content: req.content
        });
    }
});
