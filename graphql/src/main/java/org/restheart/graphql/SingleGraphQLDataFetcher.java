package org.restheart.graphql;

import com.mongodb.MongoClient;
import com.mongodb.client.FindIterable;
import graphql.GraphQL;
import graphql.schema.*;
import org.bson.Document;
import org.restheart.exchange.InvalidMetadataException;
import org.restheart.exchange.QueryVariableNotBoundException;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.mongodb.client.model.Projections.fields;
import static com.mongodb.client.model.Projections.include;

public class SingleGraphQLDataFetcher implements DataFetcher<Document>{

    private static SingleGraphQLDataFetcher instance = null;

    public static SingleGraphQLDataFetcher getInstance(){
        if (instance ==null){
            instance = new SingleGraphQLDataFetcher();
        }
        return instance;
    }
    private SingleGraphQLDataFetcher() { }


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
    public Document get(DataFetchingEnvironment dataFetchingEnvironment) throws InvocationTargetException,
            QueryVariableNotBoundException, InvalidMetadataException, NoSuchMethodException, IllegalAccessException {

        String typeName = ((GraphQLObjectType) dataFetchingEnvironment.getParentType()).getName(); //User
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

                return query.first();

            }

        }
        return null;
    }



        /**
        List<String> projField = new LinkedList<String>();
        for (SelectedField field: dataFetchingEnvironment.getSelectionSet().getFields()) {
            projField.add(field.getQualifiedName());
        }

        return query.projection(fields(include(projField))).first();
         **/



}

