package org.restheart.graphql;

import com.mongodb.MongoClient;
import com.mongodb.client.FindIterable;
import graphql.schema.*;
import org.bson.BsonDocument;
import org.bson.Document;
import org.restheart.exchange.InvalidMetadataException;
import org.restheart.exchange.QueryVariableNotBoundException;
import org.restheart.mongodb.db.MongoClientSingleton;
import org.restheart.utils.JsonUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import static com.mongodb.client.model.Projections.fields;
import static com.mongodb.client.model.Projections.include;

public class SingleGraphQLDataFetcher implements DataFetcher<BsonDocument> {

    private static SingleGraphQLDataFetcher instance = null;

    public static SingleGraphQLDataFetcher getInstance(){
        if (instance ==null){
            instance = new SingleGraphQLDataFetcher();
        }
        return instance;
    }
    private SingleGraphQLDataFetcher() { }


    private static GraphQLApp currentApp = null;


    public static GraphQLApp getCurrentApp() {
        return currentApp;
    }

    public static void setCurrentApp(GraphQLApp app) {
        currentApp = app;
    }


    @Override
    public BsonDocument get(DataFetchingEnvironment dataFetchingEnvironment) throws InvocationTargetException,
            QueryVariableNotBoundException, InvalidMetadataException, NoSuchMethodException, IllegalAccessException {

        MongoClient mongoClient = MongoClientSingleton.getInstance().getClient();

        String typeName = ((GraphQLObjectType) dataFetchingEnvironment.getParentType()).getName();
        String fieldName = dataFetchingEnvironment.getField().getName();

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

