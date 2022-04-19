export const options = {
    name: "ccHider",
    description: "hides credit card numbers",
    interceptPoint: "RESPONSE",
    pluginClass: "MongoInterceptor"
}

export function handle(request, response) {
    // convert bson content to json
    const json = fromBson(response.getContent());

    var hidden;

    if (Array.isArray(json)) {
        hidden = [];
        json.forEach(doc => hidden.push(hideFromDoc(doc)));
    } else {
        hidden = hideFromDoc(json);
    }

    // set response converting json to bson
    response.setContent(toBson(hidden));
}

export function resolve(request) {
    return request.isGet() && "creditcards" === request.getCollectionName();
}

/**
 * @see https://javadoc.io/doc/org.restheart/restheart-commons/latest/org/restheart/utils/BsonUtils.html
 */
const BsonUtils = Java.type("org.restheart.utils.BsonUtils");

function hideFromDoc(doc) {
    const ret = { ...doc };

    if (ret['cc'] && ret['cc'].length > 14) {
        ret['cc'] = ret['cc'].replace(/^.{14}/g, '****-****-****');
    }

    return ret;
}

/**
 * Convert a Java BsonElement (BsonDocument or BsonArray) to JSON
 * @param {*} bson
 * @returns
 */
function fromBson(bson) {
    return JSON.parse(BsonUtils.toJson(bson));
}

/**
 * Convert a JSON object or array to a Java BsonElement
 * @param {*} json
 * @returns
 */
function toBson(json) {
    return BsonUtils.parse(JSON.stringify(json));
}