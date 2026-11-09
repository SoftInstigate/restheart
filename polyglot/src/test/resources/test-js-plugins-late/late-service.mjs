import { defineService } from "restheart:sdk";

// copied before package.json declares it, deployed when package.json appears (#804)
export default defineService({
    name: "lateDeclaredService",
    description: "a service whose file is copied before package.json",
    uri: "/late-declared",
    secured: false,
    contentKind: "string",

    handle(req, res) {
        res.text("late");
    }
});
