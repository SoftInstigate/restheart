export const options = {
    name: "helloWorldInterceptor",
    description: "modifies the response of helloWorldService",
    interceptPoint: "RESPONSE"
}

export function handle(req, res) {
    LOGGER.debug('response {}', res.getContent());
    const rc = JSON.parse(res.getContent() || '{}');

    let modifiedBody = {
        msg: rc.msg + ' from Italy with Love',
        note: '\'from Italy with Love\' was added by \'helloWorldInterceptor\' that modifies the response of \'helloWorldService\''
    }

    res.setContent(JSON.stringify(modifiedBody));
    res.setContentTypeAsJson();
}

export function resolve(req) {
    return req.isHandledBy("helloWorldService") && req.isGet();
}