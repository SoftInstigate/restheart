package org.restheart.graphql;

import com.mongodb.MongoClient;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.Document;
import org.restheart.exchange.InvalidMetadataException;
import org.restheart.exchange.QueryVariableNotBoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GraphQLDataFetcher implements DataFetcher<List<Document>>{
    private MongoClient mclient;
    private GraphQLApp app;

    public GraphQLDataFetcher(MongoClient mclient, GraphQLApp app) {
        this.mclient = mclient;
        this.app = app;
    }

    public MongoClient getMclient() {
        return mclient;
    }

    public void setMclient(MongoClient mclient) {
        this.mclient = mclient;
    }

    public GraphQLApp getApp() {
        return app;
    }

    public void setApp(GraphQLApp app) {
        this.app = app;
    }

    @Override
    public List<Document> get(DataFetchingEnvironment env) throws NullPointerException {
        String queryName = env.getField().getName();
        try{
            Query queryDef = this.app.getQueryByName(queryName);
            BsonDocument filter = queryDef.getFilter();
            Map<String, Object> graphQLQueryArguments = env.getArguments();
            BsonDocument intQuery = this.interpolate(filter, graphQLQueryArguments).asDocument();

            var query = this.mclient.getDatabase(queryDef.getDb())
                    .getCollection(queryDef.getCollection(), Document.class)
                    .find(intQuery);

            if(queryDef.isFirst()){
                query.first();
            }

            if(queryDef.getSort() != null){
                query.sort(queryDef.getSort());
            }

            if(queryDef.getSkip() != null){
                query.skip(queryDef.getSkip());
            }

            if(queryDef.getLimit() != null){
                query.limit(queryDef.getLimit());
            }

            ArrayList<Document> result = new ArrayList<Document>();

            query.into(result);

            return result;

        }catch (NullPointerException e){
            System.out.println("No query found with name "+ queryName );
            return null;
        }catch (InvalidMetadataException e) {
            e.printStackTrace();
            return null;
        }catch (QueryVariableNotBoundException e) {
            e.printStackTrace();
            return null;
        }
    }


    public BsonValue interpolate(BsonValue obj, Map<String, Object> arguments) throws InvalidMetadataException, QueryVariableNotBoundException {

        if(obj == null){
            return  null;
        }

        BsonDocument _obj = obj.asDocument();

        if (_obj.size() == 1 && _obj.get("$arg") !=null){
            BsonValue varName = _obj.get("$arg");

            if(!(varName.isString())){
                throw new InvalidMetadataException("wrong variable name " + varName.toString());
            }

            if(arguments == null || arguments.get(varName.asString().getValue()) == null){
                throw new QueryVariableNotBoundException("variable " + varName.asString().getValue() + " not bound");
            }

            BsonValue value = (BsonValue) arguments.get(varName.asString().getValue());

            return value;
        } else {
            BsonDocument resQuery = new BsonDocument();

            for (String key : _obj.keySet()) {
                resQuery.put(key, interpolate(_obj.get(key), arguments));
            }
            return resQuery;
        }
    }
}

