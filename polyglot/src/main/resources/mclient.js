({
    options: {
        name: "jsMClient",
        description: "just an example JavaScript service that uses the MongoClient",
        uri: '/jsMClient',
        secured: true, // optional, default false
        matchPolicy: "EXACT" // optional, default PREFIX
    },

    handle: (request, response) => {
        let it = mclient.getDatabase("restheart").getCollection("coll").find().limit(10).iterator();

        let results = [];

        while(it.hasNext()) {
            results.push(JSON.parse(it.next().toJson()));
        }

        response.setContent(JSON.stringify(results));
        response.setContentTypeAsJson();
    }
})