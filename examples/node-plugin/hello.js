({
    options: {
        name: "helloWorldService",
        description: "just another Hello World",
        uri: "/hello",
        secured: false, // optional, default false
        matchPolicy: "EXACT" // optional, default PREFIX
    },

    handle: (request, response) => {
        const rc = JSON.parse(request.getContent() || '{}');
        LOGGER.debug('request {}', rc);

        let body = {
            msg: `Hello ${rc.name || 'World'}`
        };

        response.setContent(JSON.stringify(body));
        response.setContentTypeAsJson();
    }
})
