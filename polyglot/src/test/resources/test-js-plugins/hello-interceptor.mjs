export const options = {
    name: "helloWorldInterceptor",
    description: "modifies the response of helloWorldService",
    interceptPoint: "RESPONSE"
}

export function handle(req, res) {
    var body = JSON.parse(res.getContent());
    body.note = "modified by helloWorldInterceptor";
    res.setContent(JSON.stringify(body));
}

export function resolve(req) {
    return req.isHandledBy("helloWorldService") && req.isGet();
}
