/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */

package org.restheart.mongodb.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonNull;
import org.bson.BsonValue;
import org.restheart.exchange.InvalidMetadataException;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.QueryVariableNotBoundException;
import static org.restheart.mongodb.utils.VarsInterpolator.VAR_OPERATOR;
import org.restheart.security.AclVarsInterpolator;
import org.restheart.security.MongoPermissions;
import org.restheart.security.MongoRealmAccount;
import org.restheart.security.WithProperties;
import org.restheart.utils.BsonUtils;
import org.restheart.utils.LambdaUtils;


/**
 * Utility class for interpolating aggregation stages with a specified format, e.g., <code>{ [operator]: "name"}</code>,
 * and replacing placeholders with provided values. It also supports conditional stages using
 * <code>{ "$ifvar": [var] }</code>, which are removed if the variable is missing.
 *
 * <p>This class provides sophisticated variable interpolation for MongoDB aggregation pipelines,
 * supporting both simple variable substitution and conditional stage inclusion. It's designed
 * to work with RESTHeart's dynamic aggregation system, allowing aggregation pipelines to be
 * parameterized and customized at runtime.</p>
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>Variable interpolation using {@code $var} or {@code $arg} operators</li>
 *   <li>Conditional stages using {@code $ifvar} or {@code $ifarg} operators</li>
 *   <li>Support for default values when variables are not bound</li>
 *   <li>Security checks to prevent operator injection</li>
 *   <li>Integration with RESTHeart's authentication and permission system</li>
 * </ul>
 *
 * <p>Example of variable interpolation:</p>
 * <pre>{@code
 * // Pipeline with variable
 * [
 *   { "$match": { "status": { "$var": "status" } } },
 *   { "$limit": { "$var": ["limit", 10] } }  // with default value
 * ]
 *
 * // With values: { "status": "active", "limit": 20 }
 * // Results in:
 * [
 *   { "$match": { "status": "active" } },
 *   { "$limit": 20 }
 * ]
 * }</pre>
 *
 * <p>Example of conditional stages:</p>
 * <pre>{@code
 * [
 *   { "$ifvar": ["includeStats",
 *     { "$group": { "_id": "$category", "count": { "$sum": 1 } } }
 *   ]},
 *   { "$ifvar": ["sortField",
 *     { "$sort": { "$var": "sortField" } },
 *     { "$sort": { "_id": 1 } }  // else clause
 *   ]}
 * ]
 * }</pre>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */

public class StagesInterpolator {
    /**
     * Enum defining the conditional stage operators.
     * <ul>
     *   <li>{@code $ifvar} - Used in standard aggregation pipelines</li>
     *   <li>{@code $ifarg} - Used in GraphQL mappings</li>
     * </ul>
     */
    public enum STAGE_OPERATOR { $ifvar, $ifarg };

    /**
     * @param varOperator the var operator, $var for queries and aggregations, $arg for GraphQL mappings
     * @param stageOperator the stage operator, $ifvar for aggregations, $ifarg for GraphQL mappings
     * @param stages the aggregation pipeline stages
     * @param values RequestContext.getAggregationVars()
     * @return the stages, with unescaped operators and bound variables
     * @throws org.restheart.exchange.InvalidMetadataException
     * @throws org.restheart.exchange.QueryVariableNotBoundException
     */
    public static List<BsonDocument> interpolate(VAR_OPERATOR varOperator, STAGE_OPERATOR stageOperator, BsonArray stages, BsonDocument values) throws InvalidMetadataException, QueryVariableNotBoundException {
        var stagesWithUnescapedOperators = BsonUtils.unescapeKeys(stages).asArray();

        // check optional stages
        stagesWithUnescapedOperators.stream()
            .map(BsonValue::asDocument)
            .filter(stage -> optional(stageOperator, stage)).forEach(optionalStage -> {
                try {
                    checkIfVar(stageOperator, optionalStage);
                } catch(InvalidMetadataException ime) {
                    LambdaUtils.throwsSneakyException(ime);
                }
            });

        var stagesWithoutUnboundOptionalStages = stagesWithUnescapedOperators.stream()
            .map(BsonValue::asDocument)
            .map(stage -> _stage(stageOperator, stage, values))
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(BsonArray::new));

        var resolvedStages = VarsInterpolator.interpolate(varOperator, stagesWithoutUnboundOptionalStages, values).asArray();

        var ret = new ArrayList<BsonDocument>();

        resolvedStages.stream().filter(BsonValue::isDocument).map(BsonValue::asDocument).forEach(ret::add);

