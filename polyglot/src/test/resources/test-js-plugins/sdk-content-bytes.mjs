import { defineService, encode } from "restheart:sdk";

// reference plugin: contentKind bytes, req.content is bytes, res.bytes()
export default defineService({
    name: "sdkContentBytes",
    description: "conformance: contentKind bytes",
    uri: "/sdk/content/bytes",
    secured: false,
    contentKind: "bytes",

    handle(req, res) {
        const bytes = req.content;
        const text = encode.fromUtf8(bytes);
        res.bytes(encode.toUtf8(`${bytes.length}:${text}`));
    }
});
