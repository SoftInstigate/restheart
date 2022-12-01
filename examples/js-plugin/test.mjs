export const options = {
    name: "helloWorldTS",
    description: "Test typescript",
    uri: "/helloTS",
    secured: false,
    matchPolicy: "EXACT"
};
export function handle(request, response) {
    const rc = JSON.parse(request.getContent() || '{}');
    let body = { msg: `Hello ${rc.name || 'World'}` };
    response.setContent(JSON.stringify(body));
    response.setContentTypeAsJson();
}
