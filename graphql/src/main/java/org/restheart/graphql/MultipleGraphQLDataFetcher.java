package org.restheart.graphql;

import com.mongodb.MongoClient;
import com.mongodb.client.FindIterable;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLObjectType;
import graphql.schema.SelectedField;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.restheart.mongodb.db.MongoClientSingleton;
import org.restheart.utils.JsonUtils;

import java.util.*;

import static com.mongodb.client.model.Projections.fields;
import static com.mongodb.client.model.Projections.include;

public class MultipleGraphQLDataFetcher implements DataFetcher<List<BsonDocument>> {

    private static MultipleGraphQLDataFetcher instance = null;

    public static MultipleGraphQLDataFetcher getInstance(){
        if (instance ==null){
            instance = new MultipleGraphQLDataFetcher();
        }
        return instance;
    }
    private MultipleGraphQLDataFetcher() { }


    private static GraphQLApp currentApp = null;


    public static GraphQLApp getCurrentApp() {
        return currentApp;
    }

    public static void setCurrentApp(GraphQLApp app) {
        currentApp = app;
    }


    @Override
    public List<BsonDocument> get(DataFetchingEnvironment dataFetchingEnvironment) throws Exception {

        MongoClient mongoClient = MongoClientSingleton.getInstance().getClient();
        String typeName = ((GraphQLObjectType) dataFetchingEnvironment.getParentType()).getName();
        String fieldName = dataFetchingEnvironment.getField().getName(); //sender

        String database;
        String collection;
        BsonDocument filter;
        FindIterable<BsonDocument> query;
        if (currentApp.getQueryMappings().containsKey(typeName)) {
            Map<String, QueryMapping> queryMappings = currentApp.getQueryMappingByType(typeName);
            if (queryMappings.containsKey(fieldName)) {
                QueryMapping queryMapping = queryMappings.get(fieldName);
                database = queryMapping.getTarget_db();
                collection = queryMapping.getTarget_collection();
                BsonDocument parentDocument = dataFetchingEnvironment.getSource();
                Map<String, Object> graphQLQueryArguments = dataFetchingEnvironment.getArguments();
                BsonDocument interpolatedArguments = queryMapping.interpolate(JsonUtils.toBsonDocument(graphQLQueryArguments), parentDocument);

                if (interpolatedArguments.containsKey("filter")) {
                    filter = (BsonDocument) interpolatedArguments.get("filter");
                } else {
                    filter = new BsonDocument();
                }

                query = mongoClient.getDatabase(database)
                        .getCollection(collection, BsonDocument.class)
                        .find(filter);

                if(!interpolatedArguments.isEmpty()){
                    if (interpolatedArguments.containsKey("sort")){
                        query = query.sort(BsonDocument.parse(interpolatedArguments.getString("sort").getValue()));
                    }

                    if (interpolatedArguments.containsKey("skip")){
                        query = query.skip(interpolatedArguments.get("skip").asInt32().getValue());
                    }

                    if (interpolatedArguments.containsKey("limit")){
                        query = query.limit(interpolatedArguments.get("limit").asInt32().getValue());
                    }
                }

                ArrayList<BsonDocument> result = new ArrayList<BsonDocument>();
                query.into(result);
                return result;

            }

        }
        return null;
    }
}
