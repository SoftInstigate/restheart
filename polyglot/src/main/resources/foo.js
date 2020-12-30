({
    options: {
        name: "testJsSrv",
        description: "just an example JavaScript service",
        uri: '/test',
        secured: false, // optional, default false
        matchPolicy: "PREFIX" // optional, default PREFIX
    },

    handle: (request, response) => {
        LOGGER.debug('request {}', request.getContent());
        const rc = JSON.parse(request.getContent() || '{}');

        let body = {
            msg: `Hello ${rc.name || 'World'}`
        }

        response.setContent(JSON.stringify(body));
        response.setContentTypeAsJson();
    }
})