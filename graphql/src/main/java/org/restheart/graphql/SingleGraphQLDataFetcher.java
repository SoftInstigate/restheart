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
        String fieldName = dataFetchingEnvironment.getField().getName();
        String database;
        String collection;
        Document filter;
        FindIterable<Document> query;
        GraphQLObjectType parentType = (GraphQLObjectType) dataFetchingEnvironment.getParentType();
        // if fieldName is related to a query...
        if(currentApp.getQueryMappings().containsKey(fieldName)){
            //retrieve the query mappings from app definition
            QueryMapping queryMapping = currentApp.getQueryMappingByName(fieldName);
            database = queryMapping.getTarget_db();
            collection = queryMapping.getTarget_collection();
            Map<String, Object> graphQLQueryArguments = dataFetchingEnvironment.getArguments();
            Map<String, Document> interpolatedArguments = queryMapping.interpolate(graphQLQueryArguments);
            if (interpolatedArguments.containsKey("filter")){
                filter = interpolatedArguments.get("filter");
            }
            else{
                filter = new Document();
            }
        }
        // else, if fieldName is related to an association
        else if(currentApp.getAssociationMappingByType(((GraphQLObjectType) dataFetchingEnvironment.getParentType()).getName()).containsKey(fieldName)) {
            // retrieve association mappings from app definition
            AssociationMapping associationMapping =
                    currentApp.getAssociationMappingByType(((GraphQLObjectType) dataFetchingEnvironment.getParentType()).getName())
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

        /**
        List<String> projField = new LinkedList<String>();
        for (SelectedField field: dataFetchingEnvironment.getSelectionSet().getFields()) {
            projField.add(field.getQualifiedName());
        }

        return query.projection(fields(include(projField))).first();
         **/

        return query.first();
    }


}

