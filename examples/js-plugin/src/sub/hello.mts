import { defineService } from "restheart:sdk";

// a plugin in a subdirectory: package.json lists it as "sub/hello.mjs"
export default defineService({
    name: "anotherHelloWorldService",
    description: "yet another Hello World",
    uri: "/sub/hello",
    secured: false,
    matchPolicy: "EXACT",
    contentKind: "json",

    handle(req, res) {
        res.json({ msg: "Hello World, I'm the script in the sub directory" });
    },
});
