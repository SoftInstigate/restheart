import { defineInterceptor } from "restheart:sdk";

// copied before package.json declares it, deployed when package.json appears (#804)
export default defineInterceptor({
    name: "lateDeclaredInterceptor",
    description: "an interceptor whose file is copied before package.json",
    interceptPoint: "RESPONSE",
    contentKind: "string",

    handle(req, res) {
        res.content = `${res.content}!`;
    },

    resolve(req, res) {
        return req.isHandledBy("lateDeclaredService");
    }
});
