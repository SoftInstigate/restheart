const http = require('http');

({
    options: {
        name: "nodeModuleSrv",
        description: "just an example node service that requires http",
        uri: '/test',
        secured: false, // optional, default false
        matchPolicy: "PREFIX" // optional, default PREFIX
    },

    handle: (request, response) => {
        // LOGGER.debug('request {}', request.getContent());
        const options = {
            hostname: 'httpbin.org',
            port: 80,
            path: '/anything',
            method: 'GET'
        }

        const req = http.request(options, res => {
            let data = '';

            console.log(`statusCode: ${res.statusCode}`);

            res.on('data', d => {
                data += d;
            });

            res.on('end', () => {
                console.log(JSON.parse(data));
            });
        }).on("error", (err) => {
            console.log("Error: " + err.message);
        });

        req.end();

        const rc = JSON.parse(request.getContent() || '{}');

        let body = {
            msg: `Hello ${rc.name || 'World'}`
        };

        response.setContent(JSON.stringify(body));
        response.setContentTypeAsJson();
    }
})