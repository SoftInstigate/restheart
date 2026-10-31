import { defineService } from "restheart:sdk";

// a pure JavaScript npm package from node_modules: `npm install` first.
// GraalJS has no Node built-ins, so a package that needs fs, crypto or http does not run;
// what those give is in the SDK instead (resource, crypto, http).
import moment from "moment";

export default defineService({
    name: "requireModuleService",
    description: "a service that uses an npm module",
    uri: "/requireModuleService",
    secured: false,
    matchPolicy: "PREFIX",
    contentKind: "json",

    handle(req, res) {
        res.json({ date: moment().format("[Today is] dddd") });
    },
});
