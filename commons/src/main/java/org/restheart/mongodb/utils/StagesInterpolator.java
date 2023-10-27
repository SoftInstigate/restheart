/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
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
import java.util.stream.Collectors;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.exchange.InvalidMetadataException;
import org.restheart.exchange.QueryVariableNotBoundException;
import static org.restheart.mongodb.utils.VarsInterpolator.VAR_OPERATOR;
import org.restheart.utils.BsonUtils;
import org.restheart.utils.LambdaUtils;


/**
 * Utility class for interpolating aggregation stages with a specified format, e.g., <code>{ [operator]: "name"}</code>,
 * and replacing placeholders with provided values. It also supports conditional stages using
 * <code>{ "$ifvar": [var] }</code>, which are removed if the variable is missing.
 */

public class StagesInterpolator {
    public enum STAGE_OPERATOR { $ifvar, $ifarg };

    /**
     * @param varOperator the var operator, $var for queries and aggregations, $arg for GraphQL mappings
     * @param stageOperator the stagee operator, $ifvar for aggregations, $ifarg for GraphQL mappings
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
            .map(s -> s.asDocument())
            .filter(stage -> optional(stageOperator, stage)).forEach(optionalStage -> {
                try {
                    checkIfVar(stageOperator, optionalStage);
                } catch(InvalidMetadataException ime) {
                    LambdaUtils.throwsSneakyException(ime);
                }
            });

        var stagesWithoutUnboudOptionalStages = stagesWithUnescapedOperators.stream()
            .map(s -> s.asDocument())
            .map(stage -> _stage(stageOperator, stage, values))
            .filter(stage -> stage != null)
            .collect(Collectors.toCollection(BsonArray::new));

        var resolvedStages = VarsInterpolator.interpolate(varOperator, stagesWithoutUnboudOptionalStages, values).asArray();

        var ret = new ArrayList<BsonDocument>();

        resolvedStages.stream().filter(stage -> stage.isDocument()).map(stage -> stage.asDocument()).forEach(ret::add);

        return ret;
    }

    /**
     * checks if values contain operators. this is not allowed by default since
     * the client would be able to modify the query or aggregation stages
     *
     * @param values RequestContext.getAggregationVars()
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
     * @param stage
     * @return true if stage is optional
     * @throws InvalidMetadataException
     */
    private static boolean optional(STAGE_OPERATOR stageOperator, BsonDocument stage) {
        return stage.containsKey(stageOperator.name());
    }

    /**
     * @param stage
     * @return true if optional stage is valid
     * @throws InvalidMetadataException
     */
    private static void checkIfVar(STAGE_OPERATOR stageOperator, BsonDocument stage) throws InvalidMetadataException {
        if (stage.containsKey(stageOperator.name())) {
            var ifvar = stage.get(stageOperator.name());

            if (!(ifvar.isArray() && (ifvar.asArray().size() == 2 || ifvar.asArray().size() == 3) &&
                (ifvar.asArray().get(0).isString() ||
                (ifvar.asArray().get(0).isArray() && ifvar.asArray().get(0).asArray().stream().allMatch(e -> e.isString())) ||
                (ifvar.asArray().get(1).isDocument()) ||
                (ifvar.asArray().size() > 2 && ifvar.asArray().get(2).isDocument())))) {
                    throw new InvalidMetadataException("Invalid optional stage: " + BsonUtils.toJson(stage));
            }
        }
    }

    private static boolean stageApplies(STAGE_OPERATOR stageOperator, BsonDocument stage, BsonDocument avars) {
        var vars = stage.get(stageOperator.name()).asArray().get(0);

        if (vars.isString()) {
            return BsonUtils.get(avars, vars.asString().getValue()).isPresent();
        } else {
            return vars.asArray().stream().map(s -> s.asString().getValue()).allMatch(key -> BsonUtils.get(avars, key).isPresent());
        }
    }

    private static BsonDocument elseStage(STAGE_OPERATOR stageOperator, BsonDocument stage) {
        return stage.get(stageOperator.name()).asArray().size() > 2
            ? stage.get(stageOperator.name()).asArray().get(2).asDocument()
            : null;
    }


    private static BsonDocument _stage(STAGE_OPERATOR stageOperator, BsonDocument stage, BsonDocument avars) {
        if (!optional(stageOperator, stage)) {
            return stage;
        } else if (stageApplies(stageOperator, stage, avars)){
            return stage.get(stageOperator.name()).asArray().get(1).asDocument();
        } else {
            return elseStage(stageOperator, stage); // null, if no else stage specified
        }
    }

}
