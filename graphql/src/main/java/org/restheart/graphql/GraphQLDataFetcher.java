package org.restheart.graphql;

import com.mongodb.MongoClient;
import com.mongodb.client.FindIterable;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLObjectType;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.mongodb.db.MongoClientSingleton;
import org.restheart.utils.JsonUtils;

import java.util.Map;

public class GraphQLDataFetcher implements DataFetcher<BsonValue> {

    private static GraphQLApp currentApp;
    private static GraphQLDataFetcher instance = null;


    public static GraphQLDataFetcher getInstance(){
        if (instance ==null){
            instance = new GraphQLDataFetcher();
        }
        return instance;
    }


    private GraphQLDataFetcher() { }


    @Override
    public BsonValue get(DataFetchingEnvironment dataFetchingEnvironment) throws Exception {

        MongoClient mongoClient = MongoClientSingleton.getInstance().getClient();
        String typeName = ((GraphQLObjectType) dataFetchingEnvironment.getParentType()).getName();
        String fieldName = dataFetchingEnvironment.getField().getName();
        if(currentApp.getQueryMappings().containsKey(typeName)){
            Map<String, QueryMapping> queryMappings = currentApp.getQueryMappingByType(typeName);
            if ( queryMappings.containsKey(fieldName)) {
                QueryMapping mapping = queryMappings.get(fieldName);
                BsonDocument parentDocument = dataFetchingEnvironment.getSource();
                BsonDocument interpolatedArguments = mapping.interpolate(
                        JsonUtils.toBsonDocument(dataFetchingEnvironment.getArguments()),
                        parentDocument
                );

                FindIterable<BsonValue> query = mongoClient.getDatabase(mapping.getTarget_db())
                        .getCollection(mapping.getTarget_collection(), BsonValue.class)
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

                if (isMultiple){
                    BsonArray results = new BsonArray();
                    query.into(results);
                    return results;
                }
                else return query.first();

            }
        }
        return null;
    }

    public static void setCurrentApp(GraphQLApp app){
        currentApp = app;
    }


}
