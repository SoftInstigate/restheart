import { defineService, providers } from "restheart:sdk";

// reference plugin: any registered provider by name, the configured instance
export default defineService({
    name: "sdkProviders",
    description: "conformance: providers[name]",
    uri: "/sdk/providers",
    secured: false,
    contentKind: "json",

    handle(req, res) {
        const names = Object.keys(providers);
        res.json({
            hasMclient: names.includes("mclient"),
            hasRhConfig: names.includes("rh-config"),
            hasHttpClient: names.includes("http-client"),
            rhConfigIsObject: typeof providers["rh-config"] === "object",
            missing: providers["no-such-provider"] === undefined || providers["no-such-provider"] === null
        });
    }
});
