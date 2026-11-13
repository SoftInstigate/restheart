import { defineService, http, logger } from "restheart:sdk";

// the http capability: the shared, configured client of the http-client provider.
// The call blocks on a virtual thread, which is the right thing: no promises, no await.
export default defineService({
    name: "httpClientService",
    description: "a service that calls another API with the http capability",
    uri: "/httpClient",
    secured: false,
    matchPolicy: "EXACT",
    contentKind: "json",

    handle(req, res) {
        const r = http.post("https://httpbin.org/anything", { foo: 2, bar: 1 }, { timeout: 10000 });

        // a 4xx or a 5xx does not throw: it comes back with ok === false
        if (!r.ok) {
            logger.warn("httpbin answered {}", r.status);
            res.statusCode = 502;
            res.json({ msg: `upstream answered ${r.status}` });
            return;
        }

        const body = r.json() as { json: unknown };

        res.json(body.json);
    },
});
