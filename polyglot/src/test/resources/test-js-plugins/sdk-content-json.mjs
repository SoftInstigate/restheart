import { defineService } from "restheart:sdk";

// reference plugin: contentKind json, req.content is native data, res.json()
export default defineService({
    name: "sdkContentJson",
    description: "conformance: contentKind json",
    uri: "/sdk/content/json",
    secured: false,
    contentKind: "json",

    handle(req, res) {
        const c = req.content;
        res.json({
            name: c?.user?.name ?? null,
            tags: c?.tags ? [...c.tags].map(t => t.toUpperCase()) : [],
            n: c?.n ?? null,
            keys: c ? Object.keys(c) : [],
            spread: c ? { ...c } : null
        });
    }
});
