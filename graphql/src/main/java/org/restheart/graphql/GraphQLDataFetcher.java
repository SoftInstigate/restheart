package org.restheart.graphql;

import com.mongodb.MongoClient;
import com.mongodb.client.FindIterable;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLObjectType;
import org.bson.*;
import org.restheart.graphql.models.GraphQLApp;
import org.restheart.graphql.models.QueryMapping;
import org.restheart.utils.JsonUtils;

import java.util.Map;

public class GraphQLDataFetcher implements DataFetcher<BsonValue> {

    private static GraphQLDataFetcher instance = null;
    private  GraphQLApp currentApp;
    private  MongoClient mongoClient;


    public static GraphQLDataFetcher getInstance(){
        if (instance ==null){
            instance = new GraphQLDataFetcher();
        }
        return instance;
    }


    private GraphQLDataFetcher() { }



    @Override
    public BsonValue get(DataFetchingEnvironment dataFetchingEnvironment) throws Exception {

        String typeName = ((GraphQLObjectType) dataFetchingEnvironment.getParentType()).getName();
        String fieldName = dataFetchingEnvironment.getField().getName();

        if(currentApp.getMappings().containsKey(typeName)){
            Map<String, QueryMapping> mappings = currentApp.getMappings().get(typeName);
            if (mappings.containsKey(fieldName)) {
                QueryMapping mapping = mappings.get(fieldName);
                BsonDocument parentDocument = dataFetchingEnvironment.getSource();
                BsonDocument interpolatedArguments = mapping.interpolate(
                        JsonUtils.toBsonDocument(dataFetchingEnvironment.getArguments()),
                        parentDocument
                );

                FindIterable<BsonValue> query = mongoClient.getDatabase(mapping.getDb())
                        .getCollection(mapping.getCollection(), BsonValue.class)
                        .find(
                                interpolatedArguments.containsKey("find") ?
                                        (BsonDocument) interpolatedArguments.get("find"): new BsonDocument()
                        );

                if (interpolatedArguments.containsKey("sort")){
                    query = query.sort(((BsonDocument) interpolatedArguments.get("sort")));
                }

                if (interpolatedArguments.containsKey("skip")){
                    query = query.skip(interpolatedArguments.get("skip").asInt32().getValue());
                }

                if (interpolatedArguments.containsKey("limit")){
                    query = query.limit(interpolatedArguments.get("limit").asInt32().getValue());
                }

                boolean isMultiple = dataFetchingEnvironment.getFieldDefinition().getType() instanceof GraphQLList;

                BsonValue queryResult;
                if (isMultiple) {
                   BsonArray results = new BsonArray();
                   query.into(results);
                   queryResult = results;
                }
                else queryResult = query.first();

                return queryResult;

            }
            else{
                throw new NullPointerException(
                        "Mappings of GraphQL field with name " + fieldName + " not found in type " + typeName + "!"
                );
            }
        }
        else{
            throw new NullPointerException(
                    "Mappings of GraphQL type " + typeName + " not found!"
            );
        }
    }

    public  void setCurrentApp(GraphQLApp app){
        currentApp = app;
    }

    public  void setMongoClient(MongoClient mClient){
        mongoClient = mClient;
    }

}
