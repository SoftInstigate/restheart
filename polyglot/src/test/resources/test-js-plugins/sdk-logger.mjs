import { defineService, logger } from "restheart:sdk";

// reference plugin: the logger is null-safe, where the raw SLF4J logger threw on a null argument
export default defineService({
    name: "sdkLogger",
    description: "conformance: logger with null, undefined, objects and arrays",
    uri: "/sdk/logger",
    secured: false,
    contentKind: "json",

    handle(req, res) {
        const nothing = null;
        let undef;
        logger.info("null is {}, undefined is {}", nothing, undef);
        logger.info("an object {} and an array {}", { a: 1, b: [1, 2] }, [1, "two", null]);
        logger.debug("a string {} a number {} a boolean {}", "s", 1.5, true);
        logger.warn("no args at all");
        logger.error("an error {}", new Error("logged, not thrown"));
        res.json({ logged: true, debugEnabled: typeof logger.isDebugEnabled() === "boolean" });
    }
});
