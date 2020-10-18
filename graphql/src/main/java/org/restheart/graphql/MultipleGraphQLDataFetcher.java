package org.restheart.graphql;

import com.mongodb.MongoClient;
import com.mongodb.client.FindIterable;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.SelectedField;
import org.bson.Document;

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

        String fieldName = dataFetchingEnvironment.getField().getName();
        String database;
        String collection;
        Document filter;
        Map<String, Document> interpolatedArguments = new HashMap<>();
        FindIterable<Document> query;
        // if fieldName is related to a query...
        if(currentApp.getQueryMappings().containsKey(fieldName)){
            //retrieve the query mappings from app definition
            QueryMapping queryMapping = currentApp.getQueryMappingByName(fieldName);
            database = queryMapping.getTarget_db();
            collection = queryMapping.getTarget_collection();
            Map<String, Object> graphQLQueryArguments = dataFetchingEnvironment.getArguments();
            interpolatedArguments = queryMapping.interpolate(graphQLQueryArguments);
            if (interpolatedArguments.containsKey("filter")){
                filter = interpolatedArguments.get("filter");
            }
            else{
                filter = new Document();
            }
        }
        // else, if fieldName is related to an association
        else if(currentApp.getAssociationMappingByType(dataFetchingEnvironment.getFieldType().toString()).containsKey(fieldName)) {
            // retrieve association mappings from app definition
            AssociationMapping associationMapping =
                    currentApp.getAssociationMappingByType(dataFetchingEnvironment.getFieldType().toString())
                            .get(fieldName);
            database = associationMapping.getTarget_db();
            collection = associationMapping.getTarget_collection();
            filter = new Document();
            // in order to find correct data, the ref_field in target collection must be equal to the value of
            // the foreign key
            filter.put(associationMapping.getRef_field(), ((Document) dataFetchingEnvironment.getSource()).get(associationMapping.getKey()));
        }
        else{
            return  null;
        }

        query = mongoClient.getDatabase(database)
                .getCollection(collection)
                .find(filter);

        if(!interpolatedArguments.isEmpty()){
            if (interpolatedArguments.containsKey("sort")){
                query = query.sort(interpolatedArguments.get("sort"));
            }

            if (interpolatedArguments.containsKey("skip")){
                query = query.skip((int) interpolatedArguments.get("skip").get("skip"));
            }

            if (interpolatedArguments.containsKey("limit")){
                query = query.limit((int) interpolatedArguments.get("limit").get("limit"));
            }
        }

        List<String> projField = new LinkedList<String>();
        for (SelectedField field: dataFetchingEnvironment.getSelectionSet().getFields()) {
            projField.add(field.getQualifiedName());
        }
        ArrayList<Document> result = new ArrayList<Document>();

        query.projection(fields(include(projField))).into(result);

        return result;

    }
}
