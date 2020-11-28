package org.restheart.graphql;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientSettings;
import graphql.schema.*;
import graphql.schema.idl.*;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.restheart.graphql.models.GraphQLApp;
import org.restheart.graphql.models.QueryMapping;
import org.restheart.mongodb.db.MongoClientSingleton;

import java.util.Map;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

public class AppDefinitionLoader {

    private static final String APP_NAME_FIELD = "descriptor";

    private static final String BSON_SCALAR_SCHEMA = "scalar BsonTimestamp scalar BsonString " +
            "scalar ObjectId scalar BsonObject scalar BsonInt32 scalar BsonInt64 scalar BsonDouble " +
            "scalar BsonDecimal128 scalar BsonDate scalar BsonBoolean scalar BsonArray scalar BsonDocument";


    private static String appDB;
    private static String appCollection;

    public static void setup(String _db, String _collection){
        appDB = _db;
        appCollection = _collection;
    }
    public static GraphQLApp loadAppDefinition(String appName) throws NoSuchFieldException {

        MongoClient mongoClient = MongoClientSingleton.getInstance().getClient();
        CodecRegistry pojoCodecRegistry = fromProviders(PojoCodecProvider.builder().automatic(true).build());
        CodecRegistry codecRegistry = fromRegistries(MongoClientSettings.getDefaultCodecRegistry(),
                pojoCodecRegistry);


        GraphQLApp newApp = mongoClient.getDatabase(appDB).withCodecRegistry(codecRegistry)
                .getCollection(appCollection, GraphQLApp.class)
                .find(new BsonDocument(APP_NAME_FIELD, new BsonString(appName)))
                .first();

        if (newApp != null) {
            String schema = newApp.getSchema();
            String schemaWithBsonScalar = addBSONScalarsToSchema(schema);
            GraphQLSchema graphQLBuiltSchema = buildSchema(schemaWithBsonScalar, newApp);
            newApp.setBuiltSchema(graphQLBuiltSchema);
        }


        return newApp;
    }


    private static GraphQLSchema buildSchema(String sdl, GraphQLApp app) {
        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(sdl);
        RuntimeWiring runtimeWiring = buildWiring(app);
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        return schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);
    }

    private static RuntimeWiring buildWiring(GraphQLApp app){


        Map<String, Map<String, QueryMapping>> mappings = app.getMappings();
        if (mappings.size() > 0) {
            RuntimeWiring.Builder runWire = RuntimeWiring.newRuntimeWiring();
            addBSONScalarsToWiring(runWire);
            for (String type: mappings.keySet()){
                TypeRuntimeWiring.Builder queryTypeBuilder = newTypeWiring(type);
                Map<String, QueryMapping> typeMappings = mappings.get(type);
                for (String queryName : typeMappings.keySet()) {
                    queryTypeBuilder.dataFetcher(queryName, GraphQLDataFetcher.getInstance());
                }
                runWire.type(queryTypeBuilder);
                GraphQLDataFetcher.setCurrentApp(app);
            }
            return runWire.build();
        }
        else return null;
    }

    private static void addBSONScalarsToWiring(RuntimeWiring.Builder runtimeBuilder){
        runtimeBuilder.scalar(BSONScalar.GraphQLBsonObjectId)
                .scalar(BSONScalar.GraphQLBsonString)
                .scalar(BSONScalar.GraphQLBsonInt32)
                .scalar(BSONScalar.GraphQLBsonInt64)
                .scalar(BSONScalar.GraphQLBsonDouble)
                .scalar(BSONScalar.GraphQLBsonBoolean)
                .scalar(BSONScalar.GraphQLBsonDecimal128)
                .scalar(BSONScalar.GraphQLBsonDate)
                .scalar(BSONScalar.GraphQLBsonTimestamp)
                .scalar(BSONScalar.GraphQLBsonObject)
                .scalar(BSONScalar.GraphQLBsonArray)
                .scalar(BSONScalar.GraphQLBsonDocument);
    }


    private static String addBSONScalarsToSchema(String schemaWithoutBsonScalars){
        return BSON_SCALAR_SCHEMA + " " + schemaWithoutBsonScalars;
    }


}
