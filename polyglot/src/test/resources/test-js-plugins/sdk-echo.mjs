import { defineService } from "restheart:sdk";

// reference plugin: a local endpoint for the http capability tests
export default defineService({
    name: "sdkEcho",
    description: "conformance: echoes method, query, a header and the json content",
    uri: "/sdk/echo",
    secured: false,
    contentKind: "json",

    handle(req, res) {
        res.headers["x-echo"] = "yes";
        res.json({
            method: req.method,
            x: req.query.x,
            auth: req.headers["authorization"] ?? null,
            contentType: req.headers["content-type"] ?? null,
            content: req.content
        });
    }
});
