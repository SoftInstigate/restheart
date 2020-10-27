package org.restheart.graphql;

import com.mongodb.MongoClient;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.*;
import org.bson.Document;
import org.restheart.mongodb.db.MongoClientSingleton;
import org.restheart.plugins.InjectConfiguration;
import org.restheart.plugins.InjectMongoClient;

import java.util.HashMap;
import java.util.Map;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

public class AppDefinitionLoader {

    private static final String APP_NAME_FIELD = "descriptor";
    private static final String MAPPINGS_FIELD = "mappings";
    private static final String QUERY_NAME_FIELD = "name";
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
        Document appDesc = mongoClient.getDatabase(appDB)
                .getCollection(appCollection).find(new Document(APP_NAME_FIELD, appName)).first();

        Map<String, Map<String,QueryMapping>> mappings = new HashMap<>();

        for (String type: ((Document) appDesc.get(MAPPINGS_FIELD)).keySet()){
            Map<String, QueryMapping> typeMappings = new HashMap<>();
            for (Document mapping: ((Document) appDesc.get(MAPPINGS_FIELD)).getList(type, Document.class)){
                String name = mapping.getString(QUERY_NAME_FIELD);
                typeMappings.put(name, new QueryMapping.Builder(type,name, mapping.getString(DB_FIELD), mapping.getString(COLL_FIELD),
                        mapping.getBoolean(MULTI_FIELD))
                        .filter((Document) mapping.get(FILTER_FIELD))
                        .sort((Document) mapping.get(SORT_FIELD))
                        .skip((Document) mapping.get(SKIP_FIELD))
                        .limit((Document) mapping.get(LIMIT_FIELD))
                        .first((Document) mapping.get(FIRST_FIELD))
                        .build());
            }
            mappings.put(type, typeMappings);
        }

        newApp.setQueryMappings(mappings);
        GraphQLSchema graphQLSchema = buildSchema(appDesc.getString(SCHEMA_FIELD), newApp);
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
            RuntimeWiring.Builder runWire = RuntimeWiring.newRuntimeWiring();
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
