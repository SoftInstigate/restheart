({
    options: {
        name: "testJsSrv",
        uri: '/test'
    },

    handle: (request, response) => {
        LOGGER.debug('request {}', request.getContent());
        const rc = JSON.parse(request.getContent());

        let body = {
            msg: `Hello ${rc.name}`
        }

        response.setContent(JSON.stringify(body));
        response.setContentTypeAsJson();
    }
})