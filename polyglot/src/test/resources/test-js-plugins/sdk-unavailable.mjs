import { defineService, teleport } from "restheart:sdk";

// reference plugin: imports something restheart:sdk does not export in this deployment, so the
// module fails to link at load time and is never deployed. The integration test deployment has
// every capability (emails is enabled there), so the import is of a name no deployment has;
// the per-capability reason in the log line is covered by SdkPluginTest.
export default defineService({
    name: "sdkUnavailable",
    description: "conformance: a plugin importing what the sdk does not provide is not deployed",
    uri: "/sdk/unavailable",
    secured: false,
    contentKind: "json",

    handle(req, res) {
        teleport();
        res.json({ deployed: true });
    }
});
