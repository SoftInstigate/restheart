/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2025 SoftInstigate
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.bson.BsonNull;
import org.dataloader.DataLoaderRegistry;
import org.restheart.configuration.ConfigurationException;
import org.restheart.exchange.BadRequestException;
import org.restheart.exchange.ExchangeKeys;
import org.restheart.exchange.GraphQLRequest;
import org.restheart.exchange.GraphQLResponse;
import static org.restheart.exchange.GraphQLResponse.GRAPHQL_RESPONSE_CONTENT_TYPE;
import org.restheart.exchange.Request;
import org.restheart.graphql.cache.AppDefinitionLoader;
import org.restheart.graphql.cache.AppDefinitionLoadingCache;
import org.restheart.graphql.datafetchers.GraphQLDataFetcher;
import org.restheart.graphql.dataloaders.AggregationBatchLoader;
import org.restheart.graphql.dataloaders.QueryBatchLoader;
import org.restheart.graphql.instrumentation.MaxQueryTimeInstrumentation;
import org.restheart.graphql.models.AggregationMapping;
import org.restheart.graphql.models.GraphQLApp;
import org.restheart.graphql.models.QueryMapping;
import org.restheart.graphql.models.TypeMapping;
import org.restheart.graphql.models.builder.AppBuilder;
import org.restheart.graphql.scalars.bsonCoercing.CoercingUtils;
import org.restheart.metrics.MetricLabel;
import org.restheart.metrics.Metrics;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.Service;
import org.restheart.utils.BsonUtils;
import static org.restheart.utils.BsonUtils.document;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.MalformedJsonException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.MongoClient;

import graphql.ErrorType;
import graphql.ExceptionWhileDataFetching;
import graphql.ExecutionInput;
import graphql.ExecutionResultImpl;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.execution.ValueUnboxer;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentationOptions;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.language.OperationDefinition.Operation;
import graphql.parser.InvalidSyntaxException;
import graphql.parser.Parser;
import io.undertow.server.HttpServerExchange;

