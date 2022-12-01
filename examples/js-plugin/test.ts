interface Options {
    name: string;
    description: string;
    uri: string;
    secured: boolean;
    matchPolicy: string;
}

export const options: Options = {
    name: "helloWorldTS",
    description: "Test typescript",
    uri: "/helloTS",
    secured: false,
    matchPolicy: "EXACT"
}

export function handle (request: any, response: any): void {
    const rc = JSON.parse(request.getContent() || '{}');

    let body = {msg: `Hello ${rc.name || 'typescript'}`}

    response.setContent(JSON.stringify(body));
    response.setContentTypeAsJson();
}