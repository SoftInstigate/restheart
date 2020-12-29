/* How to run:
$ cd plugins
$ npm init
$ npm install moment
*/

const moment = require('moment');

const JsonObject = Java.type('com.google.gson.JsonObject');
const JsonPrimitive = Java.type('com.google.gson.JsonPrimitive');

({
    options: {
        name: "testJsSrv",
        uri: '/test'
    },

    handle: (request, response) => {
        const rc = request.getContent();
        const name = rc !== null && rc.isJsonObject()
            ? rc.getAsJsonObject().get('name')
            : new JsonPrimitive('n.a');

        const body = new JsonObject();
        body.add('msg', new JsonPrimitive('Hello World'));
        body.add('name', name);
        body.add('date', new JsonPrimitive(moment().format("[Today is] dddd")));

        response.setContent(body);
    }
})