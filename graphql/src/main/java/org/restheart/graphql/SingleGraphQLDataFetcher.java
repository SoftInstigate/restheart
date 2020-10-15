package org.restheart.graphql;

import com.mongodb.MongoClient;
import com.mongodb.client.FindIterable;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.SelectedField;
import org.bson.Document;
import org.restheart.exchange.InvalidMetadataException;
import org.restheart.exchange.QueryVariableNotBoundException;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
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
    public Document get(DataFetchingEnvironment dataFetchingEnvironment) {
        String queryName = dataFetchingEnvironment.getField().getName();
        try{
            // try to retrieve query with name queryName from app queries
            Query queryDef = currentApp.getQueryByName(queryName);
            // get graphql query's arguments
            Map<String, Object> graphQLQueryArguments = dataFetchingEnvironment.getArguments();
            // interpolate filter of query definition with graphql query
            Map<String, Document> interpolatedArguments = queryDef.interpolate(graphQLQueryArguments);

            // build mongodb query

            FindIterable<Document> query;

            if (interpolatedArguments.containsKey("filter")){
                query = mongoClient.getDatabase(queryDef.getDb())
                        .getCollection(queryDef.getCollection(), Document.class)
                        .find(interpolatedArguments.get("filter"));
            }
            else{
                query = mongoClient.getDatabase(queryDef.getDb())
                        .getCollection(queryDef.getCollection(), Document.class)
                        .find();
            }

            List<String> projField = new LinkedList<String>();
            for (SelectedField field: dataFetchingEnvironment.getSelectionSet().getFields()) {
                projField.add(field.getQualifiedName());
            }

            return query.projection(fields(include(projField))).first();

        }catch (NullPointerException e){
            System.out.println("No query found with name "+ queryName );
            return null;
        }catch (InvalidMetadataException | QueryVariableNotBoundException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            e.printStackTrace();
            return null;
        }
    }


}

