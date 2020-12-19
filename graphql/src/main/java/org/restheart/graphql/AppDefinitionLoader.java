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
import org.restheart.graphql.models.Mapping;
import java.util.Map;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

public class AppDefinitionLoader {

    private static final String APP_NAME_FIELD = "descriptor";

    private static MongoClient mongoClient;
    private static String appDB;
    private static String appCollection;

    public static void setup(String _db, String _collection, MongoClient mclient){
        appDB = _db;
        appCollection = _collection;
        mongoClient = mclient;
    }

    public static GraphQLApp loadAppDefinition(String appName) throws NoSuchFieldException, IllegalAccessException {

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
        else {
            throw new NullPointerException(
                    "Configuration for " + appName +  " application not found!"
            );
        }


        return newApp;
    }


    private static GraphQLSchema buildSchema(String sdl, GraphQLApp app) throws IllegalAccessException {
        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(sdl);
        RuntimeWiring runtimeWiring = buildWiring(app);
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        return schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);
    }

    private static RuntimeWiring buildWiring(GraphQLApp app) throws IllegalAccessException {

        Map<String, Map<String, Mapping>> mappings = app.getMappings();
        if (mappings.size() > 0) {
            RuntimeWiring.Builder runWire = RuntimeWiring.newRuntimeWiring();
            addBSONScalarsToWiring(runWire);
            GraphQLDataFetcher dataFetcher = GraphQLDataFetcher.getInstance();
            dataFetcher.setMongoClient(mongoClient);
            for (String type: mappings.keySet()){
                TypeRuntimeWiring.Builder typeWiringBuilder = newTypeWiring(type);
                Map<String, Mapping> typeMappings = mappings.get(type);
                for (String fieldName : typeMappings.keySet()) {
                    String alias =typeMappings.get(fieldName).getAlias();
                    if ( alias != null){
                        typeWiringBuilder.dataFetcher(fieldName, PropertyDataFetcher.fetching(alias));
                    }
                    else {
                        typeWiringBuilder.dataFetcher(fieldName, dataFetcher);
                    }
                }
                runWire.type(typeWiringBuilder);
                dataFetcher.setCurrentApp(app);
            }
            return runWire.build();
        }
        else return null;
    }

    private static void addBSONScalarsToWiring(RuntimeWiring.Builder runtimeBuilder) throws IllegalAccessException {
        Map<String, GraphQLScalarType> bsonScalars = BsonScalars.getBsonScalars();

        bsonScalars.forEach(((s, graphQLScalarType) -> {
            runtimeBuilder.scalar(graphQLScalarType);
        }));
    }

    private static String addBSONScalarsToSchema(String schemaWithoutBsonScalars) throws IllegalAccessException {
        return BsonScalars.getBsonScalarHeader() + schemaWithoutBsonScalars;
    }


}
