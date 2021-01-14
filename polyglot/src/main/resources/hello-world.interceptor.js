({
    options: {
        name: "helloWorldInterceptor",
        description: "modifies the response of helloWorldService",
        interceptPoint: "RESPONSE"
    },

    handle: (request, response) => {
        LOGGER.debug('response {}', response.getContent());
        const rc = JSON.parse(response.getContent() || '{}');

        let modifiedBody = {
            msg: rc.msg + ' from Italy with Love'
        }

        response.setContent(JSON.stringify(modifiedBody));
        response.setContentTypeAsJson();
    },

    resolve: (request) => {
        return request.isHandledBy("helloWorldService");
    }
})