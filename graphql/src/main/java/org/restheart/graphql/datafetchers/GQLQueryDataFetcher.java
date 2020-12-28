package org.restheart.graphql.datafetchers;

import com.mongodb.client.FindIterable;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLList;
import org.bson.*;
import org.restheart.graphql.datafetchers.GraphQLDataFetcher;
import org.restheart.graphql.models.QueryMapping;

public class GQLQueryDataFetcher extends GraphQLDataFetcher {

    private static final String SORT_FIELD = "sort";
    private static final String FIND_FIELD = "find";
    private static final String LIMIT_FIELD = "limit";
    private static final String SKIP_FIELD = "skip";

    public GQLQueryDataFetcher(QueryMapping queryMapping) {
        super(queryMapping);
    }

    @Override
    public BsonValue get(DataFetchingEnvironment dataFetchingEnvironment) throws Exception {

        QueryMapping queryMapping = (QueryMapping) this.fieldMapping;

        BsonDocument int_args = queryMapping.interpolateArgs(dataFetchingEnvironment);

        FindIterable<BsonValue> query = mongoClient.getDatabase(queryMapping.getDb())
                .getCollection(queryMapping.getCollection(), BsonValue.class)
                .find(
                        int_args.containsKey(FIND_FIELD) ?
                                 int_args.get(FIND_FIELD).asDocument(): new BsonDocument()
                );

        if (int_args.containsKey(SORT_FIELD) && int_args.get(SORT_FIELD) != null){
            query = query.sort(int_args.get(SORT_FIELD).asDocument());
        }

        if (int_args.containsKey(SKIP_FIELD) && int_args.get(SKIP_FIELD) != null){
            query = query.skip(int_args.get(SKIP_FIELD).asInt32().getValue());
        }

        if (int_args.containsKey(LIMIT_FIELD) && int_args.get(LIMIT_FIELD) != null){
            query = query.limit(int_args.get(LIMIT_FIELD).asInt32().getValue());
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
}
