import { defineInterceptor } from "restheart:sdk";

// reference plugin: a json interceptor on the response of a json service
export default defineInterceptor({
    name: "sdkJsonInterceptor",
    description: "conformance: contentKind json interceptor, res.content as native data",
    interceptPoint: "RESPONSE",
    contentKind: "json",

    handle(req, res) {
        res.content = { ...res.content, intercepted: true, statusSeen: res.statusCode };
    },

    resolve(req, res) {
        return req.isHandledBy("sdkContentJson") && req.isPost();
    }
});