        return ret;
    }

    /**
     * Checks if values contain MongoDB operators (fields starting with '$').
     * <p>
     * This is a security measure to prevent clients from injecting operators into
     * aggregation pipelines, which could potentially bypass access controls or
     * execute unintended operations. Any field starting with '$' in the values
     * will cause a SecurityException to be thrown.
     * </p>
     *
     * <p>The check is performed recursively on all nested documents and arrays
     * within the values structure.</p>
     *
     * @param values the aggregation variables to check, typically from RequestContext.getAggregationVars()
     * @throws SecurityException if any field name starts with '$', indicating an operator
     */
    public static void shouldNotContainOperators(BsonValue values) throws SecurityException {
        if (values == null) {
            return;
        }
        if (values.isDocument()) {
            var _obj = values.asDocument();

            _obj.forEach((key, value) -> {
                if (key.startsWith("$")) {
                    throw new SecurityException("aggregation variables cannot include operators");
                }

                if (value.isDocument() || value.isArray()) {
                    shouldNotContainOperators(value);
                }
            });
        } else if (values.isArray()) {
            values.asArray().getValues().stream()
                .filter(el -> (el.isDocument() || el.isArray()))
                .forEachOrdered(StagesInterpolator::shouldNotContainOperators);
        }
    }
    /**
     * Checks if a stage is optional (conditional).
     * <p>
     * A stage is considered optional if it contains a conditional operator
     * ({@code $ifvar} or {@code $ifarg}). Optional stages are only included
     * in the pipeline if their condition is met.
     * </p>
     *
     * @param stageOperator the stage operator to check for ($ifvar or $ifarg)
     * @param stage the stage document to check
     * @return {@code true} if the stage contains the conditional operator, {@code false} otherwise
     */
    private static boolean optional(STAGE_OPERATOR stageOperator, BsonDocument stage) {
        return stage.containsKey(stageOperator.name());
    }

    /**
     * Validates the structure of a conditional stage.
     * <p>
     * A valid conditional stage must have the following structure:
     * </p>
     * <pre>{@code
     * { "$ifvar": [condition, thenStage, elseStage?] }
     * }</pre>
     * <p>Where:</p>
     * <ul>
     *   <li>condition: a string variable name or array of variable names</li>
     *   <li>thenStage: the stage to include if condition is true (must be a document)</li>
     *   <li>elseStage: optional stage to include if condition is false (must be a document)</li>
     * </ul>
     *
     * @param stageOperator the stage operator being validated ($ifvar or $ifarg)
     * @param stage the stage document to validate
     * @throws InvalidMetadataException if the stage structure is invalid
     */
    private static void checkIfVar(STAGE_OPERATOR stageOperator, BsonDocument stage) throws InvalidMetadataException {
        if (stage.containsKey(stageOperator.name())) {
            var ifvar = stage.get(stageOperator.name());

            if (!(ifvar.isArray() && (ifvar.asArray().size() == 2 || ifvar.asArray().size() == 3) &&
                (ifvar.asArray().get(0).isString() ||
                (ifvar.asArray().get(0).isArray() && ifvar.asArray().get(0).asArray().stream().allMatch(BsonValue::isString)) ||
                (ifvar.asArray().get(1).isDocument()) ||
                (ifvar.asArray().size() > 2 && ifvar.asArray().get(2).isDocument())))) {
                    throw new InvalidMetadataException("Invalid optional stage: " + BsonUtils.toJson(stage));
            }
        }
    }

    /**
     * Determines whether a conditional stage should be included based on available variables.
     * <p>
     * A conditional stage applies if:
     * </p>
     * <ul>
     *   <li>The avars parameter is not null (query includes aggregation variables)</li>
     *   <li>All required variables specified in the condition are present in avars</li>
     * </ul>
     *
     * @param stageOperator the stage operator ($ifvar or $ifarg)
     * @param stage the conditional stage to evaluate
     * @param avars the available aggregation variables
     * @return {@code true} if the stage should be included, {@code false} otherwise
     */
    private static boolean stageApplies(STAGE_OPERATOR stageOperator, BsonDocument stage, BsonDocument avars) {
        // false if request does not include the ?avars qparam
        // see issue https://github.com/SoftInstigate/restheart/issues/500
        if (avars == null) {
            return false;
        }

        var vars = stage.get(stageOperator.name()).asArray().getFirst();

        if (vars.isString()) {
            return BsonUtils.get(avars, vars.asString().getValue()).isPresent();
        } else {
            return vars.asArray().stream().map(s -> s.asString().getValue()).allMatch(key -> BsonUtils.get(avars, key).isPresent());
        }
    }

    /**
     * Extracts the else clause from a conditional stage if present.
     * <p>
     * The else clause is the third element in the conditional array and is
     * included when the condition evaluates to false.
     * </p>
     *
     * @param stageOperator the stage operator ($ifvar or $ifarg)
     * @param stage the conditional stage containing the else clause
     * @return the else stage document, or null if no else clause is specified
     */
    private static BsonDocument elseStage(STAGE_OPERATOR stageOperator, BsonDocument stage) {
        return stage.get(stageOperator.name()).asArray().size() > 2
            ? stage.get(stageOperator.name()).asArray().get(2).asDocument()
            : null;
    }


    /**
     * Resolves a stage based on its type and available variables.
     * <p>
     * This method handles three cases:
     * </p>
     * <ul>
     *   <li>Non-conditional stages: returned as-is</li>
     *   <li>Conditional stages with met conditions: returns the then clause</li>
     *   <li>Conditional stages with unmet conditions: returns the else clause or null</li>
     * </ul>
     *
     * @param stageOperator the stage operator ($ifvar or $ifarg)
     * @param stage the stage to resolve
     * @param avars the available aggregation variables
     * @return the resolved stage document, or null if the stage should be excluded
     */
    private static BsonDocument _stage(STAGE_OPERATOR stageOperator, BsonDocument stage, BsonDocument avars) {
        if (!optional(stageOperator, stage)) {
            return stage;
        } else if (stageApplies(stageOperator, stage, avars)){
            return stage.get(stageOperator.name()).asArray().get(1).asDocument();
        } else {
            return elseStage(stageOperator, stage); // null, if no else stage specified
        }
    }

    /**
     * Injects default variables into the aggregation variables document.
     * <p>
     * This method adds system-provided variables that can be used in aggregation pipelines
     * without being explicitly passed by the client. These include pagination parameters,
     * user information, and MongoDB permissions.
     * </p>
     *
     * <p>The following variables are injected:</p>
     * <ul>
     *   <li>{@code @page} - Current page number from request</li>
     *   <li>{@code @pagesize} - Page size from request</li>
     *   <li>{@code @limit} - Same as pagesize, for convenience</li>
     *   <li>{@code @skip} - Calculated skip value for pagination</li>
     *   <li>{@code @user} - Authenticated user information (if available)</li>
     *   <li>{@code @user.*} - Individual user properties accessible via dot notation</li>
     *   <li>{@code @mongoPermissions} - MongoDB permissions for the current user</li>
     *   <li>{@code @mongoPermissions.projectResponse} - Response projection filter</li>
     *   <li>{@code @mongoPermissions.mergeRequest} - Request merge filter</li>
     *   <li>{@code @mongoPermissions.readFilter} - Read access filter</li>
     *   <li>{@code @mongoPermissions.writeFilter} - Write access filter</li>
     * </ul>
     *
     * <p>Supports accounts handled by MongoRealmAuthenticator, FileRealmAuthenticator,
     * and JwtAuthenticationMechanism.</p>
     *
     * @param request the MongoDB request containing authentication and pagination information
     * @param avars the aggregation variables document to inject defaults into
     */
    public static void injectAvars(MongoRequest request, BsonDocument avars) {
        // add @page, @pagesize, @limit and @skip to avars to allow handling
        // paging in the aggregation via default page and pagesize query params
        avars.put("@page", new BsonInt32(request.getPage()));
        avars.put("@pagesize", new BsonInt32(request.getPagesize()));
        avars.put("@limit", new BsonInt32(request.getPagesize()));
        avars.put("@skip", new BsonInt32(request.getPagesize() * (request.getPage() - 1)));

        // add @mongoPermissions to avars
        var mongoPermissions = MongoPermissions.of(request);
        if (mongoPermissions != null) {
            avars.put("@mongoPermissions" ,mongoPermissions.asBson());

            avars.put("@mongoPermissions.projectResponse", mongoPermissions.getProjectResponse() == null
                ? BsonNull.VALUE
                : mongoPermissions.getProjectResponse());

            avars.put("@mongoPermissions.mergeRequest", mongoPermissions.getMergeRequest() == null
                ? BsonNull.VALUE
                : AclVarsInterpolator.interpolateBson(request, mongoPermissions.getMergeRequest()));

            avars.put("@mongoPermissions.readFilter", mongoPermissions.getReadFilter() == null
                ? BsonNull.VALUE
                : AclVarsInterpolator.interpolateBson(request, mongoPermissions.getReadFilter()));

            avars.put("@mongoPermissions.writeFilter", mongoPermissions.getWriteFilter() == null
                ? BsonNull.VALUE
                : AclVarsInterpolator.interpolateBson(request, mongoPermissions.getWriteFilter()));
        } else {
            avars.put("@mongoPermissions", new MongoPermissions().asBson());
            avars.put("@mongoPermissions.projectResponse", BsonNull.VALUE);
            avars.put("@mongoPermissions.mergeRequest", BsonNull.VALUE);
            avars.put("@mongoPermissions.readFilter", BsonNull.VALUE);
            avars.put("@mongoPermissions.writeFilter", BsonNull.VALUE);
        }

        // add @user to avars
        var account = request.getAuthenticatedAccount();

        if (account == null) {
            avars.put("@user", BsonNull.VALUE);
        } else if (account instanceof MongoRealmAccount maccount) {
            var ba = maccount.properties();
            avars.put("@user", ba);
            ba.keySet().forEach(k -> avars.put("@user.".concat(k), ba.get(k)));
        } else if (account instanceof WithProperties<?> accountWithProperties) {
            var ba = BsonUtils.toBsonDocument(accountWithProperties.propertiesAsMap());
            avars.put("@user", ba);
            ba.keySet().forEach(k -> avars.put("@user.".concat(k), ba.get(k)));
        } else {
            avars.put("@user", BsonNull.VALUE);
        }
    }


}
