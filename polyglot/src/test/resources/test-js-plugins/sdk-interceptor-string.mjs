import { defineInterceptor } from "restheart:sdk";

// reference plugin: a string interceptor on the response of a string service
export default defineInterceptor({
    name: "sdkStringInterceptor",
    description: "conformance: contentKind string interceptor, res.content read and set",
    interceptPoint: "RESPONSE",
    contentKind: "string",

    handle(req, res) {
        res.content = `${res.content}!`;
        res.headers["x-intercepted"] = "string";
    },

    resolve(req, res) {
        return req.isHandledBy("sdkContentString");
    }
});
