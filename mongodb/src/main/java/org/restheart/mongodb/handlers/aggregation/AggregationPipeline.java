/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
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
package org.restheart.mongodb.handlers.aggregation;

import static org.restheart.mongodb.utils.VarOperatorsInterpolator.OPERATOR;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.restheart.exchange.InvalidMetadataException;
import org.restheart.exchange.QueryVariableNotBoundException;
import org.restheart.mongodb.utils.VarOperatorsInterpolator;
import org.restheart.utils.BsonUtils;
import org.restheart.utils.LambdaUtils;

/**
 * represents a map reduce.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AggregationPipeline extends AbstractAggregationOperation {

    /**
     * the stages property name
     */
    public static final String STAGES_ELEMENT_NAME = "stages";

    /**
     * the allowDiskUse property name
     */
    public static final String ALLOW_DISK_USER_ELEMENT_NAME = "allowDiskUse";

    private final BsonArray stages;
    private final BsonBoolean allowDiskUse;

    /**
     * Constructor from aggregation definition in collection metadata
     *
     *
     * @param properties the json object defining the aggregation; it must include the
     * array {@code stages}. Note: dollar prefixed operators in the stages
     * must be underscore escaped, e.g. "_$exits". Example:
     *
     * <pre>
    {
        "type": "pipeline",
        "uri": "test_ap",
        "allowDiskUse": false,
        "stages": [ { "_$match": { "name": { "_$exists": true } } } ]
    }
     </pre>
     *
     * @throws org.restheart.exchange.InvalidMetadataException
     */
    public AggregationPipeline(BsonDocument properties) throws InvalidMetadataException {
        super(properties);

        var _stages = properties.get(STAGES_ELEMENT_NAME);

        if (_stages == null || !_stages.isArray()) {
            throw new InvalidMetadataException("query /" + getUri() + "has invalid '" + STAGES_ELEMENT_NAME + "': " + _stages + "; must be an array of stage objects");
        }

        // chekcs that the _stages array elements are all documents
        if (_stages.asArray().stream().anyMatch(s -> !s.isDocument())) {
            throw new InvalidMetadataException("query /" + getUri() + "has invalid '" + STAGES_ELEMENT_NAME + "': " + _stages + "; must be an array of stage objects");
        }

        this.stages = _stages.asArray();

        var _allowDiskUse = properties.get(ALLOW_DISK_USER_ELEMENT_NAME);

        if (_allowDiskUse != null && !_allowDiskUse.isBoolean()) {
            throw new InvalidMetadataException("query /" + getUri() + "has invalid '" + ALLOW_DISK_USER_ELEMENT_NAME + "': " + _allowDiskUse + "; must be boolean");
        }

        this.allowDiskUse = _allowDiskUse != null ? _allowDiskUse.asBoolean() : BsonBoolean.FALSE;
    }

    /**
     * @return the stages
     */
    public BsonArray getStages() {
        return stages;
    }

    public BsonArray removeOptionalNotBoundStages(BsonDocument avars) {
        return stages;
    }

    /**
     * @param stage
     * @return true if stage is optional
     * @throws InvalidMetadataException
     */
    private static boolean optional(BsonDocument stage) {
        return stage.containsKey("$ifvar");
    }

    /**
     * @param stage
     * @return true if optional stage is valid
     * @throws InvalidMetadataException
     */
    private static void checkIfVar(BsonDocument stage) throws InvalidMetadataException {
        if (stage.containsKey("$ifvar")) {
            var ifvar = stage.get("$ifvar");

            if (!(ifvar.isArray() && (ifvar.asArray().size() == 2 || ifvar.asArray().size() == 3) &&
                (ifvar.asArray().get(0).isString() ||
                (ifvar.asArray().get(0).isArray() && ifvar.asArray().get(0).asArray().stream().allMatch(e -> e.isString())) ||
                (ifvar.asArray().get(1).isDocument()) ||
                (ifvar.asArray().size() > 2 && ifvar.asArray().get(2).isDocument())))) {
                    throw new InvalidMetadataException("Invalid optional stage: " + BsonUtils.toJson(stage));
            }
        }
    }

    private static boolean stageApplies(BsonDocument stage, BsonDocument avars) {
        var vars = stage.get("$ifvar").asArray().get(0);

        if (vars.isString()) {
            return BsonUtils.get(avars, vars.asString().getValue()).isPresent();
        } else {
            return vars.asArray().stream().map(s -> s.asString().getValue()).allMatch(key -> BsonUtils.get(avars, key).isPresent());
        }
    }

    private static BsonDocument elseStage(BsonDocument stage) {
        return stage.get("$ifvar").asArray().size() > 2
            ? stage.get("$ifvar").asArray().get(2).asDocument()
            : null;
    }

    /**
     * @param avars RequestContext.getAggregationVars()
     * @return the stages, with unescaped operators and bound variables
     * @throws org.restheart.exchange.InvalidMetadataException
     * @throws org.restheart.exchange.QueryVariableNotBoundException
     */
    public List<BsonDocument> getResolvedStagesAsList(BsonDocument avars) throws InvalidMetadataException, QueryVariableNotBoundException {
        var stagesWithUnescapedOperators = BsonUtils.unescapeKeys(stages).asArray();

        // check optional stages
        stagesWithUnescapedOperators.stream()
            .map(s -> s.asDocument())
            .filter(stage -> optional(stage)).forEach(optionalStage -> {
                try {
                    checkIfVar(optionalStage);
                } catch(InvalidMetadataException ime) {
                    LambdaUtils.throwsSneakyException(ime);
                }
            });

        var stagesWithoutUnboudOptionalStages = stagesWithUnescapedOperators.stream()
            .map(s -> s.asDocument())
            .map(stage -> _stage(stage, avars))
            .filter(stage -> stage != null)
            .collect(Collectors.toCollection(BsonArray::new));

        var resolvedStages = VarOperatorsInterpolator.interpolate(OPERATOR.$var, stagesWithoutUnboudOptionalStages, avars).asArray();

        var ret = new ArrayList<BsonDocument>();

        resolvedStages.stream().filter(stage -> stage.isDocument()).map(stage -> stage.asDocument()).forEach(ret::add);

        return ret;
    }

    private static BsonDocument _stage(BsonDocument stage, BsonDocument avars) {
        if (!optional(stage)) {
            return stage;
        } else if (stageApplies(stage, avars)){
            return stage.get("$ifvar").asArray().get(1).asDocument();
        } else {
            return elseStage(stage); // null, if no else stage specified
        }
    }

    /**
     * @return the allowDiskUse
     */
    public BsonBoolean getAllowDiskUse() {
        return allowDiskUse;
    }
}
