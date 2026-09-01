export const options = {
    name: "helloWorldService",
    description: "just another Hello World",
    uri: "/hello",
    secured: false,
    matchPolicy: "EXACT"
}

export function handle(req, res) {
    if (req.isGet()) {
        res.setContent(JSON.stringify({ msg: "Hello World!" }));
        res.setContentTypeAsJson();
    }
}
