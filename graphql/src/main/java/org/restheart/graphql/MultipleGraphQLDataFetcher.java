package org.restheart.graphql;

import com.mongodb.MongoClient;
import com.mongodb.client.FindIterable;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLObjectType;
import graphql.schema.SelectedField;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.*;

import static com.mongodb.client.model.Projections.fields;
import static com.mongodb.client.model.Projections.include;

public class MultipleGraphQLDataFetcher implements DataFetcher<List<Document>> {

    private static MultipleGraphQLDataFetcher instance = null;

    public static MultipleGraphQLDataFetcher getInstance(){
        if (instance ==null){
            instance = new MultipleGraphQLDataFetcher();
        }
        return instance;
    }
    private MultipleGraphQLDataFetcher() { }


    private static GraphQLApp currentApp = null;
    private static MongoClient mongoClient = null;


    public static GraphQLApp getCurrentApp() {
        return currentApp;
    }

    public static void setCurrentApp(GraphQLApp app) {
        currentApp = app;
    }

    public static MongoClient getMongoClient() {
        return mongoClient;
    }

    public static void setMongoClient(MongoClient mclient) {
        mongoClient = mclient;
    }

    @Override
    public List<Document> get(DataFetchingEnvironment dataFetchingEnvironment) throws Exception {

        String typeName = ((GraphQLObjectType) dataFetchingEnvironment.getParentType()).getName();
        String fieldName = dataFetchingEnvironment.getField().getName(); //sender

        String database;
        String collection;
        Document filter;
        FindIterable<Document> query;
        if (currentApp.getQueryMappings().containsKey(typeName)) {
            Map<String, QueryMapping> queryMappings = currentApp.getQueryMappingByType(typeName);
            if (queryMappings.containsKey(fieldName)) {
                QueryMapping queryMapping = queryMappings.get(fieldName);
                database = queryMapping.getTarget_db();
                collection = queryMapping.getTarget_collection();
                Document parentDocument = dataFetchingEnvironment.getSource();
                Map<String, Object> graphQLQueryArguments = dataFetchingEnvironment.getArguments();
                Document interpolatedArguments = queryMapping.interpolate(graphQLQueryArguments, parentDocument);

                if (interpolatedArguments.containsKey("filter")) {
                    filter = (Document) interpolatedArguments.get("filter");
                } else {
                    filter = new Document();
                }

                query = mongoClient.getDatabase(database)
                        .getCollection(collection)
                        .find(filter);

                if(!interpolatedArguments.isEmpty()){
                    if (interpolatedArguments.containsKey("sort")){
                        query = query.sort((Bson) interpolatedArguments.get("sort"));
                    }

                    if (interpolatedArguments.containsKey("skip")){
                        query = query.skip((int) interpolatedArguments.get("skip"));
                    }

                    if (interpolatedArguments.containsKey("limit")){
                        query = query.limit((int) interpolatedArguments.get("limit"));
                    }
                }

                ArrayList<Document> result = new ArrayList<Document>();
                query.into(result);
                return result;

            }

        }
        return null;
    }
}
