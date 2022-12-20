/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2022 SoftInstigate
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
import com.mongodb.client.MongoClient;
import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentationOptions;
import io.undertow.server.HttpServerExchange;
import org.bson.BsonValue;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.restheart.configuration.ConfigurationException;
import org.restheart.exchange.BadRequestException;
import org.restheart.exchange.ExchangeKeys;
import org.restheart.exchange.MongoResponse;
import org.restheart.graphql.cache.AppDefinitionLoader;
import org.restheart.graphql.cache.AppDefinitionLoadingCache;
import org.restheart.graphql.datafetchers.GraphQLDataFetcher;
import org.restheart.graphql.dataloaders.AggregationBatchLoader;
import org.restheart.graphql.dataloaders.QueryBatchLoader;
import org.restheart.exchange.GraphQLRequest;
import org.restheart.graphql.models.AggregationMapping;
import org.restheart.graphql.models.GraphQLApp;
import org.restheart.graphql.models.QueryMapping;
import org.restheart.graphql.models.TypeMapping;
import org.restheart.graphql.models.builder.AppBuilder;
import org.restheart.graphql.scalars.bsonCoercing.CoercingUtils;
import org.restheart.plugins.*;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.BsonUtils;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RegisterPlugin(name = "graphql", description = "Service that handles GraphQL requests", secure = true, enabledByDefault = true, defaultURI = "/graphql")

