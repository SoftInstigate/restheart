/* How to run:
$ cd plugins
$ npm init
$ npm install moment
*/

const moment = require('moment');

({
    options: {
        name: "testJsSrv",
        uri: '/test'
    },

    handle: (request, response) => {
        const rc = JSON.parse(new String(request.getContentString()));

        let body = {
            msg: `Hello ${rc.name }`,
            date: moment().format("[Today is] dddd")
        }

        LOGGER.debug('******** {}', rc);

        response.setContent(JSON.stringify(body));
        response.setContentTypeAsJson();
    }
})