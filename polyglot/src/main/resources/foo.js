({
    options: {
        name: "testJsSrv",
        uri: '/test'
    },

    handle: (request, response) => {
        var doc = mclient.getDatabase("restheart").getCollection("coll").find().first().toJson();

        doc.toString()

        LOGGER.debug('request {}', request.getContent());
        const rc = JSON.parse(request.getContent());

        let body = {
            msg: `Hello ${rc.name}`,
            doc: JSON.parse(doc)
        }

        response.setContent(JSON.stringify(body));
        response.setContentTypeAsJson();
    }
})