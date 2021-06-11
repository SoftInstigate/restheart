/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2021 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.graphql;

import com.google.gson.Gson;
import com.mongodb.MongoClient;
import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentationOptions;
import io.undertow.server.HttpServerExchange;
import org.bson.BsonValue;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.restheart.ConfigurationException;
import org.restheart.exchange.BadRequestException;
import org.restheart.exchange.ExchangeKeys;
import org.restheart.exchange.MongoResponse;
import org.restheart.graphql.cache.AppDefinitionLoader;
import org.restheart.graphql.cache.AppDefinitionLoadingCache;
import org.restheart.graphql.datafetchers.GraphQLDataFetcher;
import org.restheart.graphql.dataloaders.QueryBatchLoader;
import org.restheart.graphql.exchange.GraphQLRequest;
import org.restheart.graphql.models.GraphQLApp;
import org.restheart.graphql.models.QueryMapping;
import org.restheart.graphql.models.TypeMapping;
import org.restheart.graphql.scalars.bsonCoercing.CoercingUtils;
import org.restheart.plugins.*;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.BsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;


@RegisterPlugin(name= "graphql",
                description = "Service that handles GraphQL requests",
                secure = true,
                enabledByDefault = true,
                defaultURI = "/graphql")

public class GraphQLService implements Service<GraphQLRequest, MongoResponse> {
    public static final String DEFAULT_APP_DEF_DB = "restheart";
    public static final String DEFAULT_APP_DEF_COLLECTION = "gqlapps";
    public static final Boolean DEFAULT_VERBOSE = false;



    private static final Logger LOGGER = LoggerFactory.getLogger(GraphQLService.class);

    private GraphQL gql;
    private MongoClient mongoClient = null;
    private String db = DEFAULT_APP_DEF_DB;
    private String collection = DEFAULT_APP_DEF_COLLECTION;
    private Boolean verbose = DEFAULT_VERBOSE;

    @InjectConfiguration
    public void initConf(Map<String, Object> args) throws ConfigurationException, NoSuchFieldException, IllegalAccessException {
        CoercingUtils.replaceBuiltInCoercing();

        if (args != null) {
            try {
                this.db = ConfigurablePlugin.argValue(args, "db");
                this.collection = ConfigurablePlugin.argValue(args, "collection");
                this.verbose = ConfigurablePlugin.argValue(args, "verbose");
            } catch(ConfigurationException ex) {
                // nothing to do, using default values
            }
        }

        if(mongoClient != null){
            QueryBatchLoader.setMongoClient(mongoClient);
            GraphQLDataFetcher.setMongoClient(mongoClient);
            AppDefinitionLoader.setup(db, collection, mongoClient);
        }
    }

    @InjectMongoClient
    public void initMongoClient(MongoClient mClient){
        this.mongoClient = mClient;
        if (db!= null && collection != null){
            QueryBatchLoader.setMongoClient(mongoClient);
            GraphQLDataFetcher.setMongoClient(mongoClient);
            AppDefinitionLoader.setup(db, collection, mongoClient);
        }
    }


    @Override
    @SuppressWarnings("unchecked")
    public void handle(GraphQLRequest request, MongoResponse response) throws Exception {
        if (request.isOptions()) {
            handleOptions(request);
            return;
        }

        GraphQLApp graphQLApp = request.getAppDefinition();

        DataLoaderRegistry dataLoaderRegistry = setDataloaderRegistry(graphQLApp.getMappings());

        if (request.getQuery() == null) {
            response.setInError(HttpStatus.SC_BAD_REQUEST, "query cannot be null");
            return;
        }

        ExecutionInput.Builder inputBuilder = ExecutionInput.newExecutionInput()
                .query(request.getQuery())
                .dataLoaderRegistry(dataLoaderRegistry);

        inputBuilder.operationName(request.getOperationName());
        if (request.hasVariables()){
            inputBuilder.variables((new Gson()).fromJson(request.getVariables(), Map.class));
        }

        DataLoaderDispatcherInstrumentationOptions dispatcherInstrumentationOptions
                = DataLoaderDispatcherInstrumentationOptions.newOptions();

        if(this.verbose){
            dispatcherInstrumentationOptions = dispatcherInstrumentationOptions.includeStatistics(true);
        }

        DataLoaderDispatcherInstrumentation dispatcherInstrumentation
                = new DataLoaderDispatcherInstrumentation(dispatcherInstrumentationOptions);

        this.gql = GraphQL.newGraphQL(graphQLApp.getExecutableSchema())
                .instrumentation(dispatcherInstrumentation).build();

        var result = this.gql.execute(inputBuilder.build());

        if(this.verbose){
            logDataLoadersStatistics(dataLoaderRegistry);
        }

        if (!result.getErrors().isEmpty()){
            response.setInError(400, "Bad Request");
        }
        response.setContent(BsonUtils.toBsonDocument(result.toSpecification()));
    }


    private void logDataLoadersStatistics(DataLoaderRegistry dataLoaderRegistry){
        LOGGER.debug("##### DATALOADERS STATISTICS #####");
        dataLoaderRegistry.getKeys().forEach(key -> {
            LOGGER.debug(key.toUpperCase() + ": " + dataLoaderRegistry.getDataLoader(key).getStatistics());
        });
        LOGGER.debug("##################################");
    }

    private DataLoaderRegistry setDataloaderRegistry(Map<String, TypeMapping> mappings){

        DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry();

        mappings.forEach((type, typeMapping) -> {
            typeMapping.getFieldMappingMap().forEach((field, fieldMapping) -> {
                if (fieldMapping instanceof QueryMapping){
                    DataLoader<BsonValue, BsonValue> dataLoader = ((QueryMapping) fieldMapping).getDataloader();
                    if (dataLoader != null){
                        dataLoaderRegistry.register(type + "_" + field, dataLoader);
                    }
                }
            } );
        });

        return dataLoaderRegistry;

    }


    @Override
    public Consumer<HttpServerExchange> requestInitializer() {
        return e -> {
            try {
                if (e.getRequestMethod().equalToString(ExchangeKeys.METHOD.POST.name())
                    || e.getRequestMethod().equalToString(ExchangeKeys.METHOD.OPTIONS.name())){
                    var cache = AppDefinitionLoadingCache.getInstance();
                    String[] splitPath = e.getRequestPath().split("/");
                    var appUri = String.join("/", Arrays.copyOfRange(splitPath, 2, splitPath.length));
                    var appDef = cache.get(appUri);
                    GraphQLRequest.init(e, appUri, appDef);
                } else {
                    throw new BadRequestException(HttpStatus.SC_METHOD_NOT_ALLOWED);
                }
            } catch (GraphQLAppDefNotFoundException notFoundException){
                LOGGER.error(notFoundException.getMessage());
                throw new BadRequestException(HttpStatus.SC_NOT_FOUND);
            } catch (GraphQLIllegalAppDefinitionException illegalException){
                LOGGER.error(illegalException.getMessage());
                throw new BadRequestException(illegalException.getMessage(), HttpStatus.SC_BAD_REQUEST);
            }
        };
    }

    @Override
    public Consumer<HttpServerExchange> responseInitializer() {
        return e -> MongoResponse.init(e);
    }

    @Override
    public Function<HttpServerExchange, GraphQLRequest> request() {
        return e -> GraphQLRequest.of(e);
    }

    @Override
    public Function<HttpServerExchange, MongoResponse> response() {
        return e -> MongoResponse.of(e);
    }
}
