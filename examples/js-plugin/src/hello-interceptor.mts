import { defineInterceptor, logger } from "restheart:sdk";

// modifies the response of helloWorldService: res.content is native data for a json interceptor
export default defineInterceptor({
    name: "helloWorldInterceptor",
    description: "modifies the response of helloWorldService",
    interceptPoint: "RESPONSE",
    contentKind: "json",

    handle(req, res) {
        logger.debug("response before: {}", res.content);

        const body = res.content as { msg?: string };

        res.content = {
            msg: `${body.msg ?? ""} from Italy with Love`,
            note: "'from Italy with Love' was added by 'helloWorldInterceptor' that modifies the response of 'helloWorldService'",
        };
    },

    resolve(req) {
        return req.isHandledBy("helloWorldService") && req.isGet();
    },
});
