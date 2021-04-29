export const options = {
    name: "unirestService",
    description: "a service that uses Unirest to execute a GET request",
    uri: "/unirest",
    secured: false, // optional, default false
    matchPolicy: "EXACT" // optional, default PREFIX
}

export function handle(request, response) {
    const anything = Unirest.get('https://httpbin.org/anything').asString().getBody();
    response.setContent(anything);
    response.setContentTypeAsJson();
}