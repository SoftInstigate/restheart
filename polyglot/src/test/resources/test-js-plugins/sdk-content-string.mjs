import { defineService } from "restheart:sdk";

// reference plugin: contentKind string, req.content is a string, res.text()
export default defineService({
    name: "sdkContentString",
    description: "conformance: contentKind string",
    uri: "/sdk/content/string",
    secured: false,
    contentKind: "string",

    handle(req, res) {
        const content = req.content ?? "";
        res.text(`${typeof content}:${content.toUpperCase()}`);
    }
});
