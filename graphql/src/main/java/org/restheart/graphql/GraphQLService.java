/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2023 SoftInstigate
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
import graphql.execution.UnknownOperationException;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentationOptions;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.language.OperationDefinition.Operation;
import graphql.parser.InvalidSyntaxException;
import graphql.parser.Parser;
import io.undertow.server.HttpServerExchange;
import org.bson.BsonValue;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.restheart.configuration.ConfigurationException;
import org.restheart.exchange.BadRequestException;
import org.restheart.exchange.ExchangeKeys;
import org.restheart.exchange.MongoResponse;
import org.restheart.exchange.Request;
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
import org.restheart.metrics.MetricLabel;
import org.restheart.metrics.Metrics;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
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

    private static Parser GQL_PARSER = new Parser();

    @Override
    @SuppressWarnings("unchecked")
    public void handle(GraphQLRequest req, MongoResponse res) throws Exception {
        if (req.isOptions()) {
            handleOptions(req);
            return;
        }

        var graphQLApp = gqlApp(appURI(req.getExchange()));

        var dataLoaderRegistry = setDataloaderRegistry(graphQLApp.objectsMappings());

        if (req.getQuery() == null) {
            res.setInError(HttpStatus.SC_BAD_REQUEST, "query cannot be null");
            return;
        } else {
            try {
                // check query syntax
                var doc = GQL_PARSER.parseDocument(req.getQuery());

                // add metric label
                Metrics.attachMetricLabel(req, new MetricLabel("query", queryNames(doc)));
            } catch(InvalidSyntaxException ise) {
                res.setInError(HttpStatus.SC_BAD_REQUEST, "Syntax error in query", ise);
                return;
            }

        }

        var inputBuilder = ExecutionInput.newExecutionInput()
            .query(req.getQuery())
            .dataLoaderRegistry(dataLoaderRegistry);

        inputBuilder.operationName(req.getOperationName());

        if (req.hasVariables()) {
            inputBuilder.variables((new Gson()).fromJson(req.getVariables(), Map.class));
        }

        var dispatcherInstrumentationOptions = DataLoaderDispatcherInstrumentationOptions.newOptions();

        if (this.verbose) {
            dispatcherInstrumentationOptions = dispatcherInstrumentationOptions.includeStatistics(true);
        }

        var dispatcherInstrumentation = new DataLoaderDispatcherInstrumentation(dispatcherInstrumentationOptions);

        this.gql = GraphQL.newGraphQL(graphQLApp.getExecutableSchema()).instrumentation(dispatcherInstrumentation).build();

        try {
            var result = this.gql.execute(inputBuilder.build());

            if (this.verbose) {
                logDataLoadersStatistics(dataLoaderRegistry);
            }

            if (!result.getErrors().isEmpty()) {
                res.setInError(400, "Bad Request");
            }
            res.setContent(BsonUtils.toBsonDocument(result.toSpecification()));
        } catch(UnknownOperationException uoe) {
            res.setInError(404, uoe.getMessage(), uoe);
        } catch(Throwable t) {
            var gee = new GraphQLAppExecutionException("error executing query", t);
            res.setInError(500, gee.getMessage(), gee);
            throw gee;
        }
    }

    private void logDataLoadersStatistics(DataLoaderRegistry dataLoaderRegistry) {
        dataLoaderRegistry.getKeys().forEach(key -> LOGGER.debug(key.toUpperCase() + ": " + dataLoaderRegistry.getDataLoader(key).getStatistics()));
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
            } catch (GraphQLAppDefNotFoundException nfe) {
                throw new BadRequestException(HttpStatus.SC_NOT_FOUND);
            } catch (GraphQLIllegalAppDefinitionException ie) {
                LOGGER.error(ie.getMessage());
                throw new BadRequestException(ie.getMessage(), HttpStatus.SC_BAD_REQUEST);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
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

    /**
     * collect first level query fields are return a comma separated list of their nmaes
     * @param doc
     * @return
     */
    static String queryNames(Document doc) {
        // collect query root fields
        var rootFields = doc.getDefinitionsOfType(OperationDefinition.class).stream()
            .filter(d -> d.getOperation() == Operation.QUERY)
            .map(d -> d.getSelectionSet().getSelectionsOfType(Field.class)
            .stream()
                .filter(f -> f.getSelectionSet() != null)
                .collect(Collectors.toList())).findFirst().orElse(new ArrayList<>());

        // add metric label with queries names
        return rootFields.stream()
            .filter(f -> f.getName() != null)
            .map(f -> f.getName())
            .collect(Collectors.joining(","));
    }

    public static final String ACCESS_CONTROL_ALLOW_METHODS = "POST, OPTIONS";
    @Override
    public String accessControlAllowMethods(Request<?> r) {
        return ACCESS_CONTROL_ALLOW_METHODS;
    }
}
