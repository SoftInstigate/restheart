package org.restheart.graphql;

import com.mongodb.MongoClient;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.bson.Document;
import org.restheart.exchange.InvalidMetadataException;
import org.restheart.exchange.QueryVariableNotBoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.mongodb.client.model.Projections.fields;
import static com.mongodb.client.model.Projections.include;

public class GraphQLDataFetcher implements DataFetcher<List<Document>>{
    private MongoClient mclient;
    private GraphQLApp app;

    public GraphQLDataFetcher(MongoClient mclient, GraphQLApp app) {
        this.mclient = mclient;
        this.app = app;
    }

    @Override
    public List<Document> get(DataFetchingEnvironment env) {
        String queryName = env.getField().getName();
        try{
            // try to retrieve query with name queryName from app queries
            Query queryDef = this.app.getQueryByName(queryName);
            // get query's filter
            Document filter = queryDef.getFilter();
            // get graphql query's arguments
            Map<String, Object> graphQLQueryArguments = env.getArguments();
            // interpolate filter of query definition with graphql query
            Document intQuery = this.interpolate(filter, graphQLQueryArguments);

            // build mongodb query
            var query = this.mclient.getDatabase(queryDef.getDb())
                    .getCollection(queryDef.getCollection(), Document.class)
                    .find();
            /**
            if(queryDef.isFirst()){
                query = query.first();
            }

            if(queryDef.getSort() != null){
                query = query.sort(queryDef.getSort());
            }

            if(queryDef.getSkip() != null){
                query = query.skip(queryDef.getSkip());
            }

            if(queryDef.getLimit() != null){
                query = query.limit(queryDef.getLimit());
            }

            // find projection fields
            List<String> projField = new LinkedList<String>();
            for (SelectedField field: env.getSelectionSet().getFields()) {
                projField.add(field.getQualifiedName());
            }
            // do the projection
            query.projection(fields(include(projField)));
            **/

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


    public Document interpolate(Document obj, Map<String, Object> arguments) throws InvalidMetadataException, QueryVariableNotBoundException {

        if(obj == null){
            return  null;
        }

        if (obj.size() == 1 && obj.get("$arg") !=null){
            String varName = (String) obj.get("$arg");


            if(arguments == null || arguments.get(varName) == null){
                throw new QueryVariableNotBoundException("variable " + varName + " not bound");
            }

            Document value = new Document();
            value.put(varName, arguments.get(varName));

            return value;
        } else {
            Document resQuery = new Document();

            for (String key : obj.keySet()) {
                resQuery.put(key, interpolate((Document) obj.get(key), arguments));
            }
            return resQuery;
        }
    }
}

