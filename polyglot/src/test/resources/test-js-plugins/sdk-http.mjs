import { defineService, http } from "restheart:sdk";

// reference plugin: the http capability against a local endpoint, blocking, no promises
export default defineService({
    name: "sdkHttp",
    description: "conformance: http.get, http.post, http.request, ok === false on a 4xx",
    uri: "/sdk/http",
    secured: false,
    contentKind: "json",

    handle(req, res) {
        const base = "http://localhost:8080";

        const get = http.get(`${base}/sdk/echo?x=1`, { headers: { "X-Test": "t" }, timeout: 5000 });
        const post = http.post(`${base}/sdk/echo`, { hello: "world" });
        const form = http.request({ method: "POST", url: `${base}/sdk/content/string`, form: { a: "1", b: "x y" } });
        const auth = http.get(`${base}/sdk/echo`, { auth: { basic: { user: "admin", password: "secret" } } });
        const notFound = http.get(`${base}/sdk/errors?kind=notfound`);
        const error = http.get(`${base}/sdk/errors?kind=bad`);

        res.json({
            get: { status: get.status, ok: get.ok, x: get.json().x, header: get.headers["x-echo"], contentType: get.contentType },
            post: { status: post.status, content: post.json().content, contentType: post.json().contentType },
            form: { status: form.status, body: form.body },
            auth: auth.json().auth,
            notFound: { status: notFound.status, ok: notFound.ok },
            error: { status: error.status, ok: error.ok, msg: error.json().msg }
        });
    }
});
