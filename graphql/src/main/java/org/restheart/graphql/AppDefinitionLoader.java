package org.restheart.graphql;

import com.mongodb.MongoClient;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.*;
import org.bson.BsonDocument;
import org.bson.Document;
import org.restheart.mongodb.db.MongoClientSingleton;
import java.util.HashMap;
import java.util.Map;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

public class AppDefinitionLoader {

    private static final String APP_NAME_FIELD = "descriptor";
    private static final String MAPPINGS_FIELD = "mappings";
    private static final String DB_FIELD = "db";
    private static final String COLL_FIELD = "collection";
    private static final String MULTI_FIELD = "multiple";
    private static final String FILTER_FIELD = "filter";
    private static final String SORT_FIELD = "sort";
    private static final String SKIP_FIELD = "skip";
    private static final String LIMIT_FIELD = "limit";
    private static final String FIRST_FIELD = "first";
    private static final String SCHEMA_FIELD = "schema";

    private static String appDB;
    private static String appCollection;

    public static void setup(String _db, String _collection){
        appDB = _db;
        appCollection = _collection;
    }
    public static GraphQLApp loadAppDefinition(String appName){

        MongoClient mongoClient = MongoClientSingleton.getInstance().getClient();

        GraphQLApp newApp = new GraphQLApp(appName);
        BsonDocument appDesc = mongoClient.getDatabase(appDB)
                .getCollection(appCollection, BsonDocument.class).find(new Document(APP_NAME_FIELD, appName)).first();

        Map<String, Map<String,QueryMapping>> mappings = new HashMap<>();

        BsonDocument mappingsData = (BsonDocument) appDesc.get(MAPPINGS_FIELD);

        for (String type: mappingsData.keySet()){
            Map<String, QueryMapping> typeMappings = new HashMap<>();
            BsonDocument typeMapping = (BsonDocument) mappingsData.get(type);
            for (String name: typeMapping.keySet()){
                BsonDocument mapping = (BsonDocument) typeMapping.get(name);
                typeMappings.put(name, new QueryMapping.Builder(type,name,
                        mapping.getString(DB_FIELD).asString().getValue(),
                        mapping.getString(COLL_FIELD).asString().getValue(),
                        mapping.getBoolean(MULTI_FIELD).asBoolean().getValue())
                        .filter((BsonDocument) mapping.get(FILTER_FIELD))
                        .sort((BsonDocument) mapping.get(SORT_FIELD))
                        .skip((BsonDocument) mapping.get(SKIP_FIELD))
                        .limit((BsonDocument) mapping.get(LIMIT_FIELD))
                        .first((BsonDocument) mapping.get(FIRST_FIELD))
                        .build());
            }
            mappings.put(type, typeMappings);
        }
        newApp.setQueryMappings(mappings);
        GraphQLSchema graphQLSchema = buildSchema(appDesc.getString(SCHEMA_FIELD).asString().getValue(), newApp);
        newApp.setSchema(graphQLSchema);
        return newApp;
    }


    private static GraphQLSchema buildSchema(String sdl, GraphQLApp app){
        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(sdl);
        RuntimeWiring runtimeWiring = buildWiring(app);
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        return schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);
    }

    private static RuntimeWiring buildWiring(GraphQLApp app){

        Map<String, Map<String, QueryMapping>> queries = app.getQueryMappings();
        if (queries.size() > 0) {
            RuntimeWiring.Builder runWire = RuntimeWiring.newRuntimeWiring()
                    .scalar(BSONScalar.GraphQLBsonObjectId)
                    .scalar(BSONScalar.GraphQLBsonString)
                    .scalar(BSONScalar.GraphQLBsonDocument)
                    .scalar(BSONScalar.GraphQLBsonInt32)
                    .scalar(BSONScalar.GraphQLBsonInt64);
            for (String type: queries.keySet()){
                TypeRuntimeWiring.Builder queryTypeBuilder = newTypeWiring(type);
                for (String queryName : queries.get(type).keySet()) {
                    boolean isMultiple = app.getQueryMappingByType(type).get(queryName).isMultiple();
                    if (isMultiple) {
                        queryTypeBuilder.dataFetcher(queryName, MultipleGraphQLDataFetcher.getInstance());
                    } else {
                        queryTypeBuilder.dataFetcher(queryName, SingleGraphQLDataFetcher.getInstance());
                    }
                }
                runWire.type(queryTypeBuilder);
            }
            return runWire.build();
        }
        else return null;
    }


}