@RegisterPlugin(name = "graphql", description = "Service that handles GraphQL requests", secure = true, enabledByDefault = true, defaultURI = "/graphql")
public class GraphQLService implements Service<GraphQLRequest, GraphQLResponse> {
    public static final String DEFAULT_APP_DEF_DB = "restheart";
    public static final String DEFAULT_APP_DEF_COLLECTION = "gqlapps";
    public static final Boolean DEFAULT_VERBOSE = false;
    public static final int DEFAULT_DEFAULT_LIMIT = 100;
    public static final int DEFAULT_MAX_LIMIT = 1_000;
    public static final long DEFAULT_QUERY_TIME_LIMIT = 0l; // disabled

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphQLService.class);

    private GraphQL gql;
    private String db = DEFAULT_APP_DEF_DB;
    private String collection = DEFAULT_APP_DEF_COLLECTION;
    private Boolean verbose = DEFAULT_VERBOSE;
    private int defaultLimit = DEFAULT_DEFAULT_LIMIT;
    private int maxLimit = DEFAULT_MAX_LIMIT;
    private long queryTimeLimit = DEFAULT_QUERY_TIME_LIMIT;

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

        this.defaultLimit = argOrDefault(config, "default-limit", DEFAULT_DEFAULT_LIMIT);
        this.maxLimit = argOrDefault(config, "max-limit", DEFAULT_MAX_LIMIT);

        this.queryTimeLimit = ((Number)argOrDefault(config, "query-time-limit", DEFAULT_QUERY_TIME_LIMIT)).longValue();

        QueryBatchLoader.setMongoClient(mclient);
        AggregationBatchLoader.setMongoClient(mclient);
        GraphQLDataFetcher.setMongoClient(mclient);
        AppDefinitionLoader.setup(db, collection, mclient);
        AppBuilder.setDefaultLimit(this.defaultLimit);
        AppBuilder.setMaxLimit(this.maxLimit);
        QueryMapping.setMaxLimit(this.maxLimit);
    }

    private static final Parser GQL_PARSER = new Parser();

    @Override
    @SuppressWarnings("unchecked")
    public void handle(GraphQLRequest req, GraphQLResponse res) throws Exception {
        if (req.isOptions()) {
            handleOptions(req);
            return;
        }

        var graphQLApp = gqlApp(appURI(req.getExchange()));

        var dataLoaderRegistry = setDataloaderRegistry(graphQLApp.objectsMappings());

        if (req.getQuery() == null) {
            // no query -> 400
            // GraphQL over HTTP specs https://github.com/graphql/graphql-over-http/blob/main/spec/GraphQLOverHTTP.md
            // If the GraphQL response does not contain the {data} entry then
            // the server MUST reply with a 4xx or 5xx status code as appropriate.
            var errorResult = new ExecutionResultImpl.Builder().addError(GraphQLError.newError().message("Query cannot be null").build()).build();
            res.setStatusCode(HttpStatus.SC_BAD_REQUEST);
            res.setContent(BsonUtils.toBsonDocument(errorResult.toSpecification()));
            return;
        } else {
            try {
                // check query syntax
                var doc = GQL_PARSER.parseDocument(req.getQuery());

                var queryNames = queryNames(doc);
                // add metric label
                Metrics.attachMetricLabel(req, new MetricLabel("query", queryNames));
                LOGGER.debug("Executing GraphQL query: {}", queryNames);
            } catch(InvalidSyntaxException ise) {
                // invalid syntax -> 400
                // GraphQL over HTTP specs https://github.com/graphql/graphql-over-http/blob/main/spec/GraphQLOverHTTP.md
                // If the GraphQL response does not contain the {data} entry then
                // the server MUST reply with a 4xx or 5xx status code as appropriate.
                res.setStatusCode(HttpStatus.SC_BAD_REQUEST);
                res.setContent(BsonUtils.toBsonDocument(ise.toInvalidSyntaxError().toSpecification()));
                return;
            }
        }

        var localContext = document();

        // add the query-time-limit to the local context;
        if (this.queryTimeLimit > 0) {
            localContext.put("query-time-limit", this.queryTimeLimit);
        }

        var inputBuilder = ExecutionInput.newExecutionInput()
            .query(req.getQuery())
            .localContext(localContext.get())
            .dataLoaderRegistry(dataLoaderRegistry);

        inputBuilder.operationName(req.getOperationName());

        if (req.hasVariables()) {
            inputBuilder.variables((new Gson()).fromJson(req.getVariables(), Map.class));
        }

        var dispatcherInstrumentationOptions = DataLoaderDispatcherInstrumentationOptions.newOptions();

        if (this.verbose) {
            dispatcherInstrumentationOptions = dispatcherInstrumentationOptions.includeStatistics(true);
        }

        var chainedInstrumentations = new ArrayList<Instrumentation>();
        chainedInstrumentations.add(new DataLoaderDispatcherInstrumentation(dispatcherInstrumentationOptions));
        chainedInstrumentations.add(new MaxQueryTimeInstrumentation(this.queryTimeLimit));

        this.gql = GraphQL.newGraphQL(graphQLApp.getExecutableSchema())
            .valueUnboxer((Object object) -> object instanceof BsonNull ? null : ValueUnboxer.DEFAULT.unbox(object))
            .instrumentation(new ChainedInstrumentation(chainedInstrumentations))
            .build();

        try {
            var result = this.gql.execute(inputBuilder.build());

            //  The graphql specification specifies:
            //  If an error was encountered during the execution that prevented a valid response, the data entry in the response should be null."
            if (result.getErrors() != null && !result.getErrors().isEmpty()) {

                var graphQLQueryTimeoutException = result.getErrors().stream()
                    .filter(e -> e instanceof ExceptionWhileDataFetching)
                    .map(e -> (ExceptionWhileDataFetching) e)
                    .map(e -> containsGraphQLQueryTimeoutException(e))
                    .filter(e -> e != null)
                    .findFirst();

                if (graphQLQueryTimeoutException.isPresent()) {
                    // timeout during a mongodb query -> 408
                    // If we got a timeout, just response with 408 Request Timeout
                    // this GraphQLQueryTimeoutException is added to errors if a single query or aggregation breaks the query-time-limit
                    var errorResult = new ExecutionResultImpl.Builder().addError(graphQLQueryTimeoutException.get()).build();
                    res.setContent(BsonUtils.toBsonDocument(errorResult.toSpecification()));
                    res.setStatusCode(HttpStatus.SC_REQUEST_TIMEOUT);
                } else if (result.getData() == null) {
                    // errors and no data -> 400
                    // GraphQL over HTTP specs https://github.com/graphql/graphql-over-http/blob/main/spec/GraphQLOverHTTP.md
                    // If the GraphQL response does not contain the {data} entry then
                    // the server MUST reply with a 4xx or 5xx status code as appropriate.
                    res.setStatusCode(HttpStatus.SC_BAD_REQUEST);
                    res.setContent(BsonUtils.toBsonDocument(result.toSpecification()));
                } else {
                    // errors and data -> 200 (partial result)
                    res.setContent(BsonUtils.toBsonDocument(result.toSpecification()));
                }
            } else {
                res.setContent(BsonUtils.toBsonDocument(result.toSpecification()));
            }

            if (this.verbose) {
                logDataLoadersStatistics(dataLoaderRegistry);
            }
        } catch(GraphQLQueryTimeoutException t) {
            // this exception can be thrown by MaxQueryTimeInstrumentation
            res.setStatusCode(HttpStatus.SC_REQUEST_TIMEOUT);
            var errorResult = new ExecutionResultImpl.Builder().addError(t).build();
            res.setContent(BsonUtils.toBsonDocument(errorResult.toSpecification()));
        } catch(Throwable t) {
            if (containsMongoTimeoutException(t)) {
                // db down -> 500
                // GraphQL over HTTP specs https://github.com/graphql/graphql-over-http/blob/main/spec/GraphQLOverHTTP.md
                // If the GraphQL response does not contain the {data} entry then
                // the server MUST reply with a 4xx or 5xx status code as appropriate.
                LOGGER.error("Unable to establish a connection to the database", t);
                var errorResult = new ExecutionResultImpl.Builder()
                    .addError(GraphQLError.newError()
                        .errorType(ErrorType.ExecutionAborted)
                        .message("Unable to establish a connection to the database: " + t.getCause().getMessage()).build()).build();

                res.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                res.setContent(BsonUtils.toBsonDocument(errorResult.toSpecification()));
            } else {
                // other errors down -> 400
                // GraphQL over HTTP specs https://github.com/graphql/graphql-over-http/blob/main/spec/GraphQLOverHTTP.md
                // If the GraphQL response does not contain the {data} entry then
                // the server MUST reply with a 4xx or 5xx status code as appropriate.

                if (t instanceof GraphQLError gqle) {
                    var errorResult = new ExecutionResultImpl.Builder().addError(gqle).build();
                    res.setContent(BsonUtils.toBsonDocument(errorResult.toSpecification()));
                } else {
                    var errorResult = new ExecutionResultImpl.Builder()
                            .addError(GraphQLError.newError()
                                .errorType(ErrorType.ExecutionAborted)
                                .message("Runtime Error: " + t.getMessage()).build()).build();

                    res.setContent(BsonUtils.toBsonDocument(errorResult.toSpecification()));
                }

                res.setStatusCode(HttpStatus.SC_BAD_REQUEST);
            }
        }
    }

    private boolean containsMongoTimeoutException(Throwable t) {
        if (t == null) {
            return false;
        } else if (t instanceof MongoTimeoutException) {
            return true;
        } else if (t.getCause() != null) {
            return containsMongoTimeoutException(t.getCause());
        }

        return false;
    }

    private GraphQLQueryTimeoutException containsGraphQLQueryTimeoutException(ExceptionWhileDataFetching e) {
        if (e == null || e.getException() == null) {
            return null;
        } else if (e.getException() instanceof GraphQLQueryTimeoutException gqle) {
            return gqle;
        } else {
            return null;
        }
    }

    private void logDataLoadersStatistics(DataLoaderRegistry dataLoaderRegistry) {
        dataLoaderRegistry.getKeys().forEach(key -> LOGGER.debug(key.toUpperCase() + ": " + dataLoaderRegistry.getDataLoader(key).getStatistics()));
    }

    private DataLoaderRegistry setDataloaderRegistry(Map<String, TypeMapping> mappings) {
        var dataLoaderRegistry = new DataLoaderRegistry();

        mappings.forEach((String type, TypeMapping typeMapping) -> {
            typeMapping.getFieldMappingMap().forEach((field, fieldMapping) -> {
                if (fieldMapping instanceof QueryMapping queryMapping) {
                    var dataLoader = queryMapping.getDataloader();
                    if (dataLoader != null) {
                        dataLoaderRegistry.register(type + "_" + field, dataLoader);
                    }
                }
                // register dataLoaders for Aggregation Mapping
                if (fieldMapping instanceof AggregationMapping aggregationMapping) {
                    var dataLoader = aggregationMapping.getDataloader();
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
        // TODO set the response content type
        // in case of error
        return e -> {
            try {
                if (e.getRequestMethod().equalToString(ExchangeKeys.METHOD.POST.name()) || e.getRequestMethod().equalToString(ExchangeKeys.METHOD.OPTIONS.name())) {
                    var appURI = appURI(e);
                    gqlApp(appURI); // throws GraphQLAppDefNotFoundException when uri is not bound to an app definition
                                    // thorws GraphQLIllegalAppDefinitionException is app def is invalid
                    GraphQLRequest.init(e, appURI); // throws BadRequestException when content type is not valid or query field is missing
                                                    // throws JsonSyntaxException when content is not valid JSON
                } else {
                    // wrong method -> 405
                    throw new BadRequestException(HttpStatus.SC_METHOD_NOT_ALLOWED);
                }
            } catch(BadRequestException brex) {
                if (brex.getStatusCode() == HttpStatus.SC_METHOD_NOT_ALLOWED) {
                    // wrong method -> 405
                    var errorResult = new ExecutionResultImpl.Builder()
                            .addError(GraphQLError.newError()
                                .errorType(ErrorType.ExecutionAborted)
                                .message("Method Not Allowed").build()).build();

                    throw new BadRequestException(BsonUtils.toBsonDocument(errorResult.toSpecification()).toJson(), HttpStatus.SC_METHOD_NOT_ALLOWED, true, GRAPHQL_RESPONSE_CONTENT_TYPE);
                } else {
                    // wrong content type or query field is missing -> 400
                    // GraphQL over HTTP specs https://github.com/graphql/graphql-over-http/blob/main/spec/GraphQLOverHTTP.md
                    // Requests that the server cannot interpret SHOULD result in status code 400 (Bad Request).
                    var errorResult = new ExecutionResultImpl.Builder()
                            .addError(GraphQLError.newError()
                                .errorType(ErrorType.ValidationError)
                                .message(brex.getMessage()).build()).build();

                    throw new BadRequestException(BsonUtils.toBsonDocument(errorResult.toSpecification()).toJson(), HttpStatus.SC_BAD_REQUEST, true, GRAPHQL_RESPONSE_CONTENT_TYPE);
                }
            } catch(JsonSyntaxException jse) {
                // invalid json -> 400
                // GraphQL over HTTP specs https://github.com/graphql/graphql-over-http/blob/main/spec/GraphQLOverHTTP.md
                // Requests that the server cannot interpret SHOULD result in status code 400 (Bad Request).

                var errorResult = new ExecutionResultImpl.Builder()
                        .addError(GraphQLError.newError()
                            .errorType(ErrorType.ValidationError)
                            .message(jseCleanMessage(jse)).build()).build();

                throw new BadRequestException(BsonUtils.toBsonDocument(errorResult.toSpecification()).toJson(), HttpStatus.SC_BAD_REQUEST, true, GRAPHQL_RESPONSE_CONTENT_TYPE);
            } catch (GraphQLAppDefNotFoundException nfe) {
                // GrahpQL app not found -> 404
                // GraphQL over HTTP specs https://github.com/graphql/graphql-over-http/blob/main/spec/GraphQLOverHTTP.md
                // If the GraphQL response does not contain the {data} entry then
                // the server MUST reply with a 4xx or 5xx status code as appropriate.
                var errorResult = new ExecutionResultImpl.Builder()
                        .addError(GraphQLError.newError()
                            .errorType(ErrorType.ExecutionAborted)
                            .message("GraphQL app not found").build()).build();
                throw new BadRequestException(BsonUtils.toBsonDocument(errorResult.toSpecification()).toJson(), HttpStatus.SC_NOT_FOUND, true, GRAPHQL_RESPONSE_CONTENT_TYPE);
            } catch (GraphQLIllegalAppDefinitionException ie) {
                if (containsMongoTimeoutException(ie)) {
                    // db down -> 500
                    // GraphQL over HTTP specs https://github.com/graphql/graphql-over-http/blob/main/spec/GraphQLOverHTTP.md
                    // If the GraphQL response does not contain the {data} entry then
                    // the server MUST reply with a 4xx or 5xx status code as appropriate.
                    LOGGER.error("Unable to establish a connection to the database", ie);
                    var errorResult = new ExecutionResultImpl.Builder()
                        .addError(GraphQLError.newError()
                            .errorType(ErrorType.ExecutionAborted)
                            .message("Unable to establish a connection to the database: " + ie.getCause().getMessage()).build()).build();

                    throw new BadRequestException(BsonUtils.toBsonDocument(errorResult.toSpecification()).toJson(), HttpStatus.SC_INTERNAL_SERVER_ERROR, true, GRAPHQL_RESPONSE_CONTENT_TYPE);
                } else {
                    // this should never happen unless the app definition was not created via API when is checked (e.g. when created directly in the db with mongosh or similar)
                    // invalid app definition -> 400
                    // GraphQL over HTTP specs https://github.com/graphql/graphql-over-http/blob/main/spec/GraphQLOverHTTP.md
                    // If the GraphQL response does not contain the {data} entry then
                    // the server MUST reply with a 4xx or 5xx status code as appropriate.
                    LOGGER.error("Illegal GraphQL App definition", ie);
                    var errorResult = new ExecutionResultImpl.Builder()
                            .addError(GraphQLError.newError()
                                .errorType(ErrorType.ExecutionAborted)
                                .message("Invalid GraphQL app definition: " + ie.getMessage()).build()).build();
                    throw new BadRequestException(BsonUtils.toBsonDocument(errorResult.toSpecification()).toJson(), HttpStatus.SC_BAD_REQUEST, true, GRAPHQL_RESPONSE_CONTENT_TYPE);
                }
            } catch (IOException ioe) {
                // network error -> 400
                // GraphQL over HTTP specs https://github.com/graphql/graphql-over-http/blob/main/spec/GraphQLOverHTTP.md
                // If the GraphQL response does not contain the {data} entry then
                // the server MUST reply with a 4xx or 5xx status code as appropriate.
                var errorResult = new ExecutionResultImpl.Builder()
                        .addError(GraphQLError.newError()
                            .errorType(ErrorType.ExecutionAborted)
                            .message("Network error: " + ioe.getMessage()).build()).build();
                throw new BadRequestException(BsonUtils.toBsonDocument(errorResult.toSpecification()).toJson(), HttpStatus.SC_BAD_REQUEST, true, GRAPHQL_RESPONSE_CONTENT_TYPE);
            }
        };
    }

    private static String jseCleanMessage(JsonSyntaxException ex) {
        if (ex.getCause() instanceof MalformedJsonException mje) {
            return "Bad Request: " + mje.getMessage();
        } else if (ex.getMessage() != null) {
            if (ex.getMessage().indexOf("MalformedJsonException") > 0) {
                var index = ex.getMessage().indexOf("MalformedJsonException" + "MalformedJsonException".length());
                return "Bad Request: " + ex.getMessage().substring(index);
            } else {
                return "Bad Request: " + ex.getMessage();
            }
        } else {
            return "Bad Request";
        }
    }

    private String appURI(HttpServerExchange exchange) {
        var splitPath = exchange.getRequestPath().split("/");
        return String.join("/", Arrays.copyOfRange(splitPath, 2, splitPath.length));
    }

    private GraphQLApp gqlApp(String appURI) throws GraphQLAppDefNotFoundException, GraphQLIllegalAppDefinitionException {
        return AppDefinitionLoadingCache.getLoading(appURI);
    }

    @Override
    public Consumer<HttpServerExchange> responseInitializer() {
        return e -> GraphQLResponse.init(e);
    }

    @Override
    public Function<HttpServerExchange, GraphQLRequest> request() {
        return e -> GraphQLRequest.of(e);
    }

    @Override
    public Function<HttpServerExchange, GraphQLResponse> response() {
        return e -> GraphQLResponse.of(e);
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

    public static final String GQL_ACCESS_CONTROL_ALLOW_METHODS = "POST, OPTIONS";

    @Override
    public String accessControlAllowMethods(Request<?> r) {
        return GQL_ACCESS_CONTROL_ALLOW_METHODS;
    }
}
