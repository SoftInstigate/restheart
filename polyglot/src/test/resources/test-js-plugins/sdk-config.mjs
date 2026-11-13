import { defineService, config } from "restheart:sdk";

// reference plugin: the plugin's configuration from restheart.yml, by plugin name
export default defineService({
    name: "sdkConfig",
    description: "conformance: config is the plugin's section of the configuration",
    uri: "/sdk/config",
    secured: false,
    contentKind: "json",

    handle(req, res) {
        res.json({
            greeting: config.greeting ?? null,
            nested: config.nested?.value ?? null,
            list: config.list ? [...config.list] : null,
            missing: config.missing === undefined ? "undefined" : config.missing,
            legacyPluginArgs: typeof pluginArgs === "object" ? (pluginArgs.greeting ?? null) : "no global"
        });
    }
});
