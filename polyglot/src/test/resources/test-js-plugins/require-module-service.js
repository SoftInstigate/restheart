/* How to run:
$ npm install
*/

const moment = require('moment');
const oneLinerJoke = require('one-liner-joke');

export const options = {
    name: "requireModuleService",
    description: "just an example JavaScript service that uses a CommonJS module ",
    uri: '/requireModuleService',
    secured: false, // optional, default false
    matchPolicy: "PREFIX", // optional, default PREFIX,
    modulesReplacements: [ "" ] // optional
}

export function handle(request, response) {
    LOGGER.debug('request {}', request.getContent());
    const rc = JSON.parse(request.getContent() || '{}');

    let body = {
        msg: oneLinerJoke.getRandomJoke(),
        date: moment().format("[Today is] dddd")
    }

    response.setContent(JSON.stringify(body));
    response.setContentTypeAsJson();
}