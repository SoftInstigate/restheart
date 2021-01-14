/* How to run:
$ cd plugins
$ npm init
$ npm install one-liner-joke

(not needed to install moment since it's a builtin module)
*/

const moment = require('moment');
const oneLinerJoke = require('one-liner-joke');

({
    options: {
        name: "jsModule",
        description: "just an example JavaScript service that uses a CommonJS module ",
        uri: '/jsModule',
        secured: false, // optional, default false
        matchPolicy: "PREFIX", // optional, default PREFIX,
        modulesReplacements: [ "" ]
    },

    handle: (request, response) => {
        LOGGER.debug('request {}', request.getContent());
        const rc = JSON.parse(request.getContent() || '{}');

        let body = {
            msg: oneLinerJoke.getRandomJoke(),
            date: moment().format("[Today is] dddd")
        }

        response.setContent(JSON.stringify(body));
        response.setContentTypeAsJson();
    }
})