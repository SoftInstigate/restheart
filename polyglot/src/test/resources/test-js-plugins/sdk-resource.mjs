import { defineService, resource } from "restheart:sdk";

// reference plugin: a file shipped next to the plugin, and nothing outside the plugin directory
export default defineService({
    name: "sdkResource",
    description: "conformance: resource() reads inside the plugin directory only",
    uri: "/sdk/resource",
    secured: false,
    contentKind: "json",

    handle(req, res) {
        let outside;
        try {
            resource("../package.json");
            outside = "read";
        } catch (e) {
            outside = "refused";
        }

        res.json({
            text: resource("data/greeting.txt").trim(),
            bytes: resource.bytes("data/greeting.txt").length,
            exists: resource.exists("data/greeting.txt"),
            missing: resource.exists("data/nope.txt"),
            outside
        });
    }
});
