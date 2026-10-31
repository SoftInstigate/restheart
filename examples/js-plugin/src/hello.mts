import { defineService } from "restheart:sdk";

// the simplest service: GET /hello answers a JSON document
export default defineService({
    name: "helloWorldService",
    description: "just another Hello World",
    uri: "/hello",
    secured: false,      // optional, default false
    matchPolicy: "EXACT", // optional, default PREFIX
    contentKind: "json", // optional, default string

    handle(req, res) {
        if (req.isGet()) {
            res.json({ msg: `Hello ${req.query.name ?? "World"}!` });
        } else if (req.isOptions()) {
            res.headers["Access-Control-Allow-Methods"] = "GET, OPTIONS";
            res.headers["Access-Control-Allow-Headers"] = "Accept, Authorization, Content-Type, Origin";
        } else {
            res.statusCode = 405; // method not allowed
        }
    },
});
