export const options = {
    name: "helloWorldInterceptor",
    description: "modifies the response of helloWorldService",
    interceptPoint: "RESPONSE"
}

export function handle(request, response) {
    LOGGER.debug('response {}', response.getContent());
    const rc = JSON.parse(response.getContent() || '{}');

    let modifiedBody = {
        msg: rc.msg + ' from Italy with Love'
    }

    response.setContent(JSON.stringify(modifiedBody));
    response.setContentTypeAsJson();
}

export function resolve(request) {
    return request.isHandledBy("helloWorldService");
}