public class GraphQLService implements Service<GraphQLRequest, MongoResponse> {
    public static final String DEFAULT_APP_DEF_DB = "restheart";
    public static final String DEFAULT_APP_DEF_COLLECTION = "gqlapps";
    public static final Boolean DEFAULT_VERBOSE = false;
    public static final int DEFAULT_DEFAULT_LIMIT = 100;
    public static final int DEFAULT_MAX_LIMIT = 1_000;

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphQLService.class);

    private GraphQL gql;
    private String db = DEFAULT_APP_DEF_DB;
    private String collection = DEFAULT_APP_DEF_COLLECTION;
    private Boolean verbose = DEFAULT_VERBOSE;
    private int defaultLimit = DEFAULT_DEFAULT_LIMIT;
    private int maxLimit = DEFAULT_MAX_LIMIT;

    @Inject("mclient")
    private MongoClient mclient;

    @Inject("config")
    private Map<String, Object> config;

    @OnInit
    public void init()throws ConfigurationException, NoSuchFieldException, IllegalAccessException {
        CoercingUtils.replaceBuiltInCoercing();

        this.db = argOrDefault(config, "db", DEFAULT_APP_DEF_DB);
        this.collection = argOrDefault(config, "collection", DEFAULT_APP_DEF_COLLECTION);
        this.verbose = argOrDefault(config, "verbose", DEFAULT_VERBOSE);
        this.verbose = argOrDefault(config, "verbose", DEFAULT_VERBOSE);

        this.defaultLimit = argOrDefault(config, "default-limit", 100);
        this.maxLimit = argOrDefault(config, "max-limit", 1000);

        AppDefinitionLoadingCache.setTTL(argOrDefault(config, "app-def-cache-ttl", 1_000));

        QueryBatchLoader.setMongoClient(mclient);
        AggregationBatchLoader.setMongoClient(mclient);
        GraphQLDataFetcher.setMongoClient(mclient);
        AppDefinitionLoader.setup(db, collection, mclient);
        AppBuilder.setDefaultLimit(this.defaultLimit);
        AppBuilder.setMaxLimit(this.maxLimit);
        QueryMapping.setMaxLimit(this.maxLimit);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handle(GraphQLRequest request, MongoResponse response) throws Exception {
        if (request.isOptions()) {
            handleOptions(request);
            return;
        }

        var graphQLApp = gqlApp(appURI(request.getExchange()));

        var dataLoaderRegistry = setDataloaderRegistry(graphQLApp.objectsMappings());

        if (request.getQuery() == null) {
            response.setInError(HttpStatus.SC_BAD_REQUEST, "query cannot be null");
            return;
        }

        var inputBuilder = ExecutionInput.newExecutionInput()
            .query(request.getQuery())
            .dataLoaderRegistry(dataLoaderRegistry);

        inputBuilder.operationName(request.getOperationName());
        if (request.hasVariables()) {
            inputBuilder.variables((new Gson()).fromJson(request.getVariables(), Map.class));
        }

        var dispatcherInstrumentationOptions = DataLoaderDispatcherInstrumentationOptions.newOptions();

        if (this.verbose) {
            dispatcherInstrumentationOptions = dispatcherInstrumentationOptions.includeStatistics(true);
        }

        var dispatcherInstrumentation = new DataLoaderDispatcherInstrumentation(dispatcherInstrumentationOptions);

        this.gql = GraphQL.newGraphQL(graphQLApp.getExecutableSchema()).instrumentation(dispatcherInstrumentation).build();

        var result = this.gql.execute(inputBuilder.build());

        if (this.verbose) {
            logDataLoadersStatistics(dataLoaderRegistry);
        }

        if (!result.getErrors().isEmpty()) {
            response.setInError(400, "Bad Request");
        }
        response.setContent(BsonUtils.toBsonDocument(result.toSpecification()));
    }

    private void logDataLoadersStatistics(DataLoaderRegistry dataLoaderRegistry) {
        LOGGER.debug("##### DATALOADERS STATISTICS #####");
        dataLoaderRegistry.getKeys().forEach(key -> LOGGER.debug(key.toUpperCase() + ": " + dataLoaderRegistry.getDataLoader(key).getStatistics()));
        LOGGER.debug("##################################");
    }

    private DataLoaderRegistry setDataloaderRegistry(Map<String, TypeMapping> mappings) {
        var dataLoaderRegistry = new DataLoaderRegistry();

        mappings.forEach((type, typeMapping) -> {
            typeMapping.getFieldMappingMap().forEach((field, fieldMapping) -> {
                if (fieldMapping instanceof QueryMapping) {
                    DataLoader<BsonValue, BsonValue> dataLoader = ((QueryMapping) fieldMapping).getDataloader();
                    if (dataLoader != null) {
                        dataLoaderRegistry.register(type + "_" + field, dataLoader);
                    }
                }
                // register dataLoaders for Aggregation Mapping
                if (fieldMapping instanceof AggregationMapping) {
                    DataLoader<BsonValue, BsonValue> dataLoader = ((AggregationMapping) fieldMapping).getDataloader();
                    if (dataLoader != null) {
                        dataLoaderRegistry.register(type + "_" + field, dataLoader);
                    }
                }
            });
        });

        return dataLoaderRegistry;

    }

    @Override
    public Consumer<HttpServerExchange> requestInitializer() {
        return e -> {
            try {
                if (e.getRequestMethod().equalToString(ExchangeKeys.METHOD.POST.name()) || e.getRequestMethod().equalToString(ExchangeKeys.METHOD.OPTIONS.name())) {
                    var appURI = appURI(e);
                    gqlApp(appURI); // throws GraphQLAppDefNotFoundException when uri is not bound to an app definition
                    GraphQLRequest.init(e, appURI);
                } else {
                    throw new BadRequestException(HttpStatus.SC_METHOD_NOT_ALLOWED);
                }
            } catch (GraphQLAppDefNotFoundException notFoundException) {
                LOGGER.error(notFoundException.getMessage());
                throw new BadRequestException(HttpStatus.SC_NOT_FOUND);
            } catch (GraphQLIllegalAppDefinitionException illegalException) {
                LOGGER.error(illegalException.getMessage());
                throw new BadRequestException(illegalException.getMessage(), HttpStatus.SC_BAD_REQUEST);
            }
        };
    }

    private String appURI(HttpServerExchange exchange) {
        var splitPath = exchange.getRequestPath().split("/");
        return String.join("/", Arrays.copyOfRange(splitPath, 2, splitPath.length));
    }

    private GraphQLApp gqlApp(String appURI) throws GraphQLAppDefNotFoundException, GraphQLIllegalAppDefinitionException {
        return AppDefinitionLoadingCache.getInstance().get(appURI);
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
