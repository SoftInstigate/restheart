export const options = {
    "name": "anotherHelloWorldService",
    "description": "yet another Hello World",
    "uri": "/sub/hello",
    "secured": false,
    "matchPolicy": "EXACT"
}

export function handle(request, response) {
    response.setContent(JSON.stringify({ msg: 'Hello World, I\'m the script in the sub directory' }));
    response.setContentTypeAsJson();
};