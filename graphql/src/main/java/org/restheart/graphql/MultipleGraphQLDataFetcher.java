package org.restheart.graphql;

import com.mongodb.MongoClient;
import com.mongodb.client.FindIterable;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.SelectedField;
import org.restheart.exchange.InvalidMetadataException;
import org.restheart.exchange.QueryVariableNotBoundException;
import org.bson.Document;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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
        String queryName = dataFetchingEnvironment.getField().getName();
        try{
            Query queryDef = currentApp.getQueryByName(queryName);
            Map<String, Object> graphQLQueryArguments = dataFetchingEnvironment.getArguments();

            Map<String, Document> interpolatedArguments = queryDef.interpolate(graphQLQueryArguments);

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

            if (interpolatedArguments.containsKey("sort")){
                query = query.sort(interpolatedArguments.get("sort"));
            }

            if (interpolatedArguments.containsKey("skip")){
                query = query.skip((int) interpolatedArguments.get("skip").get("skip"));
            }

            if (interpolatedArguments.containsKey("limit")){
                query = query.limit((int) interpolatedArguments.get("limit").get("limit"));
            }

             // find projection fields
            List<String> projField = new LinkedList<String>();
            for (SelectedField field: dataFetchingEnvironment.getSelectionSet().getFields()) {
             projField.add(field.getQualifiedName());
            }
            ArrayList<Document> result = new ArrayList<Document>();

            query.projection(fields(include(projField))).into(result);

            return result;

        }catch (NullPointerException e){
            System.out.println("No query found with name "+ queryName );
            return null;
        }catch (InvalidMetadataException | QueryVariableNotBoundException e) {
            e.printStackTrace();
            return null;
        }
    }
}